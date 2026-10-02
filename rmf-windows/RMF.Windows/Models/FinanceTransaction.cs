using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class FinanceTransaction
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("type")]
    public string Type { get; set; } = "EXPENSE"; // EXPENSE, INCOME

    [JsonPropertyName("amount")]
    public decimal Amount { get; set; }

    [JsonPropertyName("category")]
    public string Category { get; set; } = "餐饮美食";

    [JsonPropertyName("note")]
    public string Note { get; set; } = string.Empty;

    [JsonPropertyName("raw_input_text")]
    public string RawInputText { get; set; } = string.Empty;

    [JsonPropertyName("transaction_time")]
    public DateTime TransactionTime { get; set; } = DateTime.Now;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;
}
