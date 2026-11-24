package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StudyRepository(private val context: Context) : StudyDataSource {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val planAdapter = moshi.adapter(StudyPlanAsset::class.java)
    private val stateAdapter = moshi.adapter(AppPersistedState::class.java).indent("  ")

    private val stateFile: File get() = File(context.applicationContext.filesDir, "hsc_study_state.json")
    private val ioMutex = Mutex()

    private var cachedPlan: StudyPlanAsset? = null
    private val _state = MutableStateFlow<AppPersistedState?>(null)

    override fun getPlan(): StudyPlanAsset {
        cachedPlan?.let { return it }
        val json = context.applicationContext.assets.open("study_plan.json").bufferedReader().use { it.readText() }
        val parsed = planAdapter.fromJson(json)
            ?: throw IllegalStateException("Failed to parse study_plan.json")
        cachedPlan = parsed
        return parsed
    }

    override suspend fun initialize(userId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
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
            }
            Unit
        }
    }

    override fun observeStudyState(userId: String?): Flow<StudyDataSnapshot> =
        _state.filterNotNull().map { persisted ->
            StudyDataSnapshot(
                persistedState = persisted,
                isCloudBacked = false,
                hasPendingWrites = false,
                isFromCache = true
            )
        }

    suspend fun saveSnapshot(state: AppPersistedState) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            saveInternal(state)
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
        val tmp = File(context.applicationContext.filesDir, "hsc_study_state.json.tmp")
        val json = stateAdapter.toJson(newState)
        tmp.writeText(json)
        if (!tmp.renameTo(stateFile)) {
            stateFile.writeText(json)
            tmp.delete()
        }
        _state.value = newState
    }

    private suspend fun updateState(transform: (AppPersistedState) -> AppPersistedState): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                ioMutex.withLock {
                    val current = _state.value ?: createDefaultState(getPlan())
                    val updated = transform(current)
                    saveInternal(updated)
                }
                Unit
            }
        }

    override suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus): Result<Unit> =
        updateState { current ->
            current.copy(
                chapters = current.chapters.map { ch ->
                    if (ch.id == chapterId) ch.copy(status = status.name) else ch
                }
            )
        }

    override suspend fun editChapter(
        chapterId: String,
        newTitle: String,
        newDeadline: String,
        newWeekNumber: Int
    ): Result<Unit> = updateState { current ->
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

    override suspend fun addChapter(
        subjectId: String,
        paper: String,
        title: String,
        weekNumber: Int,
        currentChapterCount: Int
    ): Result<Unit> {
        val plan = getPlan()
        val subject = plan.subjects.firstOrNull { it.id == subjectId }
            ?: return Result.failure(IllegalArgumentException("Unknown subject: $subjectId"))
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
        return updateState { current ->
            current.copy(chapters = current.chapters + newChapter)
        }
    }

    override suspend fun deleteChapter(chapterId: String): Result<Unit> =
        updateState { current ->
            current.copy(chapters = current.chapters.filterNot { it.id == chapterId })
        }

    override suspend fun toggleWeeklyMandatoryTask(
        weekNumber: Int,
        taskId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit> {
        val key = weekNumber.toString()
        return updateState { current ->
            val existing = current.weeklyTasksCompleted[key].orEmpty().toMutableSet()
            if (!existing.add(taskId)) existing.remove(taskId)
            val updatedMap = current.weeklyTasksCompleted.toMutableMap().apply {
                put(key, existing.toList())
            }
            current.copy(weeklyTasksCompleted = updatedMap)
        }
    }

    override suspend fun toggleFridayChecklistItem(
        weekNumber: Int,
        itemId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit> {
        val key = weekNumber.toString()
        return updateState { current ->
            val existing = current.fridayChecklistCompleted[key].orEmpty().toMutableSet()
            if (!existing.add(itemId)) existing.remove(itemId)
            val updatedMap = current.fridayChecklistCompleted.toMutableMap().apply {
                put(key, existing.toList())
            }
            current.copy(fridayChecklistCompleted = updatedMap)
        }
    }

    override suspend fun setDailySlotCompleted(
        dateIso: String,
        slot: Int,
        done: Boolean,
        currentSlots: List<Int>
    ): Result<Unit> = updateState { current ->
        val existing = current.dailyCompletedSlots[dateIso].orEmpty().toMutableSet()
        if (done) existing.add(slot) else existing.remove(slot)
        val updatedMap = current.dailyCompletedSlots.toMutableMap().apply {
            put(dateIso, existing.sorted())
        }
        current.copy(dailyCompletedSlots = updatedMap)
    }

    override suspend fun addMistake(
        subjectId: String,
        chapterTitle: String,
        question: String,
        correctAnswer: String,
        dateIso: String
    ): Result<Unit> {
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
        return updateState { current ->
            current.copy(mistakes = listOf(entry) + current.mistakes)
        }
    }

    override suspend fun toggleMistakeRevised(mistakeId: String, currentlyRevised: Boolean): Result<Unit> =
        updateState { current ->
            current.copy(
                mistakes = current.mistakes.map { item ->
                    if (item.id == mistakeId) item.copy(revised = !item.revised) else item
                }
            )
        }

    override suspend fun deleteMistake(mistakeId: String): Result<Unit> =
        updateState { current ->
            current.copy(mistakes = current.mistakes.filterNot { it.id == mistakeId })
        }

    override suspend fun updateSettings(updated: UserSettings): Result<Unit> =
        updateState { current ->
            current.copy(settings = updated)
        }

    override fun exportJson(currentState: AppPersistedState): String =
        stateAdapter.toJson(currentState)

    override suspend fun importJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val parsed = stateAdapter.fromJson(jsonString)
                    ?: throw IllegalArgumentException("Invalid JSON format")
                require(parsed.chapters.isNotEmpty()) { "Backup contains no chapters" }
                saveInternal(parsed)
            }
        }
    }

    override suspend fun resetAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ioMutex.withLock {
                val fresh = createDefaultState(getPlan())
                saveInternal(fresh)
            }
            Unit
        }
    }
}
