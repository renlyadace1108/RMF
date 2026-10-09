package com.renly.rmf.domain.fitness

import com.renly.rmf.domain.fitness.model.WorkoutSet

/**
 * 动作库中单项动作元数据
 */
data class ExerciseInfo(
    val id: String,
    val name: String,
    val englishName: String,
    val category: String, // CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO
    val categoryName: String,
    val equipment: String, // 杠铃, 哑铃, 绳索, 器械, 自重, 有氧
    val defaultSets: Int = 4,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double = 20.0,
    val tips: String = ""
)

/**
 * 训练分化模版定义
 */
data class WorkoutTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val workoutType: String, // STRENGTH, CARDIO, HIIT, STRETCH
    val targetDurationMinutes: Int,
    val exercises: List<TemplateExercise>
)

data class TemplateExercise(
    val name: String,
    val category: String,
    val equipment: String,
    val sets: List<WorkoutSet>,
    val calories: Double = 70.0
)

object ExerciseLibrary {

    val CATEGORIES = listOf(
        "ALL" to "全部",
        "CHEST" to "胸部",
        "BACK" to "背部",
        "SHOULDERS" to "肩部",
        "LEGS" to "腿臀",
        "ARMS" to "手臂",
        "CORE" to "核心",
        "CARDIO" to "有氧"
    )

