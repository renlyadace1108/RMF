using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class ExerciseInfo
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string EnglishName { get; set; } = string.Empty;
    public string NameEn { get => EnglishName; set => EnglishName = value; }
    public string Category { get; set; } = "CHEST";
    public string CategoryName { get; set; } = "胸部";
    public string Equipment { get; set; } = "杠铃";
    public int DefaultSets { get; set; } = 4;
    public int DefaultReps { get; set; } = 10;
    public double DefaultWeightKg { get; set; } = 20.0;
    public string Tips { get; set; } = string.Empty;
    public string PrimaryMuscles { get; set; } = "目标肌群";
}

public class TemplateExercise
{
    public string Name { get; set; } = string.Empty;
    public string Category { get; set; } = "CHEST";
    public string Equipment { get; set; } = "杠铃";
    public List<WorkoutSetItem> Sets { get; set; } = new();
    public double Calories { get; set; } = 70.0;
}

public class WorkoutTemplate
{
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Name { get => Title; set => Title = value; }
    public string Subtitle { get; set; } = string.Empty;
    public string WorkoutType { get; set; } = "STRENGTH";
    public int TargetDurationMinutes { get; set; } = 45;
    public int EstimatedMinutes { get => TargetDurationMinutes; set => TargetDurationMinutes = value; }
    public List<TemplateExercise> Exercises { get; set; } = new();
}

public static class ExerciseLibrary
{
    public static List<ExerciseInfo> SearchExercises(string query = "", string category = "ALL")
    {
        var list = AllExercises.AsEnumerable();
        if (!string.IsNullOrWhiteSpace(category) && !category.Equals("ALL", StringComparison.OrdinalIgnoreCase))
        {
            list = list.Where(e => e.Category.Equals(category, StringComparison.OrdinalIgnoreCase));
        }
        if (!string.IsNullOrWhiteSpace(query))
        {
            string q = query.Trim().ToLowerInvariant();
            list = list.Where(e => e.Name.ToLowerInvariant().Contains(q) ||
                                   e.EnglishName.ToLowerInvariant().Contains(q) ||
                                   e.Equipment.ToLowerInvariant().Contains(q) ||
                                   e.CategoryName.ToLowerInvariant().Contains(q));
        }
        return list.ToList();
    }

    public static readonly List<(string Key, string Name)> Categories = new()
    {
        ("ALL", "全部"),
        ("CHEST", "胸部"),
        ("BACK", "背部"),
        ("SHOULDERS", "肩部"),
        ("LEGS", "腿臀"),
        ("ARMS", "手臂"),
        ("CORE", "核心"),
        ("CARDIO", "有氧")
    };

