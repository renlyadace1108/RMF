using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class GeminiService
{
    private static readonly HttpClient HttpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(35)
    };

    /// <summary>
    /// 联网从 Google Gemini API 动态拉取当前 API Key 可用的官方模型列表
    /// </summary>
    public async Task<List<GeminiModelInfo>> ListModelsAsync()
    {
        var config = ConfigService.Load();
        if (string.IsNullOrWhiteSpace(config.GeminiApiKey))
        {
            throw new InvalidOperationException("未配置 Gemini API Key！请先填入 Key 再拉取模型。");
        }

        string baseUrl = config.GetEffectiveBaseUrl();
        string endpoint = $"{baseUrl}/v1beta/models?key={config.GeminiApiKey}";

        HttpResponseMessage response = await HttpClient.GetAsync(endpoint);
        string responseString = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"拉取模型列表失败 [HTTP {response.StatusCode}]: {responseString}");
        }

        var result = JsonSerializer.Deserialize<GeminiModelListResponse>(responseString);
        var list = new List<GeminiModelInfo>();

        if (result?.Models != null)
        {
            foreach (var m in result.Models)
            {
                if (m.SupportedGenerationMethods != null && m.SupportedGenerationMethods.Contains("generateContent"))
                {
                    list.Add(m);
                }
            }
        }

        list.Sort((a, b) =>
        {
            bool aIsFlash = a.ModelId.Contains("flash", StringComparison.OrdinalIgnoreCase);
            bool bIsFlash = b.ModelId.Contains("flash", StringComparison.OrdinalIgnoreCase);
            if (aIsFlash && !bIsFlash) return -1;
            if (!aIsFlash && bIsFlash) return 1;
            return string.Compare(a.ModelId, b.ModelId, StringComparison.OrdinalIgnoreCase);
        });

        return list;
    }

    /// <summary>
    /// 核心调用：向 Google Gemini 发送 Prompt
    /// </summary>
    private async Task<string> GenerateContentAsync(string systemInstruction, string userPrompt)
    {
        var config = ConfigService.Load();
        if (string.IsNullOrWhiteSpace(config.GeminiApiKey))
        {
            throw new InvalidOperationException("未配置 Gemini API Key！请点击左侧「⚙️ Gemini 模型与配置」填入你的 Key。");
        }

        string model = config.GetEffectiveModel().Trim();
        if (model.StartsWith("models/"))
        {
            model = model.Substring("models/".Length);
        }
        string baseUrl = config.GetEffectiveBaseUrl();
        string endpoint = $"{baseUrl}/v1beta/models/{model}:generateContent?key={config.GeminiApiKey}";

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
                temperature = config.Temperature > 0 ? config.Temperature : 0.2,
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

        return "Gemini 未返回有效文本。";
    }

    /// <summary>
    /// AI 功能 1: 多维效能分析 (AI Schedule Analysis)
    /// </summary>
    public async Task<string> AnalyzeScheduleAsync(List<ScheduleItem> items, DateTime targetDate)
    {
        string systemInstruction = @"你是一个追求绝对客观、精通时间资产配置与认知工程学的顶级效能分析专家 (RMF AI Schedule Analyst)。
你的分析必须建立在严谨的时间数据与客观科学规律之上，严禁任何情绪化打气或鸡汤套话。

请按以下 4 个维度进行结构化深度剖析：
1. ⏰【时间资产配置透视】：核算总工时、深度工作 (DeepWork) 与浅度事务 (ShallowWork) 比例，评估时间资产是否倾斜于核心战略产出；
2. 🧩【专注度与碎片化诊断】：分析单时间块长度、任务切换频率与碎片化指数，指出上下文切换造成的认知损耗；
3. 🌅【精力节律与生物钟匹配】：分析上午峰值精力时段（9:00-11:30）与下午低谷时段的任务类型分配是否合理；
4. 🎯【综合效能评级与核心行动建议】：给出一个综合评级 (如 S/A/B/C/D)，并给出 2~3 条高价值、立即可落地的效能改进策略。";

        var sb = new StringBuilder();
        sb.AppendLine($"【分析日期】：{targetDate:yyyy年MM月dd日 dddd}");
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

            sb.AppendLine($"\n【统计基础数据】：总计划工时 {totalMins / 60:F1}h | 深度工作 {deepMins / 60:F1}h | 浅层事务 {shallowMins / 60:F1}h | 休息缓冲 {restMins / 60:F1}h");
        }

        sb.AppendLine("\n请展开客观深度的多维效能分析：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// AI 功能 2: 客观排期审核 (AI Schedule Audit)
    /// </summary>
    public async Task<string> AuditScheduleAsync(List<ScheduleItem> items, DateTime targetDate, AuditScanReport? report = null)
    {
        string systemInstruction = @"你是一个冷酷、客观、严谨的 AI 日程调度与认知负荷风险审核引擎 (RMF AI Schedule Auditor)。
你的职责是进行彻底的排期质量审查，找出可能导致执行崩溃、精力耗竭或交付失败的硬伤缺陷。杜绝任何客套与安慰。

请依据以下核心规则进行全面审查与裁决：
1. ⚖️【DoD 验收标准质检】：审查每项任务是否存在模糊、缺乏可验证交付产物的风险；
2. 🧠【4.5h 深度负荷硬顶】：核查单日深度工作是否超过人类生理极限 (4.5小时)，是否存在决策疲劳或认知崩溃风险；
3. ☕【转场缓冲与恢复合规】：核查连续专注时段之间是否存在无缝背靠背排期，是否预留了 10~15 分钟的生理缓冲；
4. ⚡【时段重叠与可行性漏洞】：核实时间块是否冲突、夜晚过晚时段排高脑力任务等违背常识的安排；
5. 📋【强制纠偏指令】：给出明确的排期整改清单，指明哪些任务必须移入延期池、哪些任务必须补充 DoD。";

        var sb = new StringBuilder();
        sb.AppendLine($"【审核日期】：{targetDate:yyyy年MM月dd日 dddd}");

        if (report != null)
        {
            sb.AppendLine($"【排期健康评分】：{report.OverallHealthScore}/100");
            sb.AppendLine($"【总排期时长】：{report.TotalScheduledHours:F1}h | 深度工作: {report.DeepWorkHours:F1}h (上限: {report.DeepWorkCapHours:F1}h)");
            sb.AppendLine($"【DoD 完备率】：{report.DoDComplianceRatio:F0}%");
            sb.AppendLine($"【缓冲缺失次数】：{report.MissingBufferCount} 次");
            if (report.Issues.Count > 0)
            {
                sb.AppendLine("【本地规则引擎初筛违规项】：");
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
    /// AI 功能 3: 智能排期建议与优化 (AI Schedule Suggestions)
    /// </summary>
    public async Task<string> SuggestScheduleAsync(List<ScheduleItem> items, List<ScheduleItem> backlogItems, DateTime targetDate, string? customGoal = null)
    {
        string systemInstruction = @"你是一个顶级的 AI 日程编排与效能优化顾问 (RMF AI Scheduling Advisor)。
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
            sb.AppendLine($"【用户的优先诉求/特别偏好】：{customGoal}");
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

        sb.AppendLine("\n请给出结构清晰、可操作性极强的智能排期与优化建议：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 兼容重载：绝对客观可靠的日程审查 (Schedule Audit)
    /// </summary>
    public async Task<string> AuditScheduleAsync(List<ScheduleItem> items, string currentActivity)
    {
        string systemInstruction = @"你是一个追求绝对客观、事实为本、高度严谨的效能分析与排期审计引擎 (RMF Objective Audit Engine)。
原则要求：
1. 严禁任何情绪化修辞、口号式打气或夸张说教，完全基于时间规划数据与客观规律进行理性推演。
2. 负荷与平衡性审计：定量核算总工时、连续专注时长，指出是否存在认知疲劳风险或无缓冲连续排期的致命弱点。
3. 真实活动偏离核验：比对当前桌面活跃进程与此时段的计划目标，指出实际执行与规划的契合状态。
4. 输出 2~3 条高度具体、具备执行可行性的排期调整建议。格式清晰、条理分明。";

        var sb = new StringBuilder();
        sb.AppendLine("【今日排期日程数据清单】：");
        if (items.Count == 0)
        {
            sb.AppendLine("（今日暂无录入排期）");
        }
        else
        {
            foreach (var item in items)
            {
                sb.AppendLine($"- [{item.StartTime:HH:mm} - {item.EndTime:HH:mm}] [{item.Category}] {item.Title} (状态: {item.Status}, 优先级: {item.Priority}, 预计: {item.EstimatedMinutes}分钟)");
                if (!string.IsNullOrWhiteSpace(item.Description))
                {
                    sb.AppendLine($"  描述: {item.Description}");
                }
            }
        }

        sb.AppendLine($"\n【当前桌面实际活动嗅探】：{currentActivity}");
        sb.AppendLine("请进行客观日程审查与可行性评估：");

        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 2. 精确严密的智能辅助编排日程 (Smart Planning)
    /// </summary>
    public async Task<string> PlanScheduleAsync(string userGoal, List<ScheduleItem> existingItems)
    {
        string systemInstruction = @"你是一个严谨精确的日程编排与时间块规划引擎。
原则要求：
1. 绝对客观，不添加任何情绪化语言。
2. 将用户提出的目标按认知负荷和工程实践拆解为具体的专注时间块（单块一般为 45~90 分钟）。
3. 严格避开已占用的时间段，并预留合理的休息和切换缓冲（至少 10~15 分钟）。
4. 明确给出建议时间段、具体交付产物及执行优先级。";

        var sb = new StringBuilder();
        sb.AppendLine($"【目标诉求】：\n{userGoal}\n");
        sb.AppendLine("【已占用不可冲突的时段】：");
        foreach (var item in existingItems)
        {
            sb.AppendLine($"- {item.StartTime:HH:mm} - {item.EndTime:HH:mm}: {item.Title}");
        }

        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 针对模糊意图提供量化明确的 Definition of Done (DoD) 建议
    /// </summary>
    public async Task<string> SuggestDoDAsync(string taskTitle)
    {
        string systemInstruction = @"你是一个敏捷开发与效能架构专家。
针对用户提出的模糊或未量化任务意图，给出一句极其具体、高度可检验的 Definition of Done (DoD) 验收标准。
原则要求：
1. 必须包含可检验的量化指标、具体交付物或验证方式（例如：跑通单元测试覆盖率>80% 并合并分支，或完成 3 组实验对比数据归档）。
2. 严禁空话套话，只输出该句验收标准，字数控制在 35 字以内。";

        string prompt = $"【待量化质检的任务意图】：{taskTitle}\n请给出明确的验收标准 (DoD)：";
        string result = await GenerateContentAsync(systemInstruction, prompt);
        return result.Trim(' ', '\r', '\n', '"', '“', '”', '`');
    }

    /// <summary>
    /// 针对排期健康体检报告提供专家级客观归因与优化裁决
    /// </summary>
    public async Task<string> DeepAuditScheduleWithAiAsync(AuditScanReport report, List<ScheduleItem> items)
    {
        string systemInstruction = @"你是一个冷酷、客观、严谨的 AI 日程调度与认知负荷审计员 (AI Scheduling Auditor)。
原则要求：
1. 绝对基于事实与数据，杜绝无意义的情绪打气与鸡汤。
2. 依据四大核心规则审查：
   - 规则 A (DoD 强制质检)：未量化任务的拖延风险；
   - 规则 B (认知负荷硬顶)：单日深度工作超 4.5h 的脑力透支与决策崩塌风险；
   - 规则 C (悲观膨胀与缓冲)：零间隙无缝连续排期的转场损耗；
   - 规则 D (精力曲线拟合)：峰值时段是否被琐碎事务侵占。
3. 输出 3 点尖锐且极具操作性的调度纠偏指令。";

        var sb = new StringBuilder();
        sb.AppendLine($"【体检基本盘】：健康评分 {report.OverallHealthScore}/100，排期总工时 {report.TotalScheduledHours:F1}h，深度工作 {report.DeepWorkHours:F1}h / 4.5h (DoD完备率: {report.DoDComplianceRatio:F0}%)");
        if (report.Issues.Count > 0)
        {
            sb.AppendLine("【检出的违规与隐患项】：");
            foreach (var iss in report.Issues)
            {
                sb.AppendLine($"- [{iss.Severity}] {iss.Title}: {iss.Description}");
            }
        }
        sb.AppendLine("\n【今日时间块明细】：");
        foreach (var it in items)
        {
            sb.AppendLine($"- [{it.StartTime:HH:mm}-{it.EndTime:HH:mm}] [{it.WorkType}] {it.Title} (DoD: {(string.IsNullOrEmpty(it.Dod) ? "无" : it.Dod)})");
        }

        sb.AppendLine("\n请给出冷酷客观的审计剖析与裁决指令：");
        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 3. 绝对客观的想法与决策推演 (Idea & Strategy Evaluation)
    /// </summary>
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

    /// <summary>
    /// 4. 真实自然语言记账解析 (Expense Parser)
    /// </summary>
    public async Task<(decimal amount, string category, string note)> ParseExpenseAsync(string rawInput)
    {
        var config = ConfigService.Load();
        if (!string.IsNullOrWhiteSpace(config.GeminiApiKey))
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
                // Fallback to local regex parser
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
