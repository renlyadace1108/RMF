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
                // 只保留支持 generateContent 的文本/多模态推理模型
                if (m.SupportedGenerationMethods != null && m.SupportedGenerationMethods.Contains("generateContent"))
                {
                    list.Add(m);
                }
            }
        }

        // 默认将 flash/pro 置顶排序
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

        string model = config.GetEffectiveModel();
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
                temperature = config.Temperature > 0 ? config.Temperature : 0.7,
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

    private string GetToneInstruction()
    {
        var config = ConfigService.Load();
        return config.SupervisorTone switch
        {
            "strict" => "【语气风格：严格鞭策型】毫不客气地指出用户的懈怠、拖延和目标偏离，以极高标准要求执行力，语言直接犀利，杜绝温和空话。",
            "encouraging" => "【语气风格：温和陪伴型】富有同理心，多肯定用户的努力与专注，在指出问题时提供正向激励和缓解压力的方法。",
            _ => "【语气风格：理性客观型】专业、冷静、基于事实，像一流的高级项目总监一样分析 ROI、时间效率与执行节奏。"
        };
    }

    /// <summary>
    /// 1. 审查日程排期 (Schedule Audit)
    /// </summary>
    public async Task<string> AuditScheduleAsync(List<ScheduleItem> items, string currentActivity)
    {
        string tone = GetToneInstruction();
        string systemInstruction = $@"你是用户的个人私人效能主管与日程审查 AI (RMF Supervisor)。
{tone}
你的任务是审查用户今日的日程安排：
1. 评估合理性：任务密度是否过载？是否有足够的缓冲和休息时间？
2. 识别风险点：哪些任务容易发生拖延？精力峰值与任务类型是否匹配？
3. 结合当前前台状态：用户当前电脑正在运行的应用是否与计划冲突？
4. 给出 2~3 条极为具体、立竿见影的排期优化建议。
请用清晰精炼的 Markdown 格式输出。";

        var sb = new StringBuilder();
        sb.AppendLine("【用户今日排期日程表】：");
        if (items.Count == 0)
        {
            sb.AppendLine("（暂无安排）");
        }
        else
        {
            foreach (var item in items)
            {
                sb.AppendLine($"- [{item.StartTime:HH:mm} - {item.EndTime:HH:mm}] [{item.Category}] {item.Title} (状态: {item.Status}, 优先级: {item.Priority})");
                if (!string.IsNullOrWhiteSpace(item.Description))
                {
                    sb.AppendLine($"  备注: {item.Description}");
                }
            }
        }

        sb.AppendLine($"\n【当前桌面实际活动嗅探】：{currentActivity}");
        sb.AppendLine("请开始审查并给出专业建议：");

        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 2. 智能辅助编排日程 (Smart Planning)
    /// </summary>
    public async Task<string> PlanScheduleAsync(string userGoal, List<ScheduleItem> existingItems)
    {
        string tone = GetToneInstruction();
        string systemInstruction = $@"你是 RMF 的智能排程助手。
{tone}
用户会提出一个或多个目标想法（可能很模糊），请你：
1. 将大目标合理拆解为 1~3 个具体可落地的时间块（建议 45~90 分钟单次深度专注）；
2. 避开用户已有的日程安排；
3. 输出明确的时间建议、任务名称与优先级。
请用简洁有条理的 Markdown 输出推荐安排。";

        var sb = new StringBuilder();
        sb.AppendLine($"【用户想要安排的目标】：\n{userGoal}\n");
        sb.AppendLine("【已占用时间段】：");
        foreach (var item in existingItems)
        {
            sb.AppendLine($"- {item.StartTime:HH:mm} - {item.EndTime:HH:mm}: {item.Title}");
        }

        return await GenerateContentAsync(systemInstruction, sb.ToString());
    }

    /// <summary>
    /// 3. 评估用户的想法、灵感与决策 (Idea & Strategy Evaluation)
    /// </summary>
    public async Task<string> EvaluateIdeaAsync(string userIdea, string currentContext)
    {
        string tone = GetToneInstruction();
        string systemInstruction = $@"你是用户的 AI 智囊兼首席监督官。
{tone}
面对用户提出的突发想法、技术方案灵感或生活决策，你需要充当深度推演的思考伙伴：
1. 价值与可行性评估：这个想法的核心亮点是什么？是否有隐藏的坑或高昂的沉没成本？
2. 注意力防分散审查：当前是启动这个想法的最佳时机吗？它是否在诱惑用户从当前核心主线任务中分心？
3. 执行路径建议：如果要做，最小可行性（MVP）的第一步应该是什么？
请直切要害，避免空话套话，用富有洞察力的语言回复。";

        string userPrompt = $"【用户的突发想法 / 决策诉求】：\n{userIdea}\n\n【用户当前工作主线背景】：\n{currentContext}\n\n请进行深度评估与监督反馈：";

        return await GenerateContentAsync(systemInstruction, userPrompt);
    }
}
