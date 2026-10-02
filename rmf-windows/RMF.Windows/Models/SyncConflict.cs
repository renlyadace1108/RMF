using System;

namespace RMF.Windows.Models;

public class SyncConflict
{
    public string TaskId { get; set; } = Guid.NewGuid().ToString();
    public ScheduleItem LocalSchedule { get; set; } = new();
    public ScheduleItem RemoteSchedule { get; set; } = new();
    public string ConflictReason { get; set; } = "多端在同一时间切片发生编辑冲突";
    public DateTime ConflictTime { get; set; } = DateTime.Now;
}
