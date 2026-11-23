package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.ChapterStatus
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.SubjectAsset
import com.example.data.UserChapter
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralErrorDark
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SubjectBanglaColor
import com.example.ui.theme.SubjectBiologyColor
import com.example.ui.theme.SubjectChemistryColor
import com.example.ui.theme.SubjectEnglishColor
import com.example.ui.theme.SubjectIctColor
import com.example.ui.theme.SubjectMathColor
import com.example.ui.theme.SubjectPhysicsColor

fun subjectAccentColor(group: String): Color = when (group) {
    "physics" -> SubjectPhysicsColor
    "higher_math" -> SubjectMathColor
    "biology" -> SubjectBiologyColor
    "chemistry", "chemistry_biology" -> SubjectChemistryColor
    "bangla" -> SubjectBanglaColor
    "english" -> SubjectEnglishColor
    "ict", "bangla_english_ict" -> SubjectIctColor
    else -> EmeraldBright
}

@Composable
fun statusBadgeColor(status: ChapterStatus, isOverdue: Boolean): Color {
    if (isOverdue && !status.isFinishedForRollover()) return MaterialTheme.colorScheme.error
    return when (status) {
        ChapterStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
        ChapterStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        ChapterStatus.FIRST_READING_DONE -> MaterialTheme.colorScheme.tertiary
        ChapterStatus.PRACTICE_DONE -> MaterialTheme.colorScheme.primary
        ChapterStatus.REVISION_DONE -> MaterialTheme.colorScheme.primary
        ChapterStatus.EXAM_TAKEN -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun ChapterStatusDropdown(
    currentStatus: ChapterStatus,
    lang: AppLanguage,
    isOverdue: Boolean,
    testTag: String,
    onStatusSelected: (ChapterStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val badgeColor = statusBadgeColor(currentStatus, isOverdue)

    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag),
            shape = RoundedCornerShape(12.dp),
            color = badgeColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStatus.isFinishedForRollover()) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                } else if (isOverdue) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = currentStatus.label(lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = StringsProvider.text(lang, "স্ট্যাটাস পরিবর্তন", "Change status"),
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ChapterStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = status.label(lang),
                            fontWeight = if (status == currentStatus) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onStatusSelected(status)
                    }
                )
            }
        }
    }
}

@Composable
fun StudySegmentedPhaseBar(
    elapsedSeconds: Int,
    dailyHours: Int,
    modifier: Modifier = Modifier
) {
    val (p1Min, p2Min, p3Min) = StudyDateUtils.phaseDurationsMinutes(dailyHours)
    val p1Sec = p1Min * 60f
    val p2Sec = p2Min * 60f
    val p3Sec = p3Min * 60f

    val p1Fill = (elapsedSeconds / p1Sec).coerceIn(0f, 1f)
    val p2Fill = ((elapsedSeconds - p1Sec) / p2Sec).coerceIn(0f, 1f)
    val p3Fill = ((elapsedSeconds - p1Sec - p2Sec) / p3Sec).coerceIn(0f, 1f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PhaseSegmentBar(
            weight = 50f,
            fill = p1Fill,
            activeColor = EmeraldBright,
            modifier = Modifier.weight(50f)
        )
        PhaseSegmentBar(
            weight = 20f,
            fill = p2Fill,
            activeColor = AmberWarm,
            modifier = Modifier.weight(20f)
        )
        PhaseSegmentBar(
            weight = 5f,
            fill = p3Fill,
            activeColor = SkyAccent,
            modifier = Modifier.weight(10f)
        )
    }
}

@Composable
private fun PhaseSegmentBar(
    weight: Float,
    fill: Float,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .height(8.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}

@Composable
fun EditChapterDialog(
    chapter: UserChapter,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (title: String, deadline: String, weekNumber: Int) -> Unit
) {
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var deadline by remember(chapter.id) { mutableStateOf(chapter.deadline) }
    var weekText by remember(chapter.id) { mutableStateOf(chapter.weekNumber.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = StringsProvider.text(lang, "অধ্যায় ও ডেডলাইন এডিট", "Edit Chapter & Deadline"),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(StringsProvider.text(lang, "অধ্যায় / ইউনিটের নাম", "Chapter / Unit Title")) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weekText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        weekText = filtered
                        val wk = filtered.toIntOrNull()
                        if (wk != null && wk in 1..26) {
                            deadline = StudyDateUtils.weekDeadlineIso(wk)
                        }
                    },
                    label = { Text(StringsProvider.text(lang, "সপ্তাহ (১–২৬)", "Week (1–26)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text(StringsProvider.text(lang, "ডেডলাইন (YYYY-MM-DD)", "Deadline (YYYY-MM-DD)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wk = weekText.toIntOrNull()?.coerceIn(1, 26) ?: chapter.weekNumber
                    onSave(title, deadline, wk)
                },
                modifier = Modifier.testTag("save_chapter_edit_button")
            ) {
                Text(StringsProvider.text(lang, "সংরক্ষণ", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(StringsProvider.text(lang, "বাতিল", "Cancel"))
            }
        }
    )
}

@Composable
fun AddChapterDialog(
    subjects: List<SubjectAsset>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onAdd: (subjectId: String, paper: String, title: String, weekNumber: Int) -> Unit
) {
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var paper by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var weekText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = StringsProvider.text(lang, "নতুন অধ্যায় যোগ করুন", "Add New Chapter"),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { subjectMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedSubject?.let {
                                if (lang == AppLanguage.BN) it.nameBn else it.nameEn
                            } ?: StringsProvider.text(lang, "বিষয় নির্বাচন", "Select Subject")
                        )
                    }
                    DropdownMenu(
                        expanded = subjectMenuExpanded,
                        onDismissRequest = { subjectMenuExpanded = false }
                    ) {
                        subjects.forEach { subj ->
                            DropdownMenuItem(
                                text = { Text(if (lang == AppLanguage.BN) subj.nameBn else subj.nameEn) },
                                onClick = {
                                    selectedSubject = subj
                                    subjectMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = paper,
                    onValueChange = { paper = it },
                    label = { Text(StringsProvider.text(lang, "পত্র / অংশ (যেমন: ১ম পত্র)", "Paper / Section")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(StringsProvider.text(lang, "অধ্যায়ের নাম", "Chapter Title")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weekText,
                    onValueChange = { weekText = it.filter { c -> c.isDigit() } },
                    label = { Text(StringsProvider.text(lang, "সপ্তাহ (১–২৬)", "Week (1–26)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && selectedSubject != null,
                onClick = {
                    val subj = selectedSubject ?: return@Button
                    val wk = weekText.toIntOrNull()?.coerceIn(1, 26) ?: 1
                    onAdd(subj.id, paper, title, wk)
                }
            ) {
                Text(StringsProvider.text(lang, "যোগ করুন", "Add"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(StringsProvider.text(lang, "বাতিল", "Cancel"))
            }
        }
    )
}
