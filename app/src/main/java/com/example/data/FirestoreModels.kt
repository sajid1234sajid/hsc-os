package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val dailyHours: Long = 5L,
    val language: String = AppLanguage.BN.name,
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Long = 18L,
    val reminderMinute: Long = 0L,
    val seededDefaultChapters: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChapterDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectGroup: String = "",
    val paper: String = "",
    val startWeek: Long = 1L,
    val weekNumber: Long = 1L,
    val title: String = "",
    val deadline: String = "",
    val status: String = ChapterStatus.NOT_STARTED.name,
    val editablePlaceholder: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toUserChapter(): UserChapter {
        val safeStatus = ChapterStatus.entries.firstOrNull { it.name == status } ?: ChapterStatus.NOT_STARTED
        return UserChapter(
            id = id,
            subjectId = subjectId,
            subjectGroup = subjectGroup,
            paper = paper,
            startWeek = startWeek.toInt().coerceIn(1, StudyDateUtils.TOTAL_WEEKS),
            weekNumber = weekNumber.toInt().coerceIn(1, StudyDateUtils.TOTAL_WEEKS),
            title = title,
            deadline = deadline,
            status = safeStatus.name,
            isEditablePlaceholder = editablePlaceholder
        )
    }
}

@Keep
@IgnoreExtraProperties
data class WeekRecordDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val weekNumber: Long = 1L,
    val weeklyTasks: List<String> = emptyList(),
    val fridayChecklist: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DailyLogDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateIso: String = "",
    val completedSlots: List<Long> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MistakeDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectNameBn: String = "",
    val subjectNameEn: String = "",
    val chapterTitle: String = "",
    val question: String = "",
    val correctAnswer: String = "",
    val date: String = "",
    val revised: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMistakeEntry(): MistakeEntry = MistakeEntry(
        id = id,
        subjectId = subjectId,
        subjectNameBn = subjectNameBn,
        subjectNameEn = subjectNameEn,
        chapterTitle = chapterTitle,
        question = question,
        correctAnswer = correctAnswer,
        date = date,
        revised = revised
    )
}
