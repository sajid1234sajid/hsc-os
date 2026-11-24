package com.example.data

import kotlinx.coroutines.flow.Flow

data class StudyDataSnapshot(
    val persistedState: AppPersistedState,
    val isCloudBacked: Boolean,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = true
)

interface StudyDataSource {
    fun getPlan(): StudyPlanAsset
    suspend fun initialize(userId: String?): Result<Unit>
    fun observeStudyState(userId: String?): Flow<StudyDataSnapshot>
    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus): Result<Unit>
    suspend fun editChapter(
        chapterId: String,
        newTitle: String,
        newDeadline: String,
        newWeekNumber: Int
    ): Result<Unit>
    suspend fun addChapter(
        subjectId: String,
        paper: String,
        title: String,
        weekNumber: Int,
        currentChapterCount: Int
    ): Result<Unit>
    suspend fun deleteChapter(chapterId: String): Result<Unit>
    suspend fun toggleWeeklyMandatoryTask(
        weekNumber: Int,
        taskId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit>
    suspend fun toggleFridayChecklistItem(
        weekNumber: Int,
        itemId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit>
    suspend fun setDailySlotCompleted(
        dateIso: String,
        slot: Int,
        done: Boolean,
        currentSlots: List<Int>
    ): Result<Unit>
    suspend fun addMistake(
        subjectId: String,
        chapterTitle: String,
        question: String,
        correctAnswer: String,
        dateIso: String
    ): Result<Unit>
    suspend fun toggleMistakeRevised(mistakeId: String, currentlyRevised: Boolean): Result<Unit>
    suspend fun deleteMistake(mistakeId: String): Result<Unit>
    suspend fun updateSettings(updated: UserSettings): Result<Unit>
    fun exportJson(currentState: AppPersistedState): String
    suspend fun importJson(jsonString: String): Result<Unit>
    suspend fun resetAll(): Result<Unit>
}
