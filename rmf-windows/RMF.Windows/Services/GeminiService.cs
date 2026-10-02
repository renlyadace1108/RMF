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
    /// 1. 绝对客观可靠的日程审查 (Schedule Audit)
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
}
