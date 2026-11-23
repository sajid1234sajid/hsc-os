package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChapterStatus
import com.example.data.StudyDateUtils
import com.example.data.StudyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameMatchesBengaliTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HSC স্টাডি", appName)
    }

    @Test
    fun studyPlanJsonParsesCleanlyWithoutMissingBoxes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = StudyRepository(context)
        val plan = repo.getPlan()

        assertEquals(26, plan.plan.totalWeeks)
        assertEquals(9, plan.subjects.size)
        assertEquals(7, plan.weeklyMandatoryTasks.size)
        assertEquals(4, plan.fridayExamChecklist.size)
        assertEquals(7, plan.weeklyRoutine.size)

        val rawJson = context.assets.open("study_plan.json").bufferedReader().use { it.readText() }
        assertFalse(rawJson.contains("□"))
    }

    @Test
    fun studyDateUtilsComputesExactWeeksAndDurations() {
        assertEquals(1, StudyDateUtils.currentWeekNumber("2026-11-24"))
        assertEquals(1, StudyDateUtils.currentWeekNumber("2026-11-30"))
        assertEquals(2, StudyDateUtils.currentWeekNumber("2026-12-01"))
        assertEquals(26, StudyDateUtils.currentWeekNumber("2027-05-23"))
        assertEquals("2026-11-30", StudyDateUtils.weekDeadlineIso(1))
        assertEquals("2027-05-23", StudyDateUtils.weekDeadlineIso(26))

        assertEquals(Triple(50, 20, 5), StudyDateUtils.phaseDurationsMinutes(5))
        assertEquals(Triple(30, 12, 3), StudyDateUtils.phaseDurationsMinutes(3))
    }

    @Test
    fun repositoryPersistsAndRoundTripsJsonBackup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = StudyRepository(context)
        val initial = repo.initialize()
        assertTrue(initial.chapters.isNotEmpty())

        repo.updateChapterStatus("phy_1", ChapterStatus.EXAM_TAKEN)
        repo.addMistake("physics", "ভেক্টর", "লব্ধির দিক নির্ণয়", "tanθ = Qsinα / (P + Qcosα)", "2026-11-24")

        val backupJson = repo.exportJson()
        repo.resetAll()
        assertEquals(0, repo.state.value?.mistakes?.size)

        val imported = repo.importJson(backupJson)
        assertTrue(imported.isSuccess)
        assertEquals(1, repo.state.value?.mistakes?.size)
        assertEquals(
            ChapterStatus.EXAM_TAKEN.name,
            repo.state.value?.chapters?.first { it.id == "phy_1" }?.status
        )
    }
}
