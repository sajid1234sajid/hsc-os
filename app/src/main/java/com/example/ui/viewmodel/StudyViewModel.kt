package com.example.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppLanguage
import com.example.data.AppPersistedState
import com.example.data.ChapterStatus
import com.example.data.ChapterWithRollover
import com.example.data.MistakeEntry
import com.example.data.SessionTimerState
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.StudyPlanAsset
import com.example.data.StudyRepository
import com.example.data.SubjectAsset
import com.example.data.TimerPhase
import com.example.data.UserChapter
import com.example.data.UserSettings
import com.example.notifications.StudyNotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SubjectProgressSummary(
    val subjectId: String,
    val group: String,
    val nameBn: String,
    val nameEn: String,
    val totalChapters: Int,
    val completedChapters: Int,
    val overdueChapters: Int,
    val percent: Int
)

data class StudyUiState(
    val isLoading: Boolean = true,
    val todayIso: String = StudyDateUtils.todayIso(),
    val currentWeek: Int = StudyDateUtils.currentWeekNumber(),
    val isBeforePlanStart: Boolean = StudyDateUtils.isBeforePlanStart(),
    val daysRemaining: Int = StudyDateUtils.daysUntilExamEnd(),
    val settings: UserSettings = UserSettings(),
    val plan: StudyPlanAsset? = null,
    val chaptersWithRollover: List<ChapterWithRollover> = emptyList(),
    val todaySessions: List<SessionTimerState> = emptyList(),
    val breakRemainingSeconds: Int = 0,
    val breakRunning: Boolean = false,
    val selectedWeeklyViewWeek: Int = StudyDateUtils.currentWeekNumber(),
    val weeklyTasksCompleted: Map<String, List<String>> = emptyMap(),
    val fridayChecklistCompleted: Map<String, List<String>> = emptyMap(),
    val currentWeekCompletionPercent: Int = 0,
    val selectedWeekCompletionPercent: Int = 0,
    val mistakes: List<MistakeEntry> = emptyList(),
    val subjectSummaries: List<SubjectProgressSummary> = emptyList(),
    val overallCompletionPercent: Int = 0,
    val weeklyStreakCount: Int = 0,
    val dailyStreakCount: Int = 0,
    val rolloverCount: Int = 0
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(application)

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // In-memory runtime timer state keyed by slot (1..4)
    private data class RuntimeTimer(
        var totalSeconds: Int,
        var remainingSeconds: Int,
        var isRunning: Boolean = false,
        var lastPhase: TimerPhase = TimerPhase.NEW_TOPIC
    )

    private val timerMap = mutableMapOf<Int, RuntimeTimer>()
    private var lastDailyHours: Int = 5
    private var lastTodayIso: String = StudyDateUtils.todayIso()
    private var tickerJob: Job? = null
    private var lastTickElapsedMs: Long = 0L

    init {
        StudyNotificationHelper.ensureChannels(application)
        viewModelScope.launch {
            repository.initialize()
            repository.state.collect { persisted ->
                if (persisted != null) {
                    rebuildUiState(persisted)
                }
            }
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        lastTickElapsedMs = SystemClock.elapsedRealtime()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val nowMs = SystemClock.elapsedRealtime()
                val deltaSec = ((nowMs - lastTickElapsedMs) / 1000L).toInt().coerceIn(1, 5)
                lastTickElapsedMs = nowMs

                var changed = false
                val currentSettings = _uiState.value.settings
                val lang = currentSettings.appLanguage()

                timerMap.forEach { (slot, timer) ->
                    if (timer.isRunning && timer.remainingSeconds > 0) {
                        val beforePhase = currentPhaseFor(timer, currentSettings.dailyHours)
                        timer.remainingSeconds = (timer.remainingSeconds - deltaSec).coerceAtLeast(0)
                        val afterPhase = currentPhaseFor(timer, currentSettings.dailyHours)
                        changed = true

                        if (timer.remainingSeconds == 0) {
                            timer.isRunning = false
                            if (currentSettings.notificationsEnabled) {
                                StudyNotificationHelper.showSessionNotification(
                                    getApplication(),
                                    StringsProvider.text(lang, "সেশন $slot সম্পন্ন!", "Session $slot complete!"),
                                    StringsProvider.text(
                                        lang,
                                        "দারুণ! ১০–১৫ মিনিট বিরতি নিয়ে পরের সেশন শুরু করুন।",
                                        "Great job! Take a 10-15 min break before the next session."
                                    ),
                                    3000 + slot
                                )
                            }
                            viewModelScope.launch {
                                repository.setDailySlotCompleted(_uiState.value.todayIso, slot, true)
                            }
                        } else if (beforePhase != afterPhase) {
                            timer.lastPhase = afterPhase
                            if (currentSettings.notificationsEnabled) {
                                StudyNotificationHelper.showSessionNotification(
                                    getApplication(),
                                    StringsProvider.text(lang, "নতুন ধাপ শুরু", "Next phase started"),
                                    "${StringsProvider.text(lang, "সেশন", "Session")} $slot: ${
                                        if (lang == AppLanguage.BN) afterPhase.labelBn else afterPhase.labelEn
                                    }",
                                    3100 + slot
                                )
                            }
                        }
                    }
                }

                if (_uiState.value.breakRunning && _uiState.value.breakRemainingSeconds > 0) {
                    val nextBreak = (_uiState.value.breakRemainingSeconds - deltaSec).coerceAtLeast(0)
                    val stillRunning = nextBreak > 0
                    if (!stillRunning && currentSettings.notificationsEnabled) {
                        StudyNotificationHelper.showSessionNotification(
                            getApplication(),
                            StringsProvider.text(lang, "বিরতি শেষ!", "Break finished!"),
                            StringsProvider.text(lang, "পরবর্তী সেশন শুরু করার সময় হয়েছে।", "Time to start your next session."),
                            3200
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        breakRemainingSeconds = nextBreak,
                        breakRunning = stillRunning
                    )
                }

                if (changed) {
                    val latestPersisted = repository.state.value
                    if (latestPersisted != null) {
                        rebuildUiState(latestPersisted)
                    }
                }
            }
        }
    }

    private fun currentPhaseFor(timer: RuntimeTimer, dailyHours: Int): TimerPhase {
        val elapsed = (timer.totalSeconds - timer.remainingSeconds).coerceAtLeast(0)
        val (newMin, practiceMin, _) = StudyDateUtils.phaseDurationsMinutes(dailyHours)
        return when {
            elapsed < newMin * 60 -> TimerPhase.NEW_TOPIC
            elapsed < (newMin + practiceMin) * 60 -> TimerPhase.PRACTICE
            else -> TimerPhase.RECALL
        }
    }

    private fun rebuildUiState(persisted: AppPersistedState) {
        val plan = repository.getPlan()
        val todayIso = StudyDateUtils.todayIso()
        val currentWeek = StudyDateUtils.currentWeekNumber(todayIso)
        val subjectLookup = plan.subjects.associateBy { it.id }

        if (lastTodayIso != todayIso) {
            timerMap.clear()
            lastTodayIso = todayIso
        }
        val sessionTotalSec = StudyDateUtils.sessionTotalMinutes(persisted.settings.dailyHours) * 60
        if (lastDailyHours != persisted.settings.dailyHours) {
            lastDailyHours = persisted.settings.dailyHours
            timerMap.values.forEach { timer ->
                if (!timer.isRunning && timer.remainingSeconds == timer.totalSeconds) {
                    timer.totalSeconds = sessionTotalSec
                    timer.remainingSeconds = sessionTotalSec
                } else {
                    timer.totalSeconds = sessionTotalSec
                    timer.remainingSeconds = timer.remainingSeconds.coerceAtMost(sessionTotalSec)
                }
            }
        }

        val chaptersWithRollover = persisted.chapters.map { ch ->
            val subj = subjectLookup[ch.subjectId]
            val daysPastDeadline = StudyDateUtils.daysBetween(ch.deadline, todayIso)
            val isOverdue = !ch.chapterStatus().isFinishedForRollover() && daysPastDeadline > 0
            val effectiveWeek = if (isOverdue && currentWeek > ch.weekNumber) currentWeek else ch.weekNumber
            val isRolledOver = isOverdue && currentWeek > ch.weekNumber
            val isCurrentWeek = currentWeek in ch.startWeek..effectiveWeek
            ChapterWithRollover(
                chapter = ch,
                subjectNameBn = subj?.nameBn ?: ch.subjectId,
                subjectNameEn = subj?.nameEn ?: ch.subjectId,
                isOverdue = isOverdue,
                isCurrentWeek = isCurrentWeek,
                isRolledOver = isRolledOver,
                effectiveWeek = effectiveWeek
            )
        }

        val dayKey = StudyDateUtils.dayKeyForRoutine(todayIso)
        val routineSlots = plan.weeklyRoutine[dayKey].orEmpty()
        val completedTodaySlots = persisted.dailyCompletedSlots[todayIso].orEmpty().toSet()

        val sessions = routineSlots.map { slotAsset ->
            val timer = timerMap.getOrPut(slotAsset.slot) {
                RuntimeTimer(totalSeconds = sessionTotalSec, remainingSeconds = sessionTotalSec)
            }
            val matchingChapters = chaptersForRoutineGroup(
                group = slotAsset.group,
                week = currentWeek,
                chapters = chaptersWithRollover
            )
            // Prioritize rolled-over or unfinished chapters first
            val activeChapter = matchingChapters.firstOrNull { !it.chapter.chapterStatus().isFinishedForRollover() }
                ?: matchingChapters.firstOrNull()

            SessionTimerState(
                slot = slotAsset.slot,
                group = slotAsset.group,
                subjectLabelBn = slotAsset.labelBn,
                subjectLabelEn = slotAsset.labelEn,
                totalSeconds = timer.totalSeconds,
                remainingSeconds = timer.remainingSeconds,
                isRunning = timer.isRunning,
                isDoneToday = completedTodaySlots.contains(slotAsset.slot),
                activeChapter = activeChapter,
                todayChaptersCount = matchingChapters.size
            )
        }

        val selectedWeek = _uiState.value.selectedWeeklyViewWeek.coerceIn(1, StudyDateUtils.TOTAL_WEEKS)
        val currentWeekPct = computeWeekCompletionPercent(currentWeek, plan, persisted, chaptersWithRollover)
        val selectedWeekPct = computeWeekCompletionPercent(selectedWeek, plan, persisted, chaptersWithRollover)

        val subjectSummaries = plan.subjects.map { subj ->
            val subjChapters = chaptersWithRollover.filter { it.chapter.subjectId == subj.id }
            val completed = subjChapters.count { it.chapter.chapterStatus().isFinishedForRollover() }
            val overdue = subjChapters.count { it.isOverdue }
            val pct = if (subjChapters.isEmpty()) 0 else ((completed * 100f) / subjChapters.size).toInt()
            SubjectProgressSummary(
                subjectId = subj.id,
                group = subj.group,
                nameBn = subj.nameBn,
                nameEn = subj.nameEn,
                totalChapters = subjChapters.size,
                completedChapters = completed,
                overdueChapters = overdue,
                percent = pct
            )
        }

        val totalCh = chaptersWithRollover.size
        val completedCh = chaptersWithRollover.count { it.chapter.chapterStatus().isFinishedForRollover() }
        val overallPct = if (totalCh == 0) 0 else ((completedCh * 100f) / totalCh).toInt()

        val weeklyStreak = computeWeeklyStreak(currentWeek, plan, persisted, chaptersWithRollover)
        val dailyStreak = computeDailyStreak(todayIso, persisted)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            todayIso = todayIso,
            currentWeek = currentWeek,
            isBeforePlanStart = StudyDateUtils.isBeforePlanStart(todayIso),
            daysRemaining = StudyDateUtils.daysUntilExamEnd(todayIso),
            settings = persisted.settings,
            plan = plan,
            chaptersWithRollover = chaptersWithRollover,
            todaySessions = sessions,
            selectedWeeklyViewWeek = selectedWeek,
            weeklyTasksCompleted = persisted.weeklyTasksCompleted,
            fridayChecklistCompleted = persisted.fridayChecklistCompleted,
            currentWeekCompletionPercent = currentWeekPct,
            selectedWeekCompletionPercent = selectedWeekPct,
            mistakes = persisted.mistakes,
            subjectSummaries = subjectSummaries,
            overallCompletionPercent = overallPct,
            weeklyStreakCount = weeklyStreak,
            dailyStreakCount = dailyStreak,
            rolloverCount = chaptersWithRollover.count { it.isRolledOver }
        )
    }

    private fun chaptersForRoutineGroup(
        group: String,
        week: Int,
        chapters: List<ChapterWithRollover>
    ): List<ChapterWithRollover> {
        val targetGroups = when (group) {
            "chemistry_biology" -> setOf("chemistry", "biology")
            "bangla_english_ict" -> setOf("bangla", "english", "ict")
            else -> setOf(group)
        }
        return chapters
            .filter { ch ->
                ch.chapter.subjectGroup in targetGroups && (week in ch.chapter.startWeek..ch.effectiveWeek)
            }
            .sortedWith(
                compareByDescending<ChapterWithRollover> { it.isRolledOver }
                    .thenBy { it.chapter.chapterStatus().isFinishedForRollover() }
                    .thenBy { it.chapter.weekNumber }
            )
    }

    private fun computeWeekCompletionPercent(
        week: Int,
        plan: StudyPlanAsset,
        persisted: AppPersistedState,
        chapters: List<ChapterWithRollover>
    ): Int {
        val weekTasksTotal = plan.weeklyMandatoryTasks.size + plan.fridayExamChecklist.size
        val weekTasksDone = persisted.weeklyTasksCompleted[week.toString()].orEmpty().size +
            persisted.fridayChecklistCompleted[week.toString()].orEmpty().size

        val weekChapters = chapters.filter { week in it.chapter.startWeek..it.effectiveWeek }
        val weekChaptersDone = weekChapters.count { it.chapter.chapterStatus().isFinishedForRollover() }

        val totalItems = weekTasksTotal + weekChapters.size
        if (totalItems == 0) return 0
        val doneItems = weekTasksDone + weekChaptersDone
        return ((doneItems * 100f) / totalItems).toInt().coerceIn(0, 100)
    }

    private fun computeWeeklyStreak(
        currentWeek: Int,
        plan: StudyPlanAsset,
        persisted: AppPersistedState,
        chapters: List<ChapterWithRollover>
    ): Int {
        var streak = 0
        for (w in currentWeek downTo 1) {
            val pct = computeWeekCompletionPercent(w, plan, persisted, chapters)
            if (pct >= 80) {
                streak++
            } else if (w < currentWeek) {
                break
            }
        }
        return streak
    }

    private fun computeDailyStreak(todayIso: String, persisted: AppPersistedState): Int {
        var streak = 0
        var cursor = todayIso
        if (persisted.dailyCompletedSlots[cursor].orEmpty().isEmpty()) {
            cursor = StudyDateUtils.previousDayIso(cursor)
        }
        while (persisted.dailyCompletedSlots[cursor].orEmpty().isNotEmpty()) {
            streak++
            cursor = StudyDateUtils.previousDayIso(cursor)
            if (streak >= 365) break
        }
        return streak
    }

    fun toggleSessionTimer(slot: Int) {
        val timer = timerMap[slot] ?: return
        val willRun = !timer.isRunning
        if (willRun) {
            // Pause any other active session so focus remains on one subject at a time
            timerMap.forEach { (k, v) ->
                if (k != slot) v.isRunning = false
            }
            if (timer.remainingSeconds == 0) {
                timer.remainingSeconds = timer.totalSeconds
            }
        }
        timer.isRunning = willRun
        lastTickElapsedMs = SystemClock.elapsedRealtime()
        repository.state.value?.let { rebuildUiState(it) }
    }

    fun resetSessionTimer(slot: Int) {
        val timer = timerMap[slot] ?: return
        timer.isRunning = false
        timer.remainingSeconds = timer.totalSeconds
        repository.state.value?.let { rebuildUiState(it) }
    }

    fun startBreakTimer(minutes: Int = 10) {
        _uiState.value = _uiState.value.copy(
            breakRemainingSeconds = minutes * 60,
            breakRunning = true
        )
    }

    fun stopBreakTimer() {
        _uiState.value = _uiState.value.copy(
            breakRemainingSeconds = 0,
            breakRunning = false
        )
    }

    fun toggleSlotDone(slot: Int, done: Boolean) {
        viewModelScope.launch {
            repository.setDailySlotCompleted(_uiState.value.todayIso, slot, done)
        }
    }

    fun updateChapterStatus(chapterId: String, status: ChapterStatus) {
        viewModelScope.launch {
            repository.updateChapterStatus(chapterId, status)
        }
    }

    fun editChapter(chapterId: String, newTitle: String, newDeadline: String, newWeekNumber: Int) {
        viewModelScope.launch {
            repository.editChapter(chapterId, newTitle, newDeadline, newWeekNumber)
            val lang = _uiState.value.settings.appLanguage()
            _events.emit(StringsProvider.text(lang, "অধ্যায় আপডেট হয়েছে", "Chapter updated"))
        }
    }

    fun addChapter(subjectId: String, paper: String, title: String, weekNumber: Int) {
        viewModelScope.launch {
            repository.addChapter(subjectId, paper, title, weekNumber)
            val lang = _uiState.value.settings.appLanguage()
            _events.emit(StringsProvider.text(lang, "নতুন অধ্যায় যোগ হয়েছে", "New chapter added"))
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(chapterId)
        }
    }

    fun selectWeeklyViewWeek(week: Int) {
        _uiState.value = _uiState.value.copy(selectedWeeklyViewWeek = week.coerceIn(1, StudyDateUtils.TOTAL_WEEKS))
        repository.state.value?.let { rebuildUiState(it) }
    }

    fun toggleWeeklyTask(weekNumber: Int, taskId: String) {
        viewModelScope.launch {
            repository.toggleWeeklyMandatoryTask(weekNumber, taskId)
        }
    }

    fun toggleFridayChecklistItem(weekNumber: Int, itemId: String) {
        viewModelScope.launch {
            repository.toggleFridayChecklistItem(weekNumber, itemId)
        }
    }

    fun addMistake(
        subjectId: String,
        chapterTitle: String,
        question: String,
        correctAnswer: String,
        dateIso: String
    ) {
        viewModelScope.launch {
            repository.addMistake(subjectId, chapterTitle, question, correctAnswer, dateIso)
            val lang = _uiState.value.settings.appLanguage()
            _events.emit(StringsProvider.text(lang, "ভুলের খাতায় সংরক্ষিত হয়েছে", "Saved to Mistake Notebook"))
        }
    }

    fun toggleMistakeRevised(mistakeId: String) {
        viewModelScope.launch {
            repository.toggleMistakeRevised(mistakeId)
        }
    }

    fun deleteMistake(mistakeId: String) {
        viewModelScope.launch {
            repository.deleteMistake(mistakeId)
        }
    }

    fun setDailyHours(hours: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dailyHours = if (hours <= 3) 3 else 5) }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(language = lang.name) }
        }
    }

    fun toggleDarkMode(dark: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(darkMode = dark) }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean, hour: Int = _uiState.value.settings.reminderHour, minute: Int = _uiState.value.settings.reminderMinute) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(notificationsEnabled = enabled, reminderHour = hour, reminderMinute = minute)
            }
            StudyNotificationHelper.scheduleDailyReminder(getApplication(), hour, minute, enabled)
        }
    }

    fun sendTestNotification() {
        val lang = _uiState.value.settings.appLanguage()
        StudyNotificationHelper.showDailyReminderNotification(getApplication(), lang)
    }

    suspend fun exportBackupJson(): String = repository.exportJson()

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            val lang = _uiState.value.settings.appLanguage()
            val result = repository.importJson(json)
            if (result.isSuccess) {
                _events.emit(StringsProvider.text(lang, "ব্যাকআপ সফলভাবে ইমপোর্ট হয়েছে", "Backup restored successfully"))
            } else {
                _events.emit(StringsProvider.text(lang, "ফাইলটি সঠিক JSON নয়", "Invalid JSON backup file"))
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            timerMap.clear()
            repository.resetAll()
            val lang = _uiState.value.settings.appLanguage()
            _events.emit(StringsProvider.text(lang, "সব ডেটা রিসেট করা হয়েছে", "All progress reset to default"))
        }
    }
}
