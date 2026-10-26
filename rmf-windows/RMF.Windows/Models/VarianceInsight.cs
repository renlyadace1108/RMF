namespace RMF.Windows.Models;

public class VarianceInsight
{
    public string Icon { get; set; } = "💡";
    public string Title { get; set; } = string.Empty;
    public string Detail { get; set; } = string.Empty;
    public string Level { get; set; } = "INFO"; // "SUCCESS", "WARNING", "INFO"

    public string LevelColorHex => Level switch
    {
        "SUCCESS" => "#10B981",
        "WARNING" => "#EF4444",
        _ => "#38BDF8"
    };
}
