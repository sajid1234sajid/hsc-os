package com.example

import com.example.data.ChapterStatus
import com.example.data.StudyDateUtils
import com.example.data.StudyPlanAsset
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

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
    fun chapterStatusRolloverRulesMatchPlan() {
        assertFalse(ChapterStatus.NOT_STARTED.isFinishedForRollover())
        assertFalse(ChapterStatus.IN_PROGRESS.isFinishedForRollover())
        assertFalse(ChapterStatus.FIRST_READING_DONE.isFinishedForRollover())
        assertTrue(ChapterStatus.PRACTICE_DONE.isFinishedForRollover())
        assertTrue(ChapterStatus.REVISION_DONE.isFinishedForRollover())
        assertTrue(ChapterStatus.EXAM_TAKEN.isFinishedForRollover())
    }

    @Test
    fun studyPlanJsonParsesAllSubjectsWithoutBoxes() {
        val assetFile = File("src/main/assets/study_plan.json").let {
            if (it.exists()) it else File("app/src/main/assets/study_plan.json")
        }
        val rawJson = assetFile.readText()
        assertFalse("JSON must not contain missing-font box characters", rawJson.contains("□"))

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(StudyPlanAsset::class.java)
        val plan = adapter.fromJson(rawJson)!!

        assertEquals(26, plan.plan.totalWeeks)
        assertEquals(9, plan.subjects.size)
        assertEquals(7, plan.weeklyMandatoryTasks.size)
        assertEquals(4, plan.fridayExamChecklist.size)
        assertEquals(7, plan.weeklyRoutine.size)
    }

    @Test
    fun tutorRoleModelsMatchTaskTypes() {
        assertEquals("gemini-4-carbon", com.example.ai.TutorRole.DEEP_REASONING.modelId)
        assertEquals("gemini-4-neon", com.example.ai.TutorRole.INTERACTIVE_COACH.modelId)
        assertEquals("gemini-4-hydrogen", com.example.ai.TutorRole.GENERAL_TUTOR.modelId)
        assertEquals("gemini-3.1-flash-lite", com.example.ai.TutorRole.FAST_RECALL.modelId)
    }
}