    public static readonly List<ExerciseInfo> AllExercises = new()
    {
        // ==================== 胸部 CHEST ====================
        new() { Id = "ex_bench_press", Name = "杠铃平板卧推", EnglishName = "Barbell Bench Press", Category = "CHEST", CategoryName = "胸部", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 60.0, Tips = "核心收紧，下落触碰胸肌中下部，爆发力推起" },
        new() { Id = "ex_incline_db_press", Name = "上斜哑铃卧推", EnglishName = "Incline Dumbbell Press", Category = "CHEST", CategoryName = "胸部", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 22.0, Tips = "座椅30-45度，上胸发力，顶峰收缩" },
        new() { Id = "ex_machine_chest_press", Name = "坐姿器械推胸", EnglishName = "Chest Press Machine", Category = "CHEST", CategoryName = "胸部", Equipment = "器械", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 45.0, Tips = "沉肩后缩，肘部略低于肩膀" },
        new() { Id = "ex_db_fly", Name = "哑铃平板飞鸟", EnglishName = "Dumbbell Fly", Category = "CHEST", CategoryName = "胸部", Equipment = "哑铃", DefaultSets = 3, DefaultReps = 12, DefaultWeightKg = 12.0, Tips = "微屈手肘，感受胸大肌深层拉伸" },
        new() { Id = "ex_pec_deck_fly", Name = "蝴蝶机夹胸", EnglishName = "Pec Deck Fly", Category = "CHEST", CategoryName = "胸部", Equipment = "器械", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 35.0, Tips = "背部紧贴靠背，顶峰停顿1秒挤压胸肌中缝" },
        new() { Id = "ex_dips_chest", Name = "双杠臂屈伸 (胸部)", EnglishName = "Chest Dips", Category = "CHEST", CategoryName = "胸部", Equipment = "自重", DefaultSets = 3, DefaultReps = 10, DefaultWeightKg = 0.0, Tips = "身体前倾，手肘外展，下落至大臂平行地面" },
        new() { Id = "ex_pushup", Name = "标准俯卧撑", EnglishName = "Push-Ups", Category = "CHEST", CategoryName = "胸部", Equipment = "自重", DefaultSets = 4, DefaultReps = 15, DefaultWeightKg = 0.0, Tips = "身体呈一条直线，胸部贴地推起" },
        new() { Id = "ex_cable_crossover", Name = "绳索夹胸", EnglishName = "Cable Crossover", Category = "CHEST", CategoryName = "胸部", Equipment = "绳索", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 15.0, Tips = "全程保持张力，双手在体前交叉" },

        // ==================== 背部 BACK ====================
        new() { Id = "ex_deadlift", Name = "传统杠铃硬拉", EnglishName = "Conventional Deadlift", Category = "BACK", CategoryName = "背部", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 6, DefaultWeightKg = 80.0, Tips = "背部挺直，髋关节铰链，脚掌蹬地拉起" },
        new() { Id = "ex_pullup", Name = "宽距引体向上", EnglishName = "Wide-Grip Pull-Up", Category = "BACK", CategoryName = "背部", Equipment = "自重", DefaultSets = 4, DefaultReps = 8, DefaultWeightKg = 0.0, Tips = "背阔肌下沉发力，胸口向单杠靠近" },
        new() { Id = "ex_lat_pulldown", Name = "高位下拉", EnglishName = "Lat Pulldown", Category = "BACK", CategoryName = "背部", Equipment = "器械", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 50.0, Tips = "背阔肌主导收缩，下拉至锁骨位置" },
        new() { Id = "ex_barbell_row", Name = "俯身杠铃划船", EnglishName = "Barbell Bent-Over Row", Category = "BACK", CategoryName = "背部", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 50.0, Tips = "俯身45度，杠铃拉向肚脐方向" },
        new() { Id = "ex_seated_cable_row", Name = "坐姿绳索划船", EnglishName = "Seated Cable Row", Category = "BACK", CategoryName = "背部", Equipment = "绳索", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 45.0, Tips = "挺胸沉肩，大臂紧贴身体向后拉" },
        new() { Id = "ex_db_single_arm_row", Name = "单臂哑铃划船", EnglishName = "Single-Arm Dumbbell Row", Category = "BACK", CategoryName = "背部", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 20.0, Tips = "支撑稳定，向髋部方向弧线划动" },
        new() { Id = "ex_face_pull", Name = "绳索面拉", EnglishName = "Face Pull", Category = "BACK", CategoryName = "背部", Equipment = "绳索", DefaultSets = 4, DefaultReps = 15, DefaultWeightKg = 20.0, Tips = "强化后束与旋转肌袖，手肘高抬拉至面部两侧" },

        // ==================== 腿臀 LEGS ====================
        new() { Id = "ex_barbell_squat", Name = "杠铃深蹲", EnglishName = "Barbell Squat", Category = "LEGS", CategoryName = "腿臀", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 8, DefaultWeightKg = 70.0, Tips = "核心收紧，膝盖对准脚尖，下蹲至髋低于膝" },
        new() { Id = "ex_rdl", Name = "罗马尼亚硬拉 (RDL)", EnglishName = "Romanian Deadlift", Category = "LEGS", CategoryName = "腿臀", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 60.0, Tips = "屈髋推臀向后，充分拉伸腘绳肌与臀大肌" },
        new() { Id = "ex_leg_press", Name = "倒蹬机腿举", EnglishName = "Leg Press", Category = "LEGS", CategoryName = "腿臀", Equipment = "器械", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 120.0, Tips = "双脚与肩同宽，避免膝关节超伸锁死" },
        new() { Id = "ex_leg_extension", Name = "坐姿腿屈伸", EnglishName = "Leg Extension", Category = "LEGS", CategoryName = "腿臀", Equipment = "器械", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 40.0, Tips = "孤立股四头肌，顶峰收缩停顿1秒" },
        new() { Id = "ex_leg_curl", Name = "俯卧腿弯举", EnglishName = "Lying Leg Curl", Category = "LEGS", CategoryName = "腿臀", Equipment = "器械", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 35.0, Tips = "强化腘绳肌，控制离心慢放" },
        new() { Id = "ex_hip_thrust", Name = "杠铃臀推", EnglishName = "Barbell Hip Thrust", Category = "LEGS", CategoryName = "腿臀", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 60.0, Tips = "上背靠凳，顶峰臀部强力夹紧收缩" },
        new() { Id = "ex_calf_raise", Name = "站姿提踵", EnglishName = "Standing Calf Raise", Category = "LEGS", CategoryName = "腿臀", Equipment = "器械", DefaultSets = 4, DefaultReps = 15, DefaultWeightKg = 50.0, Tips = "脚跟深踩拉伸，前脚掌发力顶峰停顿" },

        // ==================== 肩部 SHOULDERS ====================
        new() { Id = "ex_overhead_press", Name = "站姿杠铃推举 (OHP)", EnglishName = "Overhead Press", Category = "SHOULDERS", CategoryName = "肩部", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 8, DefaultWeightKg = 40.0, Tips = "躯干直立不后仰，垂直向上推过头顶" },
        new() { Id = "ex_seated_db_press", Name = "坐姿哑铃推肩", EnglishName = "Seated Dumbbell Shoulder Press", Category = "SHOULDERS", CategoryName = "肩部", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 18.0, Tips = "肘关节稍向前收，推起不过度碰铃" },
        new() { Id = "ex_db_lateral_raise", Name = "哑铃侧平举", EnglishName = "Dumbbell Lateral Raise", Category = "SHOULDERS", CategoryName = "肩部", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 15, DefaultWeightKg = 8.0, Tips = "身体微前倾，手肘引领带起哑铃" },
        new() { Id = "ex_cable_lateral_raise", Name = "绳索侧平举", EnglishName = "Cable Lateral Raise", Category = "SHOULDERS", CategoryName = "肩部", Equipment = "绳索", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 7.5, Tips = "绳索全程恒定张力，极致泵感" },
        new() { Id = "ex_rear_delt_fly", Name = "俯身哑铃飞鸟", EnglishName = "Rear Delt Dumbbell Fly", Category = "SHOULDERS", CategoryName = "肩部", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 15, DefaultWeightKg = 7.0, Tips = "孤立三角肌后束，手臂微屈展开" },

        // ==================== 手臂 ARMS ====================
        new() { Id = "ex_barbell_curl", Name = "杠铃二头弯举", EnglishName = "Barbell Bicep Curl", Category = "ARMS", CategoryName = "手臂", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 25.0, Tips = "大臂紧贴身体，身体不前后摇晃借力" },
        new() { Id = "ex_db_hammer_curl", Name = "哑铃交替锤式弯举", EnglishName = "Hammer Curl", Category = "ARMS", CategoryName = "手臂", Equipment = "哑铃", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 12.0, Tips = "对握掌心，雕刻肱肌与前臂线条" },
        new() { Id = "ex_rope_pushdown", Name = "绳索三头下压", EnglishName = "Rope Tricep Pushdown", Category = "ARMS", CategoryName = "手臂", Equipment = "绳索", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 25.0, Tips = "底部用力分开绳索，深度挤压三头肌" },
        new() { Id = "ex_skull_crusher", Name = "仰卧杠铃臂屈伸", EnglishName = "Skull Crusher", Category = "ARMS", CategoryName = "手臂", Equipment = "杠铃", DefaultSets = 4, DefaultReps = 10, DefaultWeightKg = 25.0, Tips = "手肘固定指向天花板，下落至额头上方" },

        // ==================== 核心 CORE ====================
        new() { Id = "ex_hanging_leg_raise", Name = "悬垂举腿", EnglishName = "Hanging Leg Raise", Category = "CORE", CategoryName = "核心", Equipment = "自重", DefaultSets = 4, DefaultReps = 12, DefaultWeightKg = 0.0, Tips = "骨盆后倾卷起，强化下腹肌群" },
        new() { Id = "ex_ab_wheel", Name = "健腹轮推拉", EnglishName = "Ab Wheel Rollout", Category = "CORE", CategoryName = "核心", Equipment = "器械", DefaultSets = 3, DefaultReps = 10, DefaultWeightKg = 0.0, Tips = "骨盆后倾，腹肌全程紧绷受控" },
        new() { Id = "ex_plank", Name = "平板支撑", EnglishName = "Plank", Category = "CORE", CategoryName = "核心", Equipment = "自重", DefaultSets = 3, DefaultReps = 60, DefaultWeightKg = 0.0, Tips = "头部、背部、臀部呈一条直线，保持均匀呼吸" },

        // ==================== 有氧 CARDIO ====================
        new() { Id = "ex_treadmill_run", Name = "跑步机变速跑", EnglishName = "Treadmill Run", Category = "CARDIO", CategoryName = "有氧", Equipment = "有氧", DefaultSets = 1, DefaultReps = 1, DefaultWeightKg = 0.0, Tips = "快慢交替间歇跑，极速燃脂与提高心肺耐力" },
        new() { Id = "ex_rowing_machine", Name = "划船机间歇划", EnglishName = "Rowing Machine", Category = "CARDIO", CategoryName = "有氧", Equipment = "器械", DefaultSets = 1, DefaultReps = 1, DefaultWeightKg = 0.0, Tips = "腿部蹬伸为主，背部顺势拉动手柄" },
        new() { Id = "ex_burpee", Name = "波比跳 (Burpees)", EnglishName = "Burpees", Category = "CARDIO", CategoryName = "有氧", Equipment = "自重", DefaultSets = 3, DefaultReps = 15, DefaultWeightKg = 0.0, Tips = "全身爆发力协同，心率飙升之王" }
    };

