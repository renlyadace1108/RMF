namespace RMF.Windows.Models;

public class ExpenseCategoryItem
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Type { get; set; } = "EXPENSE"; // EXPENSE or INCOME
    public string Emoji { get; set; } = "🛒";
    public int SortOrder { get; set; } = 50;
    public int UsageCount { get; set; } = 0;
}
