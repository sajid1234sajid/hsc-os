package com.example.data

import com.squareup.moshi.JsonClass

enum class AppLanguage {
    BN,
    EN
}

enum class ChapterStatus(val order: Int, val labelBn: String, val labelEn: String) {
    NOT_STARTED(0, "শুরু হয়নি", "Not started"),
    IN_PROGRESS(1, "চলছে", "In progress"),
    FIRST_READING_DONE(2, "প্রথমবার পড়া শেষ", "First reading done"),
    PRACTICE_DONE(3, "অনুশীলন শেষ", "Practice done"),
    REVISION_DONE(4, "রিভিশন শেষ", "Revision done"),
    EXAM_TAKEN(5, "পরীক্ষা দেওয়া হয়েছে", "Exam taken");

    fun isFinishedForRollover(): Boolean =
        this == PRACTICE_DONE || this == REVISION_DONE || this == EXAM_TAKEN

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.BN) labelBn else labelEn
}

enum class TimerPhase(val labelBn: String, val labelEn: String) {
    NEW_TOPIC("নতুন টপিক পড়া", "New topic reading"),
    PRACTICE("অঙ্ক / MCQ / সৃজনশীল অনুশীলন", "Practice (Math / MCQ / CQ)"),
    RECALL("বই বন্ধ করে মনে করা", "Closed-book recall")
}

@JsonClass(generateAdapter = true)
data class PlanMeta(
    val titleBn: String,
    val titleEn: String,
    val startDate: String,
    val endDate: String,
    val totalWeeks: Int,
    val defaultDailyHours: Int,
    val sessionsPerDay: Int,
    val sessionMinutes: Int,
    val weeklyGoalMinPercent: Int,
    val weeklyGoalMaxPercent: Int
)

@JsonClass(generateAdapter = true)
data class TaskItemAsset(
    val id: String,
    val bn: String,
    val en: String
) {
    fun label(lang: AppLanguage): String = if (lang == AppLanguage.BN) bn else en
}

@JsonClass(generateAdapter = true)
data class RoutineSlotAsset(
    val slot: Int,
    val group: String,
    val labelBn: String,
    val labelEn: String
) {
    fun label(lang: AppLanguage): String = if (lang == AppLanguage.BN) labelBn else labelEn
}

@JsonClass(generateAdapter = true)
data class ChapterAsset(
    val id: String,
    val paper: String,
    val startWeek: Int = 1,
    val weekNumber: Int,
    val title: String,
    val deadline: String,
    val isEditablePlaceholder: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SubjectAsset(
    val id: String,
    val group: String,
    val nameBn: String,
    val nameEn: String,
    val chapterCountLabel: String,
    val noteBn: String = "",
    val chapters: List<ChapterAsset>
)

@JsonClass(generateAdapter = true)
data class StudyPlanAsset(
    val plan: PlanMeta,
    val weeklyMandatoryTasks: List<TaskItemAsset>,
    val fridayExamChecklist: List<TaskItemAsset>,
    val weeklyRoutine: Map<String, List<RoutineSlotAsset>>,
    val subjects: List<SubjectAsset>
)

@JsonClass(generateAdapter = true)
data class UserChapter(
    val id: String,
    val subjectId: String,
    val subjectGroup: String,
    val paper: String,
    val startWeek: Int,
    val weekNumber: Int,
    val title: String,
    val deadline: String,
    val status: String = ChapterStatus.NOT_STARTED.name,
    val isEditablePlaceholder: Boolean = false
) {
    fun chapterStatus(): ChapterStatus =
        runCatching { ChapterStatus.valueOf(status) }.getOrDefault(ChapterStatus.NOT_STARTED)
}

@JsonClass(generateAdapter = true)
data class MistakeEntry(
    val id: String,
    val subjectId: String,
    val subjectNameBn: String,
    val subjectNameEn: String,
    val chapterTitle: String,
    val question: String,
    val correctAnswer: String,
    val date: String,
    val revised: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UserSettings(
    val dailyHours: Int = 5,
    val language: String = AppLanguage.BN.name,
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0
) {
    fun appLanguage(): AppLanguage =
        runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.BN)
}

@JsonClass(generateAdapter = true)
data class AppPersistedState(
    val version: Int = 1,
    val settings: UserSettings = UserSettings(),
    val chapters: List<UserChapter> = emptyList(),
    val weeklyTasksCompleted: Map<String, List<String>> = emptyMap(),
    val fridayChecklistCompleted: Map<String, List<String>> = emptyMap(),
    val dailyCompletedSlots: Map<String, List<Int>> = emptyMap(),
    val mistakes: List<MistakeEntry> = emptyList()
)

data class ChapterWithRollover(
    val chapter: UserChapter,
    val subjectNameBn: String,
    val subjectNameEn: String,
    val isOverdue: Boolean,
    val isCurrentWeek: Boolean,
    val isRolledOver: Boolean,
    val effectiveWeek: Int
)

data class SessionTimerState(
    val slot: Int,
    val group: String,
    val subjectLabelBn: String,
    val subjectLabelEn: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isRunning: Boolean,
    val isDoneToday: Boolean,
    val activeChapter: ChapterWithRollover?,
    val todayChaptersCount: Int
) {
    val elapsedSeconds: Int get() = (totalSeconds - remainingSeconds).coerceAtLeast(0)

    fun currentPhase(dailyHours: Int): TimerPhase {
        val (newTopicMin, practiceMin, _) = StudyDateUtils.phaseDurationsMinutes(dailyHours)
        val newTopicSec = newTopicMin * 60
        val practiceEndSec = (newTopicMin + practiceMin) * 60
        return when {
            elapsedSeconds < newTopicSec -> TimerPhase.NEW_TOPIC
            elapsedSeconds < practiceEndSec -> TimerPhase.PRACTICE
            else -> TimerPhase.RECALL
        }
    }

    fun phaseProgressFraction(dailyHours: Int): Float {
        if (totalSeconds <= 0) return 0f
        return (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }
}
