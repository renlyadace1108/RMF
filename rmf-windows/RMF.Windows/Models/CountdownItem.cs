using System;

namespace RMF.Windows.Models;

public class CountdownItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = string.Empty;
    public string TargetDate { get; set; } = DateTime.Today.ToString("yyyy-MM-dd");
    public string Category { get; set; } = "EXAM";
    public string ColorHex { get; set; } = "#F59E0B";
    public bool IsPinned { get; set; } = false;
    public bool IsDeleted { get; set; } = false;
    public string CreatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string UpdatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");

    public int RemainingDays
    {
        get
        {
            if (DateTime.TryParse(TargetDate, out var dt))
            {
                return (int)Math.Ceiling((dt.Date - DateTime.Today).TotalDays);
            }
            return 0;
        }
    }
}
