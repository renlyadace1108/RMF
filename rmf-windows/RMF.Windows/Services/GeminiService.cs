using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class GeminiService
{
    private static readonly HttpClient HttpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(45)
    };

    /// <summary>
    /// 联网拉取当前 API 可用的模型列表 (支持 Gemini 官方或 OpenAI 兼容协议)
    /// </summary>
    public async Task<List<GeminiModelInfo>> ListModelsAsync()
    {
        var config = ConfigService.Load();
        string apiKey = config.GetEffectiveApiKey();
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            throw new InvalidOperationException("未配置 API Key！请先填入 Key 再拉取模型。");
        }

        string baseUrl = config.GetEffectiveBaseUrl();

        // 1. 若为 OpenAI 兼容协议 (如 DeepSeek, OpenAI, 本地 Ollama 等)
        if (config.ApiProvider == "OpenAI" || IsOpenAiModel(config.GetEffectiveModel(), baseUrl))
        {
            try
            {
                string endpoint = $"{baseUrl.TrimEnd('/')}/models";
                if (!endpoint.Contains("/v1/") && !endpoint.EndsWith("/v1/models"))
                {
                    endpoint = $"{baseUrl.TrimEnd('/')}/v1/models";
                }

                using var req = new HttpRequestMessage(HttpMethod.Get, endpoint);
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
                var resp = await HttpClient.SendAsync(req);
                if (resp.IsSuccessStatusCode)
                {
                    string json = await resp.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(json);
                    var list = new List<GeminiModelInfo>();
                    if (doc.RootElement.TryGetProperty("data", out var dataArr))
                    {
                        foreach (var m in dataArr.EnumerateArray())
                        {
                            if (m.TryGetProperty("id", out var idElem))
                            {
                                string id = idElem.GetString() ?? "";
                                if (!string.IsNullOrWhiteSpace(id))
                                {
                                    list.Add(new GeminiModelInfo { Name = id, DisplayName = id });
                                }
                            }
                        }
                    }
                    if (list.Count > 0) return list;
                }
            }
            catch
            {
                // Fallback to recommended curated list
            }

            return new List<GeminiModelInfo>
            {
                new() { Name = "deepseek-chat", DisplayName = "deepseek-chat (DeepSeek V3)" },
                new() { Name = "deepseek-reasoner", DisplayName = "deepseek-reasoner (DeepSeek R1 深度思考)" },
                new() { Name = "gpt-4o", DisplayName = "gpt-4o (OpenAI 旗舰)" },
                new() { Name = "gpt-4o-mini", DisplayName = "gpt-4o-mini (OpenAI 极速版)" },
                new() { Name = "qwen-max", DisplayName = "qwen-max (通义千问旗舰)" },
                new() { Name = "qwen-turbo", DisplayName = "qwen-turbo (通义千问极速)" }
            };
        }

        // 2. Google Gemini 原生协议
        string geminiEndpoint = $"{baseUrl}/v1beta/models?key={apiKey}";
        HttpResponseMessage response = await HttpClient.GetAsync(geminiEndpoint);
        string responseString = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"拉取 Gemini 模型列表失败 [HTTP {response.StatusCode}]: {responseString}");
        }

        var result = JsonSerializer.Deserialize<GeminiModelListResponse>(responseString);
        var resList = new List<GeminiModelInfo>();

        if (result?.Models != null)
        {
            foreach (var m in result.Models)
            {
                if (m.SupportedGenerationMethods != null && m.SupportedGenerationMethods.Contains("generateContent"))
                {
                    resList.Add(m);
                }
            }
        }

        resList.Sort((a, b) =>
        {
            bool aIsFlash = a.ModelId.Contains("flash", StringComparison.OrdinalIgnoreCase);
            bool bIsFlash = b.ModelId.Contains("flash", StringComparison.OrdinalIgnoreCase);
            if (aIsFlash && !bIsFlash) return -1;
            if (!aIsFlash && bIsFlash) return 1;
            return string.Compare(a.ModelId, b.ModelId, StringComparison.OrdinalIgnoreCase);
        });

        return resList;
    }

    /// <summary>
    /// 测试与当前配置模型的握手连接
    /// </summary>
    public async Task<string> TestConnectionAsync()
    {
        var sw = System.Diagnostics.Stopwatch.StartNew();
        string testPrompt = "请仅回复五个字：监管系统正常。";
        string result = await GenerateContentAsync("你是一个系统状态检测测试助手。", testPrompt);
        sw.Stop();
        var config = ConfigService.Load();
        return $"✅ 握手成功！模型 [{config.GetEffectiveModel()}] 响应正常 (耗时 {sw.ElapsedMilliseconds}ms)\n回复: {result.Trim()}";
    }

    /// <summary>
    /// 核心调用：向模型发送预置指令与软件数据 (自动根据协议路由至 Gemini 或 OpenAI 兼容格式)
    /// </summary>
    public async Task<string> GenerateContentAsync(string systemInstruction, string userPrompt)
    {
        var config = ConfigService.Load();
        string apiKey = config.GetEffectiveApiKey();
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            throw new InvalidOperationException("未配置 API Key！请点击「⚙️ 模型与 Key」填入你的 Key。");
        }

        string model = config.GetEffectiveModel().Trim();
        string baseUrl = config.GetEffectiveBaseUrl().Trim();
        bool isOpenAi = config.ApiProvider == "OpenAI" || IsOpenAiModel(model, baseUrl);

        if (isOpenAi)
        {
            return await CallOpenAiCompatibleApiAsync(baseUrl, apiKey, model, systemInstruction, userPrompt, config.Temperature);
        }
        else
        {
            return await CallGeminiApiAsync(baseUrl, apiKey, model, systemInstruction, userPrompt, config.Temperature);
        }
    }

    private static bool IsOpenAiModel(string model, string baseUrl)
    {
        if (baseUrl.Contains("deepseek.com", StringComparison.OrdinalIgnoreCase) ||
            baseUrl.Contains("openai.com", StringComparison.OrdinalIgnoreCase) ||
            baseUrl.Contains("localhost:11434", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        if (model.StartsWith("deepseek", StringComparison.OrdinalIgnoreCase) ||
            model.StartsWith("gpt-", StringComparison.OrdinalIgnoreCase) ||
            model.StartsWith("qwen", StringComparison.OrdinalIgnoreCase) ||
            model.StartsWith("claude", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return false;
    }

    /// <summary>
    /// OpenAI 兼容协议核心调用 (支持 DeepSeek, OpenAI, 通义千问, Ollama 等)
    /// </summary>
    private async Task<string> CallOpenAiCompatibleApiAsync(string baseUrl, string apiKey, string model, string systemInstruction, string userPrompt, double temperature)
    {
        string endpoint = baseUrl;
        if (!endpoint.EndsWith("/chat/completions", StringComparison.OrdinalIgnoreCase))
        {
            if (endpoint.EndsWith("/v1", StringComparison.OrdinalIgnoreCase))
            {
                endpoint = $"{endpoint}/chat/completions";
            }
            else
            {
                endpoint = $"{endpoint.TrimEnd('/')}/chat/completions";
            }
        }

        var requestBody = new
        {
            model = model,
            messages = new object[]
            {
                new { role = "system", content = systemInstruction },
                new { role = "user", content = userPrompt }
            },
            temperature = temperature > 0 ? temperature : 0.2
        };

        string jsonPayload = JsonSerializer.Serialize(requestBody);
        using var request = new HttpRequestMessage(HttpMethod.Post, endpoint);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
        request.Content = new StringContent(jsonPayload, Encoding.UTF8, "application/json");

        HttpResponseMessage response = await HttpClient.SendAsync(request);
        string responseString = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            // 如果 404，尝试补充 /v1 重试
            if (response.StatusCode == System.Net.HttpStatusCode.NotFound && !baseUrl.Contains("/v1"))
            {
                string v1Endpoint = $"{baseUrl.TrimEnd('/')}/v1/chat/completions";
                using var retryReq = new HttpRequestMessage(HttpMethod.Post, v1Endpoint);
                retryReq.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
                retryReq.Content = new StringContent(jsonPayload, Encoding.UTF8, "application/json");
                HttpResponseMessage retryResp = await HttpClient.SendAsync(retryReq);
                string retryString = await retryResp.Content.ReadAsStringAsync();
                if (retryResp.IsSuccessStatusCode)
                {
                    return ParseOpenAiResponse(retryString);
                }
            }

            throw new HttpRequestException($"OpenAI 兼容 API 调用失败 [HTTP {response.StatusCode}, 模型: {model}]: {responseString}");
        }

        return ParseOpenAiResponse(responseString);
    }

    private static string ParseOpenAiResponse(string json)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        if (root.TryGetProperty("choices", out var choices) && choices.GetArrayLength() > 0)
        {
            var first = choices[0];
            if (first.TryGetProperty("message", out var msg) && msg.TryGetProperty("content", out var content))
            {
                return content.GetString() ?? string.Empty;
            }
        }
        throw new InvalidOperationException($"未能从响应中解析出有效文本: {json}");
    }

    /// <summary>
    /// Google Gemini 原生协议核心调用
    /// </summary>
    private async Task<string> CallGeminiApiAsync(string baseUrl, string apiKey, string model, string systemInstruction, string userPrompt, double temperature)
    {
        if (model.StartsWith("models/"))
        {
            model = model.Substring("models/".Length);
        }
        string endpoint = $"{baseUrl}/v1beta/models/{model}:generateContent?key={apiKey}";

        var requestBody = new
        {
            contents = new[]
            {
                new
                {
                    role = "user",
                    parts = new[]
                    {
                        new { text = $"{systemInstruction}\n\n---\n\n{userPrompt}" }
                    }
                }
            },
            generationConfig = new
            {
                temperature = temperature > 0 ? temperature : 0.2,
                maxOutputTokens = 2048
            }
        };

        string jsonPayload = JsonSerializer.Serialize(requestBody);
        using var content = new StringContent(jsonPayload, Encoding.UTF8, "application/json");

        HttpResponseMessage response = await HttpClient.PostAsync(endpoint, content);
        string responseString = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"Gemini API 调用失败 [HTTP {response.StatusCode}, 模型: {model}]: {responseString}");
        }

        using var doc = JsonDocument.Parse(responseString);
        var root = doc.RootElement;
        if (root.TryGetProperty("candidates", out var candidates) &&
            candidates.GetArrayLength() > 0 &&
            candidates[0].TryGetProperty("content", out var contentElem) &&
            contentElem.TryGetProperty("parts", out var parts) &&
            parts.GetArrayLength() > 0 &&
            parts[0].TryGetProperty("text", out var textElem))
        {
            return textElem.GetString() ?? string.Empty;
        }

        throw new InvalidOperationException($"Gemini 响应未解析到有效内容: {responseString}");
    }

    // =========================================================================
    // 监管功能 1: 多维效能分析 (预置提示词模板 + 软件实时时间资产数据)
    // =========================================================================
    public async Task<string> AnalyzeScheduleAsync(List<ScheduleItem> items, DateTime targetDate)
    {
        string systemInstruction = @"你是一个追求绝对客观、精通时间资产配置与认知工程学的顶级效能监管专家 (RMF 监管系统 · 效能分析引擎)。
你的分析必须建立在严谨的时间数据与客观科学规律之上，严禁任何情绪化打气或鸡汤套话。

请按以下 4 个维度进行结构化深度剖析：
1. ⏰【时间资产配置透视】：核算总工时、深度工作 (DeepWork) 与浅度事务 (ShallowWork) 比例，评估时间资产是否倾斜于核心战略产出；
2. 🧩【专注度与碎片化诊断】：分析单时间块长度、任务切换频率与碎片化指数，指出上下文切换造成的认知损耗；
3. 🌅【精力节律与生物钟匹配】：分析上午峰值精力时段（9:00-11:30）与下午低谷时段的任务类型分配是否合理；
4. 🎯【综合效能评级与核心行动建议】：给出一个综合评级 (如 S/A/B/C/D)，并给出 2~3 条高价值、立即可落地的效能改进策略。";

        var sb = new StringBuilder();
        sb.AppendLine($"【监管分析日期】：{targetDate:yyyy年MM月dd日 dddd}");
        sb.AppendLine($"【今日排期日程清单 (共 {items.Count} 项)】：");

        if (items.Count == 0)
        {
            sb.AppendLine("（今日暂未录入任何日程时间块）");
        }
        else
        {
            double totalMins = 0;
            double deepMins = 0;
            double shallowMins = 0;
            double restMins = 0;

            foreach (var item in items)
            {
                var dur = (item.EndTime - item.StartTime).TotalMinutes;
                if (dur <= 0) dur = item.EstimatedMinutes;
                totalMins += dur;

                if (item.WorkType == "DEEP_WORK") deepMins += dur;
                else if (item.WorkType == "REST_BUFFER") restMins += dur;
                else shallowMins += dur;

                sb.AppendLine($"- [{item.StartTime:HH:mm} - {item.EndTime:HH:mm}] [{item.WorkType}] {item.Title} (分类: {item.Category}, 优先级: {item.Priority}, 状态: {item.Status})");
                if (!string.IsNullOrWhiteSpace(item.Dod)) sb.AppendLine($"  DoD验收标准: {item.Dod}");
                if (!string.IsNullOrWhiteSpace(item.Description)) sb.AppendLine($"  描述: {item.Description}");
            }

            sb.AppendLine($"\n【软件实时统计指标】：总计划工时 {totalMins / 60:F1}h | 深度工作 {deepMins / 60:F1}h | 浅层事务 {shallowMins / 60:F1}h | 休息缓冲 {restMins / 60:F1}h");
        }

        sb.AppendLine("\n请展开客观深度的多维效能监管分析：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    // =========================================================================
    // 监管功能 2: 客观排期审核 (预置合规风控提示词 + 软件本地规则引擎体检数据)
    // =========================================================================
    public async Task<string> AuditScheduleAsync(List<ScheduleItem> items, DateTime targetDate, AuditScanReport? report = null)
    {
        string systemInstruction = @"你是一个冷酷、客观、严谨的日程调度与认知负荷风险审核引擎 (RMF 监管系统 · 风控审核中枢)。
你的职责是进行彻底的排期质量审查，找出可能导致执行崩溃、精力耗竭或交付失败的硬伤缺陷。杜绝任何客套与安慰。

请依据以下核心规则进行全面审查与裁决：
1. ⚖️【DoD 验收标准质检】：审查每项任务是否存在模糊、缺乏可验证交付产物的风险；
2. 🧠【4.5h 深度负荷生理硬顶】：核查单日深度工作是否超过人类生理极限 (4.5小时)，是否存在决策疲劳或认知崩溃风险；
3. ☕【转场缓冲与恢复合规】：核查连续专注时段之间是否存在无缝背靠背排期，是否预留了 10~15 分钟的生理缓冲；
4. ⚡【时段重叠与可行性漏洞】：核实时间块是否冲突、夜晚过晚时段排高脑力任务等违背常识的安排；
5. 📋【强制纠偏指令】：给出明确的排期整改清单，指明哪些任务必须移入延期池、哪些任务必须补充 DoD。";

        var sb = new StringBuilder();
        sb.AppendLine($"【监管审核日期】：{targetDate:yyyy年MM月dd日 dddd}");

        if (report != null)
        {
            sb.AppendLine($"【本地体检健康评分】：{report.OverallHealthScore}/100");
            sb.AppendLine($"【总排期时长】：{report.TotalScheduledHours:F1}h | 深度工作: {report.DeepWorkHours:F1}h (生理硬顶上限: {report.DeepWorkCapHours:F1}h)");
            sb.AppendLine($"【DoD 完备率】：{report.DoDComplianceRatio:F0}%");
            sb.AppendLine($"【缓冲缺失次数】：{report.MissingBufferCount} 次");
            if (report.Issues.Count > 0)
            {
                sb.AppendLine("【本地规则引擎初筛风险项】：");
                foreach (var issue in report.Issues)
                {
                    sb.AppendLine($"- [{issue.Severity}] {issue.Title}: {issue.Description}");
                }
            }
        }

        sb.AppendLine("\n【待审核日程时间块明细】：");
        if (items.Count == 0)
        {
            sb.AppendLine("（无排期日程）");
        }
        else
        {
            foreach (var item in items)
            {
                sb.AppendLine($"- [{item.StartTime:HH:mm} - {item.EndTime:HH:mm}] [{item.WorkType}] {item.Title} | 状态: {item.Status}");
                sb.AppendLine($"  验收标准 DoD: {(string.IsNullOrWhiteSpace(item.Dod) ? "【未定义】" : item.Dod)}");
            }
        }

        sb.AppendLine("\n请输出严密的排期审核报告与强制裁决指令：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 兼容调用：基于本地体检报告对今日排期执行深度 AI 专家审计
    /// </summary>
    public async Task<string> DeepAuditScheduleWithAiAsync(AuditScanReport report, List<ScheduleItem> tasks)
    {
        return await AuditScheduleAsync(tasks, DateTime.Today, report);
    }

    /// <summary>
    /// 敏捷 DoD 生成助手：基于任务名称生成具体、可验证的完成标准
    /// </summary>
    public async Task<string> SuggestDoDAsync(string taskTitle)
    {
        string prompt = $"任务名称：「{taskTitle}」。请为其生成一句清晰、具体、可客观验收的交付标准 (Definition of Done)，字数在 25 字以内，严禁套话。仅输出此句验收标准本身。";
        return await GenerateContentAsync("你是一名严格的高级敏捷教练与质量总监，擅长为任务制定清晰可验证的完成标准 (DoD)。", prompt);
    }

    // =========================================================================
    // 监管功能 3: 智能排期建议 (预置优化提示词 + 软件空闲槽位与敏捷待办池数据)
    // =========================================================================
    public async Task<string> SuggestScheduleAsync(List<ScheduleItem> items, List<ScheduleItem> backlogItems, DateTime targetDate, string? customGoal = null)
    {
        string systemInstruction = @"你是一个顶级的日程编排与效能优化顾问 (RMF 监管系统 · 策略建议中枢)。
基于认知负荷理论、生物钟精力曲线与敏捷时间块原则，为用户推演最优的排期优化建议。

请按以下模块输出极具实操性的优化建议方案：
1. 🌅【黄金专注时段重排建议】：指导用户将高认知脑力任务（Lv3/Lv4 深度工作）安排在早晨黄金专注时段 (09:00-11:30)，将低脑力琐碎事务和沟通会议移至下午低谷时段；
2. ☕【转场缓冲与恢复插入建议】：明确指出在哪些具体时间点之间应插入 10~15 分钟的休息缓冲；
3. 🎯【DoD 验收标准补全建议】：挑选排期中缺少 DoD 或 DoD 模糊的任务，为每项生成一句具体、量化、可验证的验收标准；
4. 📥【敏捷待办填充方案】：结合用户今日剩余空闲时间块与敏捷待办池 (Backlog)，建议优先填入哪项待办任务；
5. 🌟【一句话行动纲要】：简明扼要的今日最高优先级执行指引。";

        var sb = new StringBuilder();
        sb.AppendLine($"【建议规划日期】：{targetDate:yyyy年MM月dd日 dddd}");
        if (!string.IsNullOrWhiteSpace(customGoal))
        {
            sb.AppendLine($"【用户个性化监督诉求】：{customGoal}");
        }

        sb.AppendLine("\n【今日现有排期】：");
        if (items.Count == 0)
        {
            sb.AppendLine("（今日暂无排期，全天时间段均可自由编排）");
        }
        else
        {
            foreach (var it in items)
            {
                sb.AppendLine($"- [{it.StartTime:HH:mm} - {it.EndTime:HH:mm}] [{it.WorkType}] {it.Title} (DoD: {(string.IsNullOrEmpty(it.Dod) ? "无" : it.Dod)})");
            }
        }

        sb.AppendLine("\n【未排期敏捷待办池 (Backlog)】：");
        if (backlogItems.Count == 0)
        {
            sb.AppendLine("（待办池为空）");
        }
        else
        {
            foreach (var b in backlogItems)
            {
                sb.AppendLine($"- [待办] {b.Title} (预估 {b.EstimatedMinutes}m, 优先级: {b.Priority})");
            }
        }

        sb.AppendLine("\n请基于上述数据推演并生成最优排期优化建议方案：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    public async Task<string> EvaluateIdeaAsync(string userIdea, string currentContext)
    {
        string systemInstruction = @"你是一个基于第一性原理的高级架构师与客观决策分析引擎。
原则要求：
1. 绝对客观、求真务实，拒绝无意义的迎合赞美，也不做情绪化的盲目否定。
2. 价值与边界审查：清晰界定该想法的核心价值、技术可行性边界与隐性风险/潜在陷阱。
3. 机会成本与主线冲突评估：结合用户当前的核心工作主线，评估若执行该想法所付出的精力分心代价与沉没成本。
4. 验证闭环路径：给出验证该想法有效性的最小可行步骤（MVP）与关键指标。语言精炼，直击本质。";

        string userPrompt = $"【待评估的想法 / 规划决策】：\n{userIdea}\n\n【当前进行中的主线任务背景】：\n{currentContext}\n\n请进行客观深度的推演评估：";

        return await GenerateContentAsync(systemInstruction, userPrompt);
    }

    public async Task<(decimal amount, string category, string note)> ParseExpenseAsync(string rawInput)
    {
        var config = ConfigService.Load();
        if (!string.IsNullOrWhiteSpace(config.GetEffectiveApiKey()))
        {
            try
            {
                string systemInstruction = @"你是一个财务流水提取解析引擎。
请从用户的自然语言记账文本中提取金额、分类、备注。
只输出严格的 JSON 格式，不要任何 markdown 标记或任何其他文本：
{""amount"": 25.5, ""category"": ""餐饮美食"", ""note"": ""午餐黄焖鸡""}";

                string res = await GenerateContentAsync(systemInstruction, rawInput);
                res = res.Replace("```json", "").Replace("```", "").Trim();
                using var doc = JsonDocument.Parse(res);
                var root = doc.RootElement;
                decimal amount = root.TryGetProperty("amount", out var a) ? a.GetDecimal() : 0m;
                string category = root.TryGetProperty("category", out var c) ? (c.GetString() ?? "其它支出") : "其它支出";
                string note = root.TryGetProperty("note", out var n) ? (n.GetString() ?? rawInput) : rawInput;
                if (amount > 0) return (amount, category, note);
            }
            catch
            {
            }
        }

        return ParseExpenseLocalFallback(rawInput);
    }

    public static (decimal amount, string category, string note) ParseExpenseLocalFallback(string rawInput)
    {
        decimal amount = 0m;
        var match = System.Text.RegularExpressions.Regex.Match(rawInput, @"(\d+(\.\d+)?)");
        if (match.Success && decimal.TryParse(match.Value, out var val))
        {
            amount = val;
        }

        string category = "其它支出";
        if (rawInput.Contains("饭") || rawInput.Contains("吃") || rawInput.Contains("餐") || rawInput.Contains("咖啡") || rawInput.Contains("饮") || rawInput.Contains("奶茶") || rawInput.Contains("外卖"))
            category = "餐饮美食";
        else if (rawInput.Contains("车") || rawInput.Contains("地铁") || rawInput.Contains("打车") || rawInput.Contains("加油") || rawInput.Contains("机票") || rawInput.Contains("高铁"))
            category = "交通出行";
        else if (rawInput.Contains("买") || rawInput.Contains("超市") || rawInput.Contains("购物") || rawInput.Contains("服饰"))
            category = "日常购物";
        else if (rawInput.Contains("话费") || rawInput.Contains("网费") || rawInput.Contains("电费") || rawInput.Contains("房租"))
            category = "生活缴费";

        return (amount, category, rawInput);
    }
}