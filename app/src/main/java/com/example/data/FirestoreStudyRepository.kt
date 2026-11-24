package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FirestoreStudyRepository(
    private val db: FirebaseFirestore,
    private val planProvider: () -> StudyPlanAsset,
    private val legacyLocalFile: File? = null,
    private val auth: FirebaseAuth = Firebase.auth
) : StudyDataSource {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        planProvider = {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(StudyPlanAsset::class.java)
            val json = context.applicationContext.assets.open("study_plan.json").bufferedReader().use { it.readText() }
            adapter.fromJson(json) ?: throw IllegalStateException("Failed to parse study_plan.json")
        },
        legacyLocalFile = File(context.applicationContext.filesDir, "hsc_study_state.json")
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val stateAdapter = moshi.adapter(AppPersistedState::class.java).indent("  ")
    private var cachedPlan: StudyPlanAsset? = null

    override fun getPlan(): StudyPlanAsset {
        cachedPlan?.let { return it }
        return planProvider().also { cachedPlan = it }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun chaptersCol(uid: String) = userDocRef(uid).collection("chapters")
    private fun weekRecordsCol(uid: String) = userDocRef(uid).collection("weekRecords")
    private fun dailyLogsCol(uid: String) = userDocRef(uid).collection("dailyLogs")
    private fun mistakesCol(uid: String) = userDocRef(uid).collection("mistakes")

    override suspend fun initialize(userId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = userId?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalStateException("Missing signed-in user ID"))
        val docPath = "users/$targetId"
        try {
            val uid = requireUserId()
            check(uid == targetId) { "Signed-in user mismatch." }
            val userSnap = runCatching { userDocRef(uid).get().await() }.getOrNull()
            val profile = userSnap?.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (userSnap != null && userSnap.exists() && profile?.seededDefaultChapters == true) {
                return@withContext Result.success(Unit)
            }

            // Also avoid duplicating if chapters already exist in cache/cloud
            val existingChapters = runCatching { chaptersCol(uid).limit(1).get().await() }.getOrNull()
            if (existingChapters != null && !existingChapters.isEmpty) {
                if (userSnap == null || !userSnap.exists()) {
                    createUserProfileDoc(uid, UserSettings(), seeded = true)
                } else if (profile?.seededDefaultChapters != true) {
                    userDocRef(uid).update(
                        mapOf(
                            "seededDefaultChapters" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
                }
                return@withContext Result.success(Unit)
            }

            // Migrate pre-existing local progress file if present, otherwise seed default PDF roadmap
            val legacyState = loadLegacyLocalState()
            val initialState = legacyState ?: createDefaultState(getPlan())
            seedWholeStateToFirestore(uid, initialState)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun loadLegacyLocalState(): AppPersistedState? {
        val file = legacyLocalFile ?: return null
        if (!file.exists()) return null
        return runCatching {
            val parsed = stateAdapter.fromJson(file.readText())
            parsed?.takeIf { it.chapters.isNotEmpty() }
        }.getOrNull()
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

    private fun createUserProfileDoc(uid: String, settings: UserSettings, seeded: Boolean) {
        val currentUser = auth.currentUser
        val safeName = (currentUser?.displayName ?: "HSC Student").take(120)
        val profileMap = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to safeName,
            "email" to currentUser?.email?.take(160),
            "dailyHours" to if (settings.dailyHours <= 3) 3L else 5L,
            "language" to settings.appLanguage().name,
            "darkMode" to settings.darkMode,
            "notificationsEnabled" to settings.notificationsEnabled,
            "reminderHour" to settings.reminderHour.coerceIn(0, 23).toLong(),
            "reminderMinute" to settings.reminderMinute.coerceIn(0, 59).toLong(),
            "seededDefaultChapters" to seeded,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>

        userDocRef(uid).set(profileMap)
            .addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, "users/$uid") }
    }

    private fun seedWholeStateToFirestore(uid: String, state: AppPersistedState) {
        val batch = db.batch()
        val currentUser = auth.currentUser
        val safeName = (currentUser?.displayName ?: "HSC Student").take(120)
        val profileMap = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to safeName,
            "email" to currentUser?.email?.take(160),
            "dailyHours" to if (state.settings.dailyHours <= 3) 3L else 5L,
            "language" to state.settings.appLanguage().name,
            "darkMode" to state.settings.darkMode,
            "notificationsEnabled" to state.settings.notificationsEnabled,
            "reminderHour" to state.settings.reminderHour.coerceIn(0, 23).toLong(),
            "reminderMinute" to state.settings.reminderMinute.coerceIn(0, 59).toLong(),
            "seededDefaultChapters" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        batch.set(userDocRef(uid), profileMap)

        state.chapters.forEachIndexed { index, ch ->
            val safeId = sanitizeId(ch.id, "ch_$index")
            val chMap = mapOf(
                "userId" to uid,
                "subjectId" to ch.subjectId.take(60).ifBlank { "physics" },
                "subjectGroup" to ch.subjectGroup.take(60).ifBlank { "physics" },
                "paper" to ch.paper.take(80).ifBlank { "১ম পত্র" },
                "startWeek" to ch.startWeek.coerceIn(1, StudyDateUtils.TOTAL_WEEKS).toLong(),
                "weekNumber" to ch.weekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS).toLong(),
                "title" to ch.title.trim().take(200).ifBlank { "Chapter" },
                "deadline" to ch.deadline.take(10).padEnd(10, '0'),
                "status" to ch.chapterStatus().name,
                "editablePlaceholder" to ch.isEditablePlaceholder,
                "sortOrder" to index.coerceIn(0, 1000).toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(chaptersCol(uid).document(safeId), chMap)
        }

        val weekKeys = (state.weeklyTasksCompleted.keys + state.fridayChecklistCompleted.keys).distinct()
        weekKeys.forEach { wkKey ->
            val wkNum = wkKey.toIntOrNull()?.coerceIn(1, StudyDateUtils.TOTAL_WEEKS) ?: return@forEach
            val tasks = state.weeklyTasksCompleted[wkKey].orEmpty().take(20)
            val friday = state.fridayChecklistCompleted[wkKey].orEmpty().take(20)
            if (tasks.isNotEmpty() || friday.isNotEmpty()) {
                val wkMap = mapOf(
                    "userId" to uid,
                    "weekNumber" to wkNum.toLong(),
                    "weeklyTasks" to tasks,
                    "fridayChecklist" to friday,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(weekRecordsCol(uid).document(wkNum.toString()), wkMap)
            }
        }

        state.dailyCompletedSlots.forEach { (dateIso, slots) ->
            val safeDateId = sanitizeId(dateIso, "")
            if (safeDateId.length == 10 && slots.isNotEmpty()) {
                val dayMap = mapOf(
                    "userId" to uid,
                    "dateIso" to safeDateId,
                    "completedSlots" to slots.take(10).map { it.toLong() },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(dailyLogsCol(uid).document(safeDateId), dayMap)
            }
        }

        state.mistakes.forEachIndexed { idx, m ->
            val safeMistakeId = sanitizeId(m.id, "m_${idx}_${UUID.randomUUID().toString().take(6)}")
            val mistakeMap = mapOf(
                "userId" to uid,
                "subjectId" to m.subjectId.take(60).ifBlank { "physics" },
                "subjectNameBn" to m.subjectNameBn.take(100).ifBlank { "সাধারণ" },
                "subjectNameEn" to m.subjectNameEn.take(100).ifBlank { "General" },
                "chapterTitle" to m.chapterTitle.take(200),
                "question" to m.question.take(2000).ifBlank { "Question" },
                "correctAnswer" to m.correctAnswer.take(2000).ifBlank { "Answer" },
                "date" to m.date.take(10).ifBlank { StudyDateUtils.todayIso() },
                "revised" to m.revised,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(mistakesCol(uid).document(safeMistakeId), mistakeMap)
        }

        // Fire-and-sync: commits to local persistent disk cache immediately even offline,
        // and Firestore automatically syncs to cloud when Wi-Fi is available.
        batch.commit().addOnFailureListener { e ->
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
        }
    }

    override fun observeStudyState(userId: String?): Flow<StudyDataSnapshot> = flow {
        val targetId = userId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing signed-in user ID")
        val uid = requireUserId()
        check(uid == targetId) { "Signed-in user changed; refusing cross-user observation." }

        val profileFlow = userDocRef(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .catch { error -> logAndRethrow(error, OperationType.GET, "users/$uid", uid) }

        val chaptersFlow = chaptersCol(uid)
            .orderBy("sortOrder")
            .snapshots(MetadataChanges.INCLUDE)
            .catch { error -> logAndRethrow(error, OperationType.LIST, "users/$uid/chapters", uid) }

        val weekRecordsFlow = weekRecordsCol(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .catch { error -> logAndRethrow(error, OperationType.LIST, "users/$uid/weekRecords", uid) }

        val dailyLogsFlow = dailyLogsCol(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .catch { error -> logAndRethrow(error, OperationType.LIST, "users/$uid/dailyLogs", uid) }

        val mistakesFlow = mistakesCol(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.INCLUDE)
            .catch { error -> logAndRethrow(error, OperationType.LIST, "users/$uid/mistakes", uid) }

        emitAll(
            combine(profileFlow, chaptersFlow, weekRecordsFlow, dailyLogsFlow, mistakesFlow) {
                profileSnap, chaptersSnap, weekSnap, dailySnap, mistakesSnap ->

                val profile = profileSnap.toObject(
                    UserProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )

                val settings = if (profile != null) {
                    val safeLang = AppLanguage.entries.firstOrNull { it.name == profile.language } ?: AppLanguage.BN
                    UserSettings(
                        dailyHours = if (profile.dailyHours.toInt() <= 3) 3 else 5,
                        language = safeLang.name,
                        darkMode = profile.darkMode,
                        notificationsEnabled = profile.notificationsEnabled,
                        reminderHour = profile.reminderHour.toInt().coerceIn(0, 23),
                        reminderMinute = profile.reminderMinute.toInt().coerceIn(0, 59)
                    )
                } else {
                    UserSettings()
                }

                val chapters = chaptersSnap.toObjects(
                    ChapterDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toUserChapter() }

                val weekDocs = weekSnap.toObjects(
                    WeekRecordDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val weeklyTasksMap = mutableMapOf<String, List<String>>()
                val fridayMap = mutableMapOf<String, List<String>>()
                weekDocs.forEach { doc ->
                    val key = doc.weekNumber.toString()
                    if (doc.weeklyTasks.isNotEmpty()) weeklyTasksMap[key] = doc.weeklyTasks
                    if (doc.fridayChecklist.isNotEmpty()) fridayMap[key] = doc.fridayChecklist
                }

                val dailyLogs = dailySnap.toObjects(
                    DailyLogDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val dailyMap = dailyLogs.associate { doc ->
                    doc.dateIso to doc.completedSlots.map { it.toInt() }.sorted()
                }

                val mistakes = mistakesSnap.toObjects(
                    MistakeDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toMistakeEntry() }

                val resolvedChapters = if (chapters.isEmpty() && profile?.seededDefaultChapters != true) {
                    createDefaultState(getPlan()).chapters
                } else {
                    chapters
                }

                val hasPendingWrites = profileSnap.metadata.hasPendingWrites() ||
                    chaptersSnap.metadata.hasPendingWrites() ||
                    weekSnap.metadata.hasPendingWrites() ||
                    dailySnap.metadata.hasPendingWrites() ||
                    mistakesSnap.metadata.hasPendingWrites()

                val isFromCache = profileSnap.metadata.isFromCache && chaptersSnap.metadata.isFromCache

                StudyDataSnapshot(
                    persistedState = AppPersistedState(
                        version = 1,
                        settings = settings,
                        chapters = resolvedChapters,
                        weeklyTasksCompleted = weeklyTasksMap,
                        fridayChecklistCompleted = fridayMap,
                        dailyCompletedSlots = dailyMap,
                        mistakes = mistakes
                    ),
                    isCloudBacked = true,
                    hasPendingWrites = hasPendingWrites,
                    isFromCache = isFromCache
                )
            }.flowOn(Dispatchers.Default)
        )
    }

    private fun logAndRethrow(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    override suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            require(chapterId.isNotBlank() && isValidDocId(chapterId)) { "Invalid chapter ID" }
            val docRef = chaptersCol(uid).document(chapterId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun editChapter(
        chapterId: String,
        newTitle: String,
        newDeadline: String,
        newWeekNumber: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            require(chapterId.isNotBlank() && isValidDocId(chapterId)) { "Invalid chapter ID" }
            val trimmedTitle = newTitle.trim().take(200)
            require(trimmedTitle.isNotEmpty()) { "Chapter title cannot be blank" }
            val safeDeadline = newDeadline.trim().take(10)
            require(safeDeadline.length == 10) { "Deadline must be YYYY-MM-DD" }
            val clampedWeek = newWeekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS).toLong()

            val docRef = chaptersCol(uid).document(chapterId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to trimmedTitle,
                    "deadline" to safeDeadline,
                    "weekNumber" to clampedWeek,
                    "editablePlaceholder" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun addChapter(
        subjectId: String,
        paper: String,
        title: String,
        weekNumber: Int,
        currentChapterCount: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chapters"
        return try {
            val uid = requireUserId()
            val plan = getPlan()
            val subject = plan.subjects.firstOrNull { it.id == subjectId }
                ?: throw IllegalArgumentException("Unknown subject: $subjectId")
            val trimmedTitle = title.trim().take(200)
            require(trimmedTitle.isNotEmpty()) { "Chapter title cannot be blank" }
            val clampedWeek = weekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS)
            val newId = "custom_${UUID.randomUUID().toString().take(8)}"
            val docRef = chaptersCol(uid).document(newId)
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subject.id.take(60),
                "subjectGroup" to subject.group.take(60),
                "paper" to paper.trim().ifBlank { subject.nameBn }.take(80),
                "startWeek" to clampedWeek.toLong(),
                "weekNumber" to clampedWeek.toLong(),
                "title" to trimmedTitle,
                "deadline" to StudyDateUtils.weekDeadlineIso(clampedWeek),
                "status" to ChapterStatus.NOT_STARTED.name,
                "editablePlaceholder" to false,
                "sortOrder" to currentChapterCount.coerceIn(0, 1000).toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteChapter(chapterId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            require(chapterId.isNotBlank() && isValidDocId(chapterId)) { "Invalid chapter ID" }
            val docRef = chaptersCol(uid).document(chapterId)
            docPath = docRef.path
            docRef.delete().addOnFailureListener { handleFirestoreError(it, OperationType.DELETE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleWeeklyMandatoryTask(
        weekNumber: Int,
        taskId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit> {
        val clampedWeek = weekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/weekRecords/$clampedWeek"
        return try {
            val uid = requireUserId()
            val updated = currentTasks.toMutableSet()
            if (!updated.add(taskId)) updated.remove(taskId)
            val docRef = weekRecordsCol(uid).document(clampedWeek.toString())
            docPath = docRef.path

            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists()) {
                docRef.update(
                    mapOf(
                        "weeklyTasks" to updated.take(20),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "weekNumber" to clampedWeek.toLong(),
                        "weeklyTasks" to updated.take(20),
                        "fridayChecklist" to currentFriday.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, docPath) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleFridayChecklistItem(
        weekNumber: Int,
        itemId: String,
        currentTasks: List<String>,
        currentFriday: List<String>
    ): Result<Unit> {
        val clampedWeek = weekNumber.coerceIn(1, StudyDateUtils.TOTAL_WEEKS)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/weekRecords/$clampedWeek"
        return try {
            val uid = requireUserId()
            val updated = currentFriday.toMutableSet()
            if (!updated.add(itemId)) updated.remove(itemId)
            val docRef = weekRecordsCol(uid).document(clampedWeek.toString())
            docPath = docRef.path

            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists()) {
                docRef.update(
                    mapOf(
                        "fridayChecklist" to updated.take(20),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "weekNumber" to clampedWeek.toLong(),
                        "weeklyTasks" to currentTasks.take(20),
                        "fridayChecklist" to updated.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, docPath) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun setDailySlotCompleted(
        dateIso: String,
        slot: Int,
        done: Boolean,
        currentSlots: List<Int>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/dailyLogs/$dateIso"
        return try {
            val uid = requireUserId()
            require(dateIso.length == 10 && isValidDocId(dateIso)) { "Invalid date ID" }
            val updated = currentSlots.toMutableSet()
            if (done) updated.add(slot) else updated.remove(slot)
            val sortedSlots = updated.sorted().take(10).map { it.toLong() }

            val docRef = dailyLogsCol(uid).document(dateIso)
            docPath = docRef.path
            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists()) {
                docRef.update(
                    mapOf(
                        "completedSlots" to sortedSlots,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateIso" to dateIso,
                        "completedSlots" to sortedSlots,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, docPath) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun addMistake(
        subjectId: String,
        chapterTitle: String,
        question: String,
        correctAnswer: String,
        dateIso: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mistakes"
        return try {
            val uid = requireUserId()
            val trimmedQ = question.trim().take(2000)
            val trimmedA = correctAnswer.trim().take(2000)
            require(trimmedQ.isNotEmpty() && trimmedA.isNotEmpty()) { "Question and answer cannot be blank" }
            val plan = getPlan()
            val subject = plan.subjects.firstOrNull { it.id == subjectId }
            val newId = "m_${UUID.randomUUID().toString().take(12)}"
            val docRef = mistakesCol(uid).document(newId)
            docPath = docRef.path

            val safeDate = dateIso.trim().take(10).let {
                if (it.length == 10) it else StudyDateUtils.todayIso()
            }

            docRef.set(
                mapOf(
                    "userId" to uid,
                    "subjectId" to subjectId.take(60).ifBlank { "physics" },
                    "subjectNameBn" to (subject?.nameBn ?: "সাধারণ").take(100),
                    "subjectNameEn" to (subject?.nameEn ?: "General").take(100),
                    "chapterTitle" to chapterTitle.trim().take(200),
                    "question" to trimmedQ,
                    "correctAnswer" to trimmedA,
                    "date" to safeDate,
                    "revised" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleMistakeRevised(mistakeId: String, currentlyRevised: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mistakes/$mistakeId"
        return try {
            val uid = requireUserId()
            require(mistakeId.isNotBlank() && isValidDocId(mistakeId)) { "Invalid mistake ID" }
            val docRef = mistakesCol(uid).document(mistakeId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "revised" to !currentlyRevised,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteMistake(mistakeId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mistakes/$mistakeId"
        return try {
            val uid = requireUserId()
            require(mistakeId.isNotBlank() && isValidDocId(mistakeId)) { "Invalid mistake ID" }
            val docRef = mistakesCol(uid).document(mistakeId)
            docPath = docRef.path
            docRef.delete().addOnFailureListener { handleFirestoreError(it, OperationType.DELETE, docPath) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateSettings(updated: UserSettings): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = userDocRef(uid)
            docPath = docRef.path
            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists()) {
                docRef.update(
                    mapOf(
                        "dailyHours" to if (updated.dailyHours <= 3) 3L else 5L,
                        "language" to updated.appLanguage().name,
                        "darkMode" to updated.darkMode,
                        "notificationsEnabled" to updated.notificationsEnabled,
                        "reminderHour" to updated.reminderHour.coerceIn(0, 23).toLong(),
                        "reminderMinute" to updated.reminderMinute.coerceIn(0, 59).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, docPath) }
            } else {
                createUserProfileDoc(uid, updated, seeded = true)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override fun exportJson(currentState: AppPersistedState): String = stateAdapter.toJson(currentState)

    override suspend fun importJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val parsed = stateAdapter.fromJson(jsonString)
                ?: throw IllegalArgumentException("Invalid JSON format")
            require(parsed.chapters.isNotEmpty()) { "Backup contains no chapters" }
            clearSubcollectionsAndReseed(uid, parsed)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun resetAll(): Result<Unit> = withContext(Dispatchers.IO) {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val fresh = createDefaultState(getPlan())
            clearSubcollectionsAndReseed(uid, fresh)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun clearSubcollectionsAndReseed(uid: String, targetState: AppPersistedState) {
        val batch = db.batch()
        val collections = listOf(chaptersCol(uid), weekRecordsCol(uid), dailyLogsCol(uid), mistakesCol(uid))
        collections.forEach { col ->
            val snap = runCatching { col.get().await() }.getOrNull()
            snap?.documents?.forEach { doc -> batch.delete(doc.reference) }
        }
        batch.commit().await()
        seedWholeStateToFirestore(uid, targetState)
    }

    private fun isValidDocId(id: String): Boolean =
        id.length in 1..128 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun sanitizeId(raw: String, fallback: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        return clean.ifBlank { fallback }
    }
}