    val ALL_EXERCISES: List<ExerciseInfo> = listOf(
        // ==================== 胸部 CHEST ====================
        ExerciseInfo("ex_bench_press", "杠铃平板卧推", "Barbell Bench Press", "CHEST", "胸部", "杠铃", 4, 10, 60.0, "核心收紧，下落触碰胸肌中下部，爆发力推起"),
        ExerciseInfo("ex_incline_db_press", "上斜哑铃卧推", "Incline Dumbbell Press", "CHEST", "胸部", "哑铃", 4, 10, 22.0, "座椅30-45度，上胸发力，顶峰收缩"),
        ExerciseInfo("ex_machine_chest_press", "坐姿器械推胸", "Chest Press Machine", "CHEST", "胸部", "器械", 4, 12, 45.0, "沉肩后缩，肘部略低于肩膀"),
        ExerciseInfo("ex_db_fly", "哑铃平板飞鸟", "Dumbbell Fly", "CHEST", "胸部", "哑铃", 3, 12, 12.0, "微屈手肘，感受胸大肌深层拉伸"),
        ExerciseInfo("ex_pec_deck_fly", "蝴蝶机夹胸", "Pec Deck Fly", "CHEST", "胸部", "器械", 4, 12, 35.0, "背部紧贴靠背，顶峰停顿1秒挤压胸肌中缝"),
        ExerciseInfo("ex_dips_chest", "双杠臂屈伸 (胸部)", "Chest Dips", "CHEST", "胸部", "自重", 3, 10, 0.0, "身体前倾，手肘外展，下落至大臂平行地面"),
        ExerciseInfo("ex_pushup", "标准俯卧撑", "Push-Ups", "CHEST", "胸部", "自重", 4, 15, 0.0, "身体呈一条直线，胸部贴地推起"),
        ExerciseInfo("ex_cable_crossover", "绳索夹胸", "Cable Crossover", "CHEST", "胸部", "绳索", 4, 12, 15.0, "全程保持张力，双手在体前交叉"),

        // ==================== 背部 BACK ====================
        ExerciseInfo("ex_deadlift", "传统杠铃硬拉", "Conventional Deadlift", "BACK", "背部", "杠铃", 4, 6, 80.0, "背部挺直，髋关节铰链，脚掌蹬地拉起"),
        ExerciseInfo("ex_pullup", "宽距引体向上", "Wide-Grip Pull-Up", "BACK", "背部", "自重", 4, 8, 0.0, "背阔肌下沉发力，胸口向单杠靠近"),
        ExerciseInfo("ex_lat_pulldown", "高位下拉", "Lat Pulldown", "BACK", "背部", "器械", 4, 10, 50.0, "背阔肌主导收缩，下拉至锁骨位置"),
        ExerciseInfo("ex_barbell_row", "俯身杠铃划船", "Barbell Bent-Over Row", "BACK", "背部", "杠铃", 4, 10, 50.0, "俯身45度，杠铃拉向肚脐方向"),
        ExerciseInfo("ex_seated_cable_row", "坐姿绳索划船", "Seated Cable Row", "BACK", "背部", "绳索", 4, 12, 45.0, "挺胸沉肩，大臂紧贴身体向后拉"),
        ExerciseInfo("ex_db_single_arm_row", "单臂哑铃划船", "Single-Arm Dumbbell Row", "BACK", "背部", "哑铃", 4, 10, 20.0, "支撑稳定，向髋部方向弧线划动"),
        ExerciseInfo("ex_face_pull", "绳索面拉", "Face Pull", "BACK", "背部", "绳索", 4, 15, 20.0, "强化后束与旋转肌袖，手肘高抬拉至面部两侧"),
        ExerciseInfo("ex_straight_arm_pulldown", "直臂绳索下压", "Straight-Arm Pulldown", "BACK", "背部", "绳索", 4, 12, 25.0, "手臂微屈锁死，孤立刺激背阔肌"),

        // ==================== 腿臀 LEGS ====================
        ExerciseInfo("ex_barbell_squat", "杠铃深蹲", "Barbell Squat", "LEGS", "腿臀", "杠铃", 4, 8, 70.0, "核心收紧，膝盖对准脚尖，下蹲至髋低于膝"),
        ExerciseInfo("ex_rdl", "罗马尼亚硬拉 (RDL)", "Romanian Deadlift", "LEGS", "腿臀", "杠铃", 4, 10, 60.0, "屈髋推臀向后，充分拉伸腘绳肌与臀大肌"),
        ExerciseInfo("ex_leg_press", "倒蹬机腿举", "Leg Press", "LEGS", "腿臀", "器械", 4, 12, 120.0, "双脚与肩同宽，避免膝关节超伸锁死"),
        ExerciseInfo("ex_leg_extension", "坐姿腿屈伸", "Leg Extension", "LEGS", "腿臀", "器械", 4, 12, 40.0, "孤立股四头肌，顶峰收缩停顿1秒"),
        ExerciseInfo("ex_leg_curl", "俯卧腿弯举", "Lying Leg Curl", "LEGS", "腿臀", "器械", 4, 12, 35.0, "强化腘绳肌，控制离心慢放"),
        ExerciseInfo("ex_db_lunge", "哑铃剪步蹲", "Dumbbell Lunge", "LEGS", "腿臀", "哑铃", 3, 12, 14.0, "步幅适中，前膝不内扣，躯干稳定"),
        ExerciseInfo("ex_hip_thrust", "杠铃臀推", "Barbell Hip Thrust", "LEGS", "腿臀", "杠铃", 4, 10, 60.0, "上背靠凳，顶峰臀部强力夹紧收缩"),
        ExerciseInfo("ex_calf_raise", "站姿提踵", "Standing Calf Raise", "LEGS", "腿臀", "器械", 4, 15, 50.0, "脚跟深踩拉伸，前脚掌发力顶峰停顿"),

        // ==================== 肩部 SHOULDERS ====================
        ExerciseInfo("ex_overhead_press", "站姿杠铃推举 (OHP)", "Overhead Press", "SHOULDERS", "肩部", "杠铃", 4, 8, 40.0, "躯干直立不后仰，垂直向上推过头顶"),
        ExerciseInfo("ex_seated_db_press", "坐姿哑铃推肩", "Seated Dumbbell Shoulder Press", "SHOULDERS", "肩部", "哑铃", 4, 10, 18.0, "肘关节稍向前收，推起不过度碰铃"),
        ExerciseInfo("ex_db_lateral_raise", "哑铃侧平举", "Dumbbell Lateral Raise", "SHOULDERS", "肩部", "哑铃", 4, 15, 8.0, "身体微前倾，手肘引领带起哑铃"),
        ExerciseInfo("ex_cable_lateral_raise", "绳索侧平举", "Cable Lateral Raise", "SHOULDERS", "肩部", "绳索", 4, 12, 7.5, "绳索全程恒定张力，极致泵感"),
        ExerciseInfo("ex_rear_delt_fly", "俯身哑铃飞鸟", "Rear Delt Dumbbell Fly", "SHOULDERS", "肩部", "哑铃", 4, 15, 7.0, "孤立三角肌后束，手臂微屈展开"),
        ExerciseInfo("ex_arnold_press", "阿诺德推举", "Arnold Press", "SHOULDERS", "肩部", "哑铃", 3, 10, 16.0, "旋转动作轨迹，全方位轰炸三角肌"),

        // ==================== 手臂 ARMS ====================
        ExerciseInfo("ex_barbell_curl", "杠铃二头弯举", "Barbell Bicep Curl", "ARMS", "手臂", "杠铃", 4, 10, 25.0, "大臂紧贴身体，身体不前后摇晃借力"),
        ExerciseInfo("ex_db_hammer_curl", "哑铃交替锤式弯举", "Hammer Curl", "ARMS", "手臂", "哑铃", 4, 12, 12.0, "对握掌心，雕刻肱肌与前臂线条"),
        ExerciseInfo("ex_preacher_curl", "牧师凳弯举", "Preacher Curl", "ARMS", "手臂", "器械", 4, 10, 20.0, "完全限制借力，专注二头峰度"),
        ExerciseInfo("ex_rope_pushdown", "绳索三头下压", "Rope Tricep Pushdown", "ARMS", "手臂", "绳索", 4, 12, 25.0, "底部用力分开绳索，深度挤压三头肌"),
        ExerciseInfo("ex_skull_crusher", "仰卧杠铃臂屈伸", "Skull Crusher", "ARMS", "手臂", "杠铃", 4, 10, 25.0, "手肘固定指向天花板，下落至额头上方"),
        ExerciseInfo("ex_close_grip_bench", "窄距杠铃卧推", "Close-Grip Bench Press", "ARMS", "手臂", "杠铃", 4, 8, 50.0, "双手握距与肩同宽，三头大重量轰炸"),

        // ==================== 核心 CORE ====================
        ExerciseInfo("ex_hanging_leg_raise", "悬垂举腿", "Hanging Leg Raise", "CORE", "核心", "自重", 4, 12, 0.0, "盆骨后倾卷动，下腹深度收缩"),
        ExerciseInfo("ex_ab_wheel", "健腹轮推拉", "Ab Wheel Rollout", "CORE", "核心", "器械", 4, 10, 0.0, "臀部收紧，不过度塌腰，核心全力制动"),
        ExerciseInfo("ex_plank", "标准平板支撑", "Plank", "CORE", "核心", "自重", 3, 60, 0.0, "全身保持一条直线，深层核心稳定抗伸展"),
        ExerciseInfo("ex_russian_twist", "俄罗斯转体", "Russian Twist", "CORE", "核心", "自重", 3, 20, 5.0, "双腿悬空，躯干左右旋转刺激腹斜肌"),
        ExerciseInfo("ex_cable_crunch", "绳索跪姿卷腹", "Cable Crunch", "CORE", "核心", "绳索", 4, 15, 30.0, "弓背卷腹，用腹肌力量下压绳索"),

        // ==================== 有氧 CARDIO ====================
        ExerciseInfo("ex_treadmill_run", "跑步机变速跑 (HIIT)", "Treadmill Intervals", "CARDIO", "有氧", "有氧", 1, 1, 0.0, "快慢交替，高效燃烧脂肪与提升心肺"),
        ExerciseInfo("ex_rowing_machine", "划船机有氧", "Rowing Machine", "CARDIO", "有氧", "有氧", 1, 1, 0.0, "全身85%肌肉参与，低冲击护膝有氧"),
        ExerciseInfo("ex_spin_bike", "动感单车冲刺", "Spin Bike", "CARDIO", "有氧", "有氧", 1, 1, 0.0, "高阻力冲刺与踏频配合"),
        ExerciseInfo("ex_jump_rope", "快速跳绳", "Jump Rope", "CARDIO", "有氧", "有氧", 4, 150, 0.0, "前脚掌着地，小臂与手腕轻摇绳索"),
        ExerciseInfo("ex_burpee", "波比跳 (Burpees)", "Burpees", "CARDIO", "有氧", "自重", 4, 15, 0.0, "全身爆发力极速燃脂杀手")
    )

