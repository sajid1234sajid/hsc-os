package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.ChapterStatus
import com.example.data.ChapterWithRollover
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.ui.components.ChapterStatusDropdown
import com.example.ui.viewmodel.StudyUiState

@Composable
fun WeeklyScreen(
    state: StudyUiState,
    onSelectWeek: (Int) -> Unit,
    onToggleMandatoryTask: (Int, String) -> Unit,
    onToggleFridayItem: (Int, String) -> Unit,
    onUpdateChapterStatus: (String, ChapterStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.settings.appLanguage()
    val week = state.selectedWeeklyViewWeek
    val plan = state.plan
    val weekTasksDone = state.weeklyTasksCompleted[week.toString()].orEmpty().toSet()
    val fridayDone = state.fridayChecklistCompleted[week.toString()].orEmpty().toSet()
    val weekChapters = state.chaptersWithRollover.filter { week in it.chapter.startWeek..it.effectiveWeek }
    val completionPct = state.selectedWeekCompletionPercent
    val isBelowGoal = completionPct < 80

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopLayout = maxWidth >= 840.dp

        if (isDesktopLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Left Pane: Week Goal, Mandatory Tasks, Friday Exam
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        WeekSummaryHeaderCard(
                            week = week,
                            completionPct = completionPct,
                            isBelowGoal = isBelowGoal,
                            lang = lang,
                            onSelectWeek = onSelectWeek
                        )
                    }
                    item {
                        WeeklyGoalAlertBanner(completionPct = completionPct, isBelowGoal = isBelowGoal, lang = lang)
                    }
                    item {
                        Text(
                            text = StringsProvider.text(lang, "প্রতি সপ্তাহের বাধ্যতামূলক কাজ", "Weekly Mandatory Tasks"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(plan?.weeklyMandatoryTasks.orEmpty(), key = { it.id }) { task ->
                        ChecklistRowCard(
                            label = task.label(lang),
                            checked = weekTasksDone.contains(task.id),
                            testTag = "weekly_task_${task.id}",
                            onToggle = { onToggleMandatoryTask(week, task.id) }
                        )
                    }
                    item {
                        Text(
                            text = StringsProvider.text(lang, "শুক্রবার — সাপ্তাহিক পরীক্ষা চেকলিস্ট", "Friday — Weekly Exam Checklist"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(plan?.fridayExamChecklist.orEmpty(), key = { it.id }) { item ->
                        ChecklistRowCard(
                            label = item.label(lang),
                            checked = fridayDone.contains(item.id),
                            testTag = "friday_task_${item.id}",
                            onToggle = { onToggleFridayItem(week, item.id) }
                        )
                    }
                }

                // Right Pane: Chapters due or rolled over into this week
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "সপ্তাহ ${StudyDateUtils.toBnDigits(week)}-এর নির্ধারিত ও রোলওভার অধ্যায় (${StudyDateUtils.toBnDigits(weekChapters.size)})",
                                "Chapters in Week $week (${weekChapters.size})"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(weekChapters, key = { it.chapter.id }) { item ->
                        WeeklyChapterRow(
                            item = item,
                            lang = lang,
                            onUpdateChapterStatus = onUpdateChapterStatus
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        WeekSummaryHeaderCard(
                            week = week,
                            completionPct = completionPct,
                            isBelowGoal = isBelowGoal,
                            lang = lang,
                            onSelectWeek = onSelectWeek
                        )
                    }

                    item {
                        WeeklyGoalAlertBanner(completionPct = completionPct, isBelowGoal = isBelowGoal, lang = lang)
                    }

                    item {
                        Text(
                            text = StringsProvider.text(lang, "প্রতি সপ্তাহের বাধ্যতামূলক কাজ", "Weekly Mandatory Tasks"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(plan?.weeklyMandatoryTasks.orEmpty(), key = { it.id }) { task ->
                        ChecklistRowCard(
                            label = task.label(lang),
                            checked = weekTasksDone.contains(task.id),
                            testTag = "weekly_task_${task.id}",
                            onToggle = { onToggleMandatoryTask(week, task.id) }
                        )
                    }

                    item {
                        Text(
                            text = StringsProvider.text(lang, "শুক্রবার — সাপ্তাহিক পরীক্ষা চেকলিস্ট", "Friday — Weekly Exam Checklist"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(plan?.fridayExamChecklist.orEmpty(), key = { it.id }) { item ->
                        ChecklistRowCard(
                            label = item.label(lang),
                            checked = fridayDone.contains(item.id),
                            testTag = "friday_task_${item.id}",
                            onToggle = { onToggleFridayItem(week, item.id) }
                        )
                    }

                    item {
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "সপ্তাহ ${StudyDateUtils.toBnDigits(week)}-এর নির্ধারিত ও রোলওভার অধ্যায় (${StudyDateUtils.toBnDigits(weekChapters.size)})",
                                "Chapters in Week $week (${weekChapters.size})"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(weekChapters, key = { it.chapter.id }) { item ->
                        WeeklyChapterRow(
                            item = item,
                            lang = lang,
                            onUpdateChapterStatus = onUpdateChapterStatus
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WeekSummaryHeaderCard(
    week: Int,
    completionPct: Int,
    isBelowGoal: Boolean,
    lang: AppLanguage,
    onSelectWeek: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    enabled = week > 1,
                    onClick = { onSelectWeek(week - 1) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("weekly_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = StringsProvider.text(lang, "আগের সপ্তাহ", "Previous week")
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = StringsProvider.text(
                            lang,
                            "সপ্তাহ ${StudyDateUtils.toBnDigits(week)} / ২৬",
                            "Week $week / 26"
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${StudyDateUtils.formatShortDate(StudyDateUtils.weekStartIso(week), lang)} – ${StudyDateUtils.formatShortDate(StudyDateUtils.weekDeadlineIso(week), lang)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    enabled = week < 26,
                    onClick = { onSelectWeek(week + 1) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("weekly_next_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = StringsProvider.text(lang, "পরের সপ্তাহ", "Next week")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = StringsProvider.text(lang, "সাপ্তাহিক লক্ষ্য: ৮০–৯০%", "Weekly Target: 80–90%"),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "${StringsProvider.num(completionPct, lang)}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isBelowGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { completionPct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = if (isBelowGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun WeeklyGoalAlertBanner(
    completionPct: Int,
    isBelowGoal: Boolean,
    lang: AppLanguage
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isBelowGoal) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            1.dp,
            if (isBelowGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isBelowGoal) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isBelowGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isBelowGoal) {
                    StringsProvider.text(
                        lang,
                        "সতর্কতা: সম্পন্ন হার ৮০%-এর নিচে (${StudyDateUtils.toBnDigits(completionPct)}%)। কোনো অধ্যায় কঠিন হলে বাদ না দিয়ে পরের সপ্তাহে অতিরিক্ত সময় দিয়ে শেষ করুন।",
                        "Warning: Weekly completion is below 80% ($completionPct%). Never skip tough chapters—roll them over and finish next week."
                    )
                } else {
                    StringsProvider.text(
                        lang,
                        "দারুণ! সাপ্তাহিক লক্ষ্য (৮০%+) অর্জিত হয়েছে।",
                        "Great job! You have achieved the 80%+ weekly target."
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isBelowGoal) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun WeeklyChapterRow(
    item: ChapterWithRollover,
    lang: AppLanguage,
    onUpdateChapterStatus: (String, ChapterStatus) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (item.isOverdue) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (item.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "${if (lang == AppLanguage.BN) item.subjectNameBn else item.subjectNameEn} • ${item.chapter.paper}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            ChapterStatusDropdown(
                currentStatus = item.chapter.chapterStatus(),
                lang = lang,
                isOverdue = item.isOverdue,
                testTag = "weekly_chapter_status_${item.chapter.id}",
                onStatusSelected = { status -> onUpdateChapterStatus(item.chapter.id, status) }
            )
        }
    }
}

@Composable
private fun ChecklistRowCard(
    label: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.minimumInteractiveComponentSize()
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
