using System;

namespace RMF.Windows.Models;

public class NoteItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = string.Empty;
    public string Content { get; set; } = string.Empty;
    public string Category { get; set; } = "灵感"; // 灵感, 思考, 闪念, 待办, 摘录, 生活
    public string ColorHex { get; set; } = "#F59E0B";
    public bool IsPinned { get; set; } = false;
    public bool IsDeleted { get; set; } = false;
    public string CreatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string UpdatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");

    public string DisplayTime
    {
        get
        {
            if (DateTime.TryParse(UpdatedAt, out var dt))
            {
                return dt.ToString("yyyy-MM-dd HH:mm");
            }
            return UpdatedAt;
        }
    }

    public string PreviewContent
    {
        get
        {
            if (string.IsNullOrWhiteSpace(Content)) return "(无内容)";
            var lines = Content.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            var firstLine = lines.Length > 0 ? lines[0] : Content;
            return firstLine.Length > 80 ? firstLine.Substring(0, 80) + "..." : firstLine;
        }
    }
}