    fun getByCategory(cat: String): List<ExerciseInfo> {
        if (cat == "ALL") return ALL_EXERCISES
        return ALL_EXERCISES.filter { it.category.equals(cat, ignoreCase = true) }
    }

    fun search(keyword: String, cat: String = "ALL"): List<ExerciseInfo> {
        val base = getByCategory(cat)
        if (keyword.isBlank()) return base
        val kw = keyword.trim().lowercase()
        return base.filter {
            it.name.lowercase().contains(kw) ||
            it.englishName.lowercase().contains(kw) ||
            it.equipment.lowercase().contains(kw)
        }
    }

    val TEMPLATES: List<WorkoutTemplate> get() = PRESET_TEMPLATES

    val PRESET_TEMPLATES: List<WorkoutTemplate> = listOf(
        WorkoutTemplate(
            id = "tpl_ppl_push",
            title = "经典推日 (Push Day)",
            subtitle = "胸部、三角肌前中束、肱三头肌高强度复合刺激",
            workoutType = "STRENGTH",
            targetDurationMinutes = 50,
            exercises = listOf(
                createTemplateExercise("杠铃平板卧推", "CHEST", "杠铃", listOf(
                    WorkoutSet(1, "W", 40.0, 12),
                    WorkoutSet(2, "N", 60.0, 10),
                    WorkoutSet(3, "N", 65.0, 8),
                    WorkoutSet(4, "F", 70.0, 6)
                )),
                createTemplateExercise("上斜哑铃卧推", "CHEST", "哑铃", listOf(
                    WorkoutSet(1, "N", 22.0, 10),
                    WorkoutSet(2, "N", 22.0, 10),
                    WorkoutSet(3, "N", 24.0, 8),
                    WorkoutSet(4, "D", 18.0, 10)
                )),
                createTemplateExercise("坐姿哑铃推肩", "SHOULDERS", "哑铃", listOf(
                    WorkoutSet(1, "N", 18.0, 10),
                    WorkoutSet(2, "N", 18.0, 10),
                    WorkoutSet(3, "N", 20.0, 8)
                )),
                createTemplateExercise("哑铃侧平举", "SHOULDERS", "哑铃", listOf(
                    WorkoutSet(1, "N", 8.0, 15),
                    WorkoutSet(2, "N", 8.0, 15),
                    WorkoutSet(3, "N", 9.0, 12),
                    WorkoutSet(4, "F", 9.0, 12)
                )),
                createTemplateExercise("绳索三头下压", "ARMS", "绳索", listOf(
                    WorkoutSet(1, "N", 25.0, 12),
                    WorkoutSet(2, "N", 25.0, 12),
                    WorkoutSet(3, "N", 30.0, 10),
                    WorkoutSet(4, "D", 20.0, 12)
                ))
            )
        ),
        WorkoutTemplate(
            id = "tpl_ppl_pull",
            title = "经典拉日 (Pull Day)",
            subtitle = "背阔肌宽度、上背厚度、三角肌后束与二头肌轰炸",
            workoutType = "STRENGTH",
            targetDurationMinutes = 50,
            exercises = listOf(
                createTemplateExercise("传统杠铃硬拉", "BACK", "杠铃", listOf(
                    WorkoutSet(1, "W", 60.0, 8),
                    WorkoutSet(2, "N", 90.0, 6),
                    WorkoutSet(3, "N", 100.0, 5),
                    WorkoutSet(4, "N", 110.0, 3)
                )),
                createTemplateExercise("高位下拉", "BACK", "器械", listOf(
                    WorkoutSet(1, "N", 45.0, 12),
                    WorkoutSet(2, "N", 50.0, 10),
                    WorkoutSet(3, "N", 55.0, 8),
                    WorkoutSet(4, "F", 55.0, 8)
                )),
                createTemplateExercise("俯身杠铃划船", "BACK", "杠铃", listOf(
                    WorkoutSet(1, "N", 50.0, 10),
                    WorkoutSet(2, "N", 55.0, 8),
                    WorkoutSet(3, "N", 55.0, 8)
                )),
                createTemplateExercise("绳索面拉", "BACK", "绳索", listOf(
                    WorkoutSet(1, "N", 20.0, 15),
                    WorkoutSet(2, "N", 20.0, 15),
                    WorkoutSet(3, "N", 22.5, 12)
                )),
                createTemplateExercise("哑铃交替锤式弯举", "ARMS", "哑铃", listOf(
                    WorkoutSet(1, "N", 12.0, 12),
                    WorkoutSet(2, "N", 12.0, 12),
                    WorkoutSet(3, "N", 14.0, 10)
                ))
            )
        ),
        WorkoutTemplate(
            id = "tpl_ppl_legs",
            title = "经典腿臀日 (Legs Day)",
            subtitle = "深蹲主导，股四头肌、腘绳肌、臀部与小腿全维生长",
            workoutType = "STRENGTH",
            targetDurationMinutes = 55,
            exercises = listOf(
                createTemplateExercise("杠铃深蹲", "LEGS", "杠铃", listOf(
                    WorkoutSet(1, "W", 40.0, 10),
                    WorkoutSet(2, "N", 70.0, 8),
                    WorkoutSet(3, "N", 80.0, 6),
                    WorkoutSet(4, "F", 85.0, 5)
                )),
                createTemplateExercise("罗马尼亚硬拉 (RDL)", "LEGS", "杠铃", listOf(
                    WorkoutSet(1, "N", 60.0, 10),
                    WorkoutSet(2, "N", 65.0, 8),
                    WorkoutSet(3, "N", 70.0, 8)
                )),
                createTemplateExercise("倒蹬机腿举", "LEGS", "器械", listOf(
                    WorkoutSet(1, "N", 120.0, 12),
                    WorkoutSet(2, "N", 140.0, 10),
                    WorkoutSet(3, "N", 150.0, 10)
                )),
                createTemplateExercise("坐姿腿屈伸", "LEGS", "器械", listOf(
                    WorkoutSet(1, "N", 35.0, 12),
                    WorkoutSet(2, "N", 40.0, 10),
                    WorkoutSet(3, "D", 30.0, 12)
                )),
                createTemplateExercise("站姿提踵", "LEGS", "器械", listOf(
                    WorkoutSet(1, "N", 50.0, 15),
                    WorkoutSet(2, "N", 55.0, 15),
                    WorkoutSet(3, "N", 55.0, 15)
                ))
            )
        ),
        WorkoutTemplate(
            id = "tpl_upper",
            title = "上肢塑形强化 (Upper Body)",
            subtitle = "卧推+划船+推肩拮抗组合，适合二分化高频训练者",
            workoutType = "STRENGTH",
            targetDurationMinutes = 45,
            exercises = listOf(
                createTemplateExercise("杠铃平板卧推", "CHEST", "杠铃", listOf(
                    WorkoutSet(1, "W", 40.0, 12),
                    WorkoutSet(2, "N", 60.0, 10),
                    WorkoutSet(3, "N", 65.0, 8)
                )),
                createTemplateExercise("坐姿绳索划船", "BACK", "绳索", listOf(
                    WorkoutSet(1, "N", 45.0, 12),
                    WorkoutSet(2, "N", 50.0, 10),
                    WorkoutSet(3, "N", 55.0, 8)
                )),
                createTemplateExercise("坐姿哑铃推肩", "SHOULDERS", "哑铃", listOf(
                    WorkoutSet(1, "N", 18.0, 10),
                    WorkoutSet(2, "N", 18.0, 10),
                    WorkoutSet(3, "N", 20.0, 8)
                )),
                createTemplateExercise("宽距引体向上", "BACK", "自重", listOf(
                    WorkoutSet(1, "N", 0.0, 8),
                    WorkoutSet(2, "N", 0.0, 8),
                    WorkoutSet(3, "N", 0.0, 7)
                )),
                createTemplateExercise("绳索三头下压", "ARMS", "绳索", listOf(
                    WorkoutSet(1, "N", 25.0, 12),
                    WorkoutSet(2, "N", 25.0, 12)
                ))
            )
        ),
        WorkoutTemplate(
            id = "tpl_cardio_hiit",
            title = "高效燃脂与心肺有氧 (Fat Burn & HIIT)",
            subtitle = "跑步机变速冲刺结合核心抗伸展，极速代谢燃脂",
            workoutType = "CARDIO",
            targetDurationMinutes = 40,
            exercises = listOf(
                createTemplateExercise("跑步机变速跑 (HIIT)", "CARDIO", "有氧", listOf(
                    WorkoutSet(1, "N", 0.0, 1)
                ), calories = 220.0),
                createTemplateExercise("悬垂举腿", "CORE", "自重", listOf(
                    WorkoutSet(1, "N", 0.0, 12),
                    WorkoutSet(2, "N", 0.0, 12),
                    WorkoutSet(3, "N", 0.0, 10)
                )),
                createTemplateExercise("健腹轮推拉", "CORE", "器械", listOf(
                    WorkoutSet(1, "N", 0.0, 10),
                    WorkoutSet(2, "N", 0.0, 10),
                    WorkoutSet(3, "N", 0.0, 10)
                )),
                createTemplateExercise("波比跳 (Burpees)", "CARDIO", "自重", listOf(
                    WorkoutSet(1, "N", 0.0, 15),
                    WorkoutSet(2, "N", 0.0, 15),
                    WorkoutSet(3, "N", 0.0, 15)
                ), calories = 90.0)
            )
        )
    )

    private fun createTemplateExercise(
        name: String,
        category: String,
        equipment: String,
        sets: List<WorkoutSet>,
        calories: Double = 70.0
    ): TemplateExercise {
        return TemplateExercise(
            name = name,
            category = category,
            equipment = equipment,
            sets = sets,
            calories = calories
        )
    }
}
