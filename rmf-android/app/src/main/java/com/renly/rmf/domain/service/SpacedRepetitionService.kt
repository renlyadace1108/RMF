package com.renly.rmf.domain.service

import com.renly.rmf.data.local.entity.StudyTopicEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ReviewRecommendation(
    val topicId: String,
    val title: String,
    val category: String,
    val stage: Int, // 1~5 复习周期阶梯
    val reviewDueDate: LocalDate,
    val isOverdue: Boolean,
    val urgencyScore: Double // 紧急度评分
)

class SpacedRepetitionService {

    // 艾宾浩斯经典复习曲线间隔天数 (1, 2, 4, 7, 15)
    private val reviewIntervals = listOf(1, 2, 4, 7, 15)

    /**
     * 根据创建/更新时间生成学习主题当前的复习任务推荐
     */
    fun getReviewRecommendations(
        topics: List<StudyTopicEntity>,
        today: LocalDate = LocalDate.now()
    ): List<ReviewRecommendation> {
        val recommendations = mutableListOf<ReviewRecommendation>()

        for (topic in topics) {
            if (topic.isDeleted == 1 || topic.status == "COMPLETED") continue

            val updatedDate = try {
                LocalDateTime.parse(topic.updatedAt, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
            } catch (e: Exception) {
                today
            }

            val elapsedDays = ChronoUnit.DAYS.between(updatedDate, today)

            // 计算当前处于哪个复习阶梯
            for ((index, interval) in reviewIntervals.withIndex()) {
                val dueDate = updatedDate.plusDays(interval.toLong())
                val isDue = !today.isBefore(dueDate)
                val daysDiff = ChronoUnit.DAYS.between(dueDate, today)

                if (isDue && daysDiff in 0..3) {
                    val urgency = (daysDiff + 1) * 1.5
                    recommendations.add(
                        ReviewRecommendation(
                            topicId = topic.id,
                            title = topic.title,
                            category = topic.category,
                            stage = index + 1,
                            reviewDueDate = dueDate,
                            isOverdue = daysDiff > 0,
                            urgencyScore = urgency
                        )
                    )
                    break
                }
            }
        }

        return recommendations.sortedByDescending { it.urgencyScore }
    }
}