    public static List<ExerciseInfo> Search(string keyword, string cat = "ALL")
    {
        var baseList = cat == "ALL" ? AllExercises : AllExercises.Where(e => e.Category.Equals(cat, StringComparison.OrdinalIgnoreCase)).ToList();
        if (string.IsNullOrWhiteSpace(keyword)) return baseList;
        var kw = keyword.Trim().ToLowerInvariant();
        return baseList.Where(e =>
            e.Name.ToLowerInvariant().Contains(kw) ||
            e.EnglishName.ToLowerInvariant().Contains(kw) ||
            e.Equipment.ToLowerInvariant().Contains(kw)).ToList();
    }

    public static readonly List<WorkoutTemplate> Templates = new()
    {
        new()
        {
            Id = "tpl_ppl_push",
            Title = "经典推日 (Push Day)",
            Subtitle = "胸部、三角肌前中束、肱三头肌高强度复合刺激",
            WorkoutType = "STRENGTH",
            TargetDurationMinutes = 50,
            Exercises = new()
            {
                new() { Name = "杠铃平板卧推", Category = "CHEST", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "W", WeightKg = 40, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 60, Reps = 10 }, new() { SetNumber = 3, Type = "N", WeightKg = 65, Reps = 8 }, new() { SetNumber = 4, Type = "F", WeightKg = 70, Reps = 6 } } },
                new() { Name = "上斜哑铃卧推", Category = "CHEST", Equipment = "哑铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 22, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 22, Reps = 10 }, new() { SetNumber = 3, Type = "N", WeightKg = 24, Reps = 8 }, new() { SetNumber = 4, Type = "D", WeightKg = 18, Reps = 10 } } },
                new() { Name = "坐姿哑铃推肩", Category = "SHOULDERS", Equipment = "哑铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 18, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 18, Reps = 10 }, new() { SetNumber = 3, Type = "N", WeightKg = 20, Reps = 8 } } },
                new() { Name = "哑铃侧平举", Category = "SHOULDERS", Equipment = "哑铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 8, Reps = 15 }, new() { SetNumber = 2, Type = "N", WeightKg = 8, Reps = 15 }, new() { SetNumber = 3, Type = "N", WeightKg = 9, Reps = 12 }, new() { SetNumber = 4, Type = "F", WeightKg = 9, Reps = 12 } } },
                new() { Name = "绳索三头下压", Category = "ARMS", Equipment = "绳索", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 25, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 25, Reps = 12 }, new() { SetNumber = 3, Type = "N", WeightKg = 30, Reps = 10 }, new() { SetNumber = 4, Type = "D", WeightKg = 20, Reps = 12 } } }
            }
        },
        new()
        {
            Id = "tpl_ppl_pull",
            Title = "经典拉日 (Pull Day)",
            Subtitle = "背阔肌宽度、上背厚度、三角肌后束与二头肌轰炸",
            WorkoutType = "STRENGTH",
            TargetDurationMinutes = 50,
            Exercises = new()
            {
                new() { Name = "传统杠铃硬拉", Category = "BACK", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "W", WeightKg = 60, Reps = 8 }, new() { SetNumber = 2, Type = "N", WeightKg = 90, Reps = 6 }, new() { SetNumber = 3, Type = "N", WeightKg = 100, Reps = 5 } } },
                new() { Name = "高位下拉", Category = "BACK", Equipment = "器械", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 45, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 50, Reps = 10 }, new() { SetNumber = 3, Type = "N", WeightKg = 55, Reps = 8 } } },
                new() { Name = "俯身杠铃划船", Category = "BACK", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 50, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 55, Reps = 8 } } },
                new() { Name = "绳索面拉", Category = "BACK", Equipment = "绳索", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 20, Reps = 15 }, new() { SetNumber = 2, Type = "N", WeightKg = 20, Reps = 15 } } },
                new() { Name = "哑铃交替锤式弯举", Category = "ARMS", Equipment = "哑铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 12, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 12, Reps = 12 } } }
            }
        },
        new()
        {
            Id = "tpl_ppl_legs",
            Title = "经典腿臀日 (Legs Day)",
            Subtitle = "深蹲主导，股四头肌、腘绳肌、臀部与小腿全维生长",
            WorkoutType = "STRENGTH",
            TargetDurationMinutes = 55,
            Exercises = new()
            {
                new() { Name = "杠铃深蹲", Category = "LEGS", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "W", WeightKg = 40, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 70, Reps = 8 }, new() { SetNumber = 3, Type = "N", WeightKg = 80, Reps = 6 } } },
                new() { Name = "罗马尼亚硬拉 (RDL)", Category = "LEGS", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 60, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 65, Reps = 8 } } },
                new() { Name = "倒蹬机腿举", Category = "LEGS", Equipment = "器械", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 120, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 140, Reps = 10 } } },
                new() { Name = "坐姿腿屈伸", Category = "LEGS", Equipment = "器械", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 35, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 40, Reps = 10 } } },
                new() { Name = "站姿提踵", Category = "LEGS", Equipment = "器械", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 50, Reps = 15 }, new() { SetNumber = 2, Type = "N", WeightKg = 55, Reps = 15 } } }
            }
        },
        new()
        {
            Id = "tpl_upper",
            Title = "上肢塑形强化 (Upper Body)",
            Subtitle = "卧推+划船+推肩拮抗组合，适合二分化高频训练者",
            WorkoutType = "STRENGTH",
            TargetDurationMinutes = 45,
            Exercises = new()
            {
                new() { Name = "杠铃平板卧推", Category = "CHEST", Equipment = "杠铃", Sets = new() { new() { SetNumber = 1, Type = "W", WeightKg = 40, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 60, Reps = 10 } } },
                new() { Name = "坐姿绳索划船", Category = "BACK", Equipment = "绳索", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 45, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 50, Reps = 10 } } },
                new() { Name = "坐姿哑铃推肩", Category = "SHOULDERS", Equipment = "哑铃", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 18, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 18, Reps = 10 } } },
                new() { Name = "宽距引体向上", Category = "BACK", Equipment = "自重", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 0, Reps = 8 }, new() { SetNumber = 2, Type = "N", WeightKg = 0, Reps = 8 } } },
                new() { Name = "绳索三头下压", Category = "ARMS", Equipment = "绳索", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 25, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 25, Reps = 12 } } }
            }
        },
        new()
        {
            Id = "tpl_cardio_hiit",
            Title = "高效燃脂与心肺有氧 (Fat Burn & HIIT)",
            Subtitle = "跑步机变速冲刺结合核心抗伸展，极速代谢燃脂",
            WorkoutType = "CARDIO",
            TargetDurationMinutes = 40,
            Exercises = new()
            {
                new() { Name = "跑步机变速跑", Category = "CARDIO", Equipment = "有氧", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 0, Reps = 1 } }, Calories = 220 },
                new() { Name = "悬垂举腿", Category = "CORE", Equipment = "自重", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 0, Reps = 12 }, new() { SetNumber = 2, Type = "N", WeightKg = 0, Reps = 12 } } },
                new() { Name = "健腹轮推拉", Category = "CORE", Equipment = "器械", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 0, Reps = 10 }, new() { SetNumber = 2, Type = "N", WeightKg = 0, Reps = 10 } } },
                new() { Name = "波比跳 (Burpees)", Category = "CARDIO", Equipment = "自重", Sets = new() { new() { SetNumber = 1, Type = "N", WeightKg = 0, Reps = 15 }, new() { SetNumber = 2, Type = "N", WeightKg = 0, Reps = 15 } }, Calories = 90 }
            }
        }
    };
}
