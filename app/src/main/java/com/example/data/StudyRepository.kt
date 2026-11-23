package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StudyRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val planAdapter = moshi.adapter(StudyPlanAsset::class.java)
    private val stateAdapter = moshi.adapter(AppPersistedState::class.java).indent("  ")

    private val stateFile: File get() = File(context.filesDir, "hsc_study_state.json")
    private val ioMutex = Mutex()

    private var cachedPlan: StudyPlanAsset? = null

    private val _state = MutableStateFlow<AppPersistedState?>(null)
    val state: StateFlow<AppPersistedState?> = _state.asStateFlow()

    fun getPlan(): StudyPlanAsset {
        cachedPlan?.let { return it }
        val json = context.assets.open("study_plan.json").bufferedReader().use { it.readText() }
        val parsed = planAdapter.fromJson(json)
            ?: throw IllegalStateException("Failed to parse study_plan.json")
        cachedPlan = parsed
        return parsed
    }

    suspend fun initialize(): AppPersistedState = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val plan = getPlan()
            val loaded = if (stateFile.exists()) {
                runCatching { stateAdapter.fromJson(stateFile.readText()) }.getOrNull()
            } else null

            val resolved = if (loaded != null && loaded.chapters.isNotEmpty()) {
                loaded
            } else {
                createDefaultState(plan).also { saveInternal(it) }
            }
            _state.value = resolved
            resolved
        }
    }

    private fun createDefaultState(plan: StudyPlanAsset): AppPersistedState {
        val chapters = plan.subjects.flatMap { subject ->
            subject.chapters.map { ch ->
                UserChapter(
                    id = ch.id,
                    subjectId = subject.id,
                    subjectGroup = subject.group,
                    paper = ch.paper,
                    startWeek = ch.startWeek,
                    weekNumber = ch.weekNumber,
                    title = ch.title,
                    deadline = ch.deadline,
                    status = ChapterStatus.NOT_STARTED.name,
                    isEditablePlaceholder = ch.isEditablePlaceholder
                )
            }
        }
        return AppPersistedState(
            version = 1,
            settings = UserSettings(),
            chapters = chapters
        )
    }

    private fun saveInternal(newState: AppPersistedState) {
        val tmp = File(context.filesDir, "hsc_study_state.json.tmp")
        tmp.writeText(stateAdapter.toJson(newState))
        if (!tmp.renameTo(stateFile)) {
            stateFile.writeText(stateAdapter.toJson(newState))
            tmp.delete()
        }
        _state.value = newState
    }

    private suspend fun updateState(transform: (AppPersistedState) -> AppPersistedState) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                val current = _state.value ?: createDefaultState(getPlan())
                val updated = transform(current)
                saveInternal(updated)
            }
        }
    }

    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus) {
        updateState { current ->
            current.copy(
                chapters = current.chapters.map { ch ->
                    if (ch.id == chapterId) ch.copy(status = status.name) else ch
                }
            )
        }
    }

    suspend fun editChapter(chapterId: String, newTitle: String, newDeadline: String, newWeekNumber: Int) {
        updateState { current ->
            current.copy(
                chapters = current.chapters.map { ch ->
                    if (ch.id == chapterId) {
                        ch.copy(
                            title = newTitle.trim().ifEmpty { ch.title },
                            deadline = newDeadline.trim().ifEmpty { ch.deadline },
                            weekNumber = newWeekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS),
                            isEditablePlaceholder = false
                        )
                    } else ch
                }
            )
        }
    }

    suspend fun addChapter(
        subjectId: String,
        paper: String,
        title: String,
        weekNumber: Int
    ) {
        val plan = getPlan()
        val subject = plan.subjects.firstOrNull { it.id == subjectId } ?: return
        val clampedWeek = weekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS)
        val newChapter = UserChapter(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            subjectId = subject.id,
            subjectGroup = subject.group,
            paper = paper.ifBlank { subject.nameBn },
            startWeek = clampedWeek,
            weekNumber = clampedWeek,
            title = title.trim(),
            deadline = StudyDateUtils.weekDeadlineIso(clampedWeek),
            status = ChapterStatus.NOT_STARTED.name,
            isEditablePlaceholder = false
        )
        updateState { current ->
            current.copy(chapters = current.chapters + newChapter)
        }
    }

    suspend fun deleteChapter(chapterId: String) {
        updateState { current ->
            current.copy(chapters = current.chapters.filterNot { it.id == chapterId })
        }
    }

    suspend fun toggleWeeklyMandatoryTask(weekNumber: Int, taskId: String) {
        val key = weekNumber.toString()
        updateState { current ->
            val existing = current.weeklyTasksCompleted[key].orEmpty().toMutableSet()
            if (!existing.add(taskId)) existing.remove(taskId)
            val updatedMap = current.weeklyTasksCompleted.toMutableMap().apply {
                put(key, existing.toList())
            }
            current.copy(weeklyTasksCompleted = updatedMap)
        }
    }

    suspend fun toggleFridayChecklistItem(weekNumber: Int, itemId: String) {
        val key = weekNumber.toString()
        updateState { current ->
            val existing = current.fridayChecklistCompleted[key].orEmpty().toMutableSet()
            if (!existing.add(itemId)) existing.remove(itemId)
            val updatedMap = current.fridayChecklistCompleted.toMutableMap().apply {
                put(key, existing.toList())
            }
            current.copy(fridayChecklistCompleted = updatedMap)
        }
    }

    suspend fun setDailySlotCompleted(dateIso: String, slot: Int, done: Boolean) {
        updateState { current ->
            val existing = current.dailyCompletedSlots[dateIso].orEmpty().toMutableSet()
            if (done) existing.add(slot) else existing.remove(slot)
            val updatedMap = current.dailyCompletedSlots.toMutableMap().apply {
                put(dateIso, existing.sorted())
            }
            current.copy(dailyCompletedSlots = updatedMap)
        }
    }

    suspend fun addMistake(
        subjectId: String,
        chapterTitle: String,
        question: String,
        correctAnswer: String,
        dateIso: String
    ) {
        val plan = getPlan()
        val subject = plan.subjects.firstOrNull { it.id == subjectId }
        val entry = MistakeEntry(
            id = UUID.randomUUID().toString().take(12),
            subjectId = subjectId,
            subjectNameBn = subject?.nameBn ?: "সাধারণ",
            subjectNameEn = subject?.nameEn ?: "General",
            chapterTitle = chapterTitle.trim(),
            question = question.trim(),
            correctAnswer = correctAnswer.trim(),
            date = dateIso.ifBlank { StudyDateUtils.todayIso() },
            revised = false
        )
        updateState { current ->
            current.copy(mistakes = listOf(entry) + current.mistakes)
        }
    }

    suspend fun toggleMistakeRevised(mistakeId: String) {
        updateState { current ->
            current.copy(
                mistakes = current.mistakes.map { item ->
                    if (item.id == mistakeId) item.copy(revised = !item.revised) else item
                }
            )
        }
    }

    suspend fun deleteMistake(mistakeId: String) {
        updateState { current ->
            current.copy(mistakes = current.mistakes.filterNot { it.id == mistakeId })
        }
    }

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        updateState { current ->
            current.copy(settings = transform(current.settings))
        }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val current = _state.value ?: initialize()
            stateAdapter.toJson(current)
        }
    }

    suspend fun importJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val parsed = stateAdapter.fromJson(jsonString)
                    ?: throw IllegalArgumentException("Invalid JSON format")
                require(parsed.chapters.isNotEmpty()) { "Backup contains no chapters" }
                saveInternal(parsed)
            }
        }
    }

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val fresh = createDefaultState(getPlan())
            saveInternal(fresh)
        }
    }
}
