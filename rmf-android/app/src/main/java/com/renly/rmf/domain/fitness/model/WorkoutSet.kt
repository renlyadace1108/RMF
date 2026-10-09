package com.renly.rmf.domain.fitness.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 专业的单组训练记录数据模型 (参考 Hevy / Strong / 训记)
 */
data class WorkoutSet(
    val setNumber: Int,
    val type: String = "N", // "W" = 热身组 (Warmup), "N" = 正式组 (Normal), "D" = 递减组 (Drop Set), "F" = 力竭组 (Failure)
    val weightKg: Double = 0.0,
    val reps: Int = 10,
    val isCompleted: Boolean = false,
    val rpe: Double? = null // 自觉竭力程度 (Rate of Perceived Exertion 6.0 ~ 10.0)
) {
    val typeLabel: String
        get() = when (type.uppercase()) {
            "W" -> "热身"
            "D" -> "递减"
            "F" -> "力竭"
            else -> "正式"
        }

    val typeBadge: String
        get() = when (type.uppercase()) {
            "W" -> "W"
            "D" -> "D"
            "F" -> "F"
            else -> "$setNumber"
        }

    /**
     * 单组估算 1RM (Epley 公式: Weight * (1 + Reps / 30))
     */
    val estimated1RM: Double
        get() = if (weightKg <= 0.0 || reps <= 0) 0.0
        else if (reps == 1) weightKg
        else weightKg * (1.0 + reps / 30.0)

    /**
     * 单组训练容量 (Weight * Reps)
     */
    val volumeKg: Double
        get() = if (isCompleted) weightKg * reps else 0.0

    companion object {
        fun toJsonArray(sets: List<WorkoutSet>): String {
            val array = JSONArray()
            sets.forEach { s ->
                val obj = JSONObject().apply {
                    put("setNumber", s.setNumber)
                    put("type", s.type)
                    put("weightKg", s.weightKg)
                    put("reps", s.reps)
                    put("isCompleted", s.isCompleted)
                    s.rpe?.let { put("rpe", it) }
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun fromJsonArray(jsonStr: String?, fallbackSetsCount: Int = 4, fallbackReps: Int = 10, fallbackWeight: Double = 0.0): List<WorkoutSet> {
            if (jsonStr.isNullOrBlank() || jsonStr.trim() == "[]") {
                // 回退合成标准组数
                val count = if (fallbackSetsCount <= 0) 4 else fallbackSetsCount
                return (1..count).map { idx ->
                    WorkoutSet(
                        setNumber = idx,
                        type = if (idx == 1) "W" else "N",
                        weightKg = if (idx == 1 && fallbackWeight > 20) (fallbackWeight * 0.6).toInt().toDouble() else fallbackWeight,
                        reps = fallbackReps,
                        isCompleted = false
                    )
                }
            }

            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<WorkoutSet>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        WorkoutSet(
                            setNumber = obj.optInt("setNumber", i + 1),
                            type = obj.optString("type", "N"),
                            weightKg = obj.optDouble("weightKg", 0.0),
                            reps = obj.optInt("reps", 10),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            rpe = if (obj.has("rpe") && !obj.isNull("rpe")) obj.optDouble("rpe") else null
                        )
                    )
                }
                if (list.isEmpty()) {
                    fromJsonArray(null, fallbackSetsCount, fallbackReps, fallbackWeight)
                } else {
                    list
                }
            } catch (_: Exception) {
                fromJsonArray(null, fallbackSetsCount, fallbackReps, fallbackWeight)
            }
        }
    }
}
