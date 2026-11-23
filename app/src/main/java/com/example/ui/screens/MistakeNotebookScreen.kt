package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.MistakeEntry
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.SubjectAsset
import com.example.ui.components.subjectAccentColor
import com.example.ui.viewmodel.StudyUiState

private enum class MistakeFilter {
    ALL, UNREVISED, REVISED
}

@Composable
fun MistakeNotebookScreen(
    state: StudyUiState,
    onAddMistake: (subjectId: String, chapterTitle: String, question: String, correctAnswer: String, dateIso: String) -> Unit,
    onToggleRevised: (String) -> Unit,
    onDeleteMistake: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.settings.appLanguage()
    val subjects = state.plan?.subjects.orEmpty()
    var showAddDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(MistakeFilter.ALL) }
    var subjectFilter by remember { mutableStateOf("ALL") }

    val filteredMistakes = remember(state.mistakes, filter, subjectFilter) {
        state.mistakes.filter { m ->
            val matchRev = when (filter) {
                MistakeFilter.ALL -> true
                MistakeFilter.UNREVISED -> !m.revised
                MistakeFilter.REVISED -> m.revised
            }
            val matchSubj = subjectFilter == "ALL" || m.subjectId == subjectFilter
            matchRev && matchSubj
        }
    }

    val unrevisedCount = state.mistakes.count { !it.revised }
    val revisedCount = state.mistakes.count { it.revised }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MistakeStatPill(
                            label = StringsProvider.text(lang, "মোট ভুল", "Total"),
                            value = StringsProvider.num(state.mistakes.size, lang),
                            modifier = Modifier.weight(1f)
                        )
                        MistakeStatPill(
                            label = StringsProvider.text(lang, "রিভিশন বাকি", "To Revise"),
                            value = StringsProvider.num(unrevisedCount, lang),
                            highlightError = unrevisedCount > 0,
                            modifier = Modifier.weight(1f)
                        )
                        MistakeStatPill(
                            label = StringsProvider.text(lang, "রিভাইজড", "Revised"),
                            value = StringsProvider.num(revisedCount, lang),
                            highlightPrimary = revisedCount > 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = filter == MistakeFilter.ALL,
                                onClick = { filter = MistakeFilter.ALL },
                                label = { Text(StringsProvider.text(lang, "সব", "All")) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filter == MistakeFilter.UNREVISED,
                                onClick = { filter = MistakeFilter.UNREVISED },
                                label = { Text(StringsProvider.text(lang, "রিভিশন বাকি", "Unrevised")) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filter == MistakeFilter.REVISED,
                                onClick = { filter = MistakeFilter.REVISED },
                                label = { Text(StringsProvider.text(lang, "রিভাইজড", "Revised")) }
                            )
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = subjectFilter == "ALL",
                                onClick = { subjectFilter = "ALL" },
                                label = { Text(StringsProvider.text(lang, "সব বিষয়", "All Subjects")) }
                            )
                        }
                        items(subjects, key = { it.id }) { subj ->
                            FilterChip(
                                selected = subjectFilter == subj.id,
                                onClick = { subjectFilter = subj.id },
                                label = { Text(if (lang == AppLanguage.BN) subj.nameBn else subj.nameEn) }
                            )
                        }
                    }
                }

                if (filteredMistakes.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = StringsProvider.text(lang, "ভুলের খাতা এখন খালি", "Mistake Notebook is empty"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = StringsProvider.text(
                                        lang,
                                        "অনুশীলন বা সাপ্তাহিক পরীক্ষায় ভুল হওয়া প্রশ্ন ও সঠিক উত্তর এখানে লিখে রাখুন এবং নিয়মিত রিভিশন দিন।",
                                        "Save wrong questions from practice or Friday exams here and mark them as revised after review."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMistakes, key = { it.id }) { mistake ->
                        MistakeCard(
                            entry = mistake,
                            lang = lang,
                            onToggleRevised = { onToggleRevised(mistake.id) },
                            onDelete = { onDeleteMistake(mistake.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_mistake_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = StringsProvider.text(lang, "ভুল যোগ করুন", "Add Mistake"),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddDialog) {
        AddMistakeDialog(
            subjects = subjects,
            state = state,
            lang = lang,
            onDismiss = { showAddDialog = false },
            onSave = { subjectId, chapterTitle, question, answer, dateIso ->
                onAddMistake(subjectId, chapterTitle, question, answer, dateIso)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MistakeStatPill(
    label: String,
    value: String,
    highlightError: Boolean = false,
    highlightPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    highlightError -> MaterialTheme.colorScheme.error
                    highlightPrimary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MistakeCard(
    entry: MistakeEntry,
    lang: AppLanguage,
    onToggleRevised: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = subjectAccentColor(
        when (entry.subjectId) {
            "physics" -> "physics"
            "higher_math" -> "higher_math"
            "biology" -> "biology"
            "chemistry" -> "chemistry"
            "bangla_1", "bangla_2" -> "bangla"
            "english_1", "english_2" -> "english"
            else -> "ict"
        }
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            if (entry.revised) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.BN) entry.subjectNameBn else entry.subjectNameEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = StudyDateUtils.formatShortDate(entry.date, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = StringsProvider.text(lang, "মুছুন", "Delete"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.chapterTitle.isNotBlank()) {
                Text(
                    text = entry.chapterTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${StringsProvider.text(lang, "প্রশ্ন:", "Q:")} ${entry.question}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    text = "${StringsProvider.text(lang, "সঠিক উত্তর:", "Answer:")} ${entry.correctAnswer}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilterChip(
                    selected = entry.revised,
                    onClick = onToggleRevised,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            if (entry.revised) {
                                StringsProvider.text(lang, "রিভিশন সম্পন্ন", "Revised")
                            } else {
                                StringsProvider.text(lang, "রিভিশন হিসেবে চিহ্নিত করুন", "Mark Revised")
                            }
                        )
                    },
                    modifier = Modifier.testTag("mistake_revised_${entry.id}")
                )
            }
        }
    }
}

@Composable
private fun AddMistakeDialog(
    subjects: List<SubjectAsset>,
    state: StudyUiState,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (subjectId: String, chapterTitle: String, question: String, correctAnswer: String, dateIso: String) -> Unit
) {
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
    var subjectMenuOpen by remember { mutableStateOf(false) }
    var chapterMenuOpen by remember { mutableStateOf(false) }
    var chapterTitle by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var correctAnswer by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(state.todayIso) }

    val chaptersForSubject = remember(selectedSubject, state.chaptersWithRollover) {
        state.chaptersWithRollover
            .filter { it.chapter.subjectId == selectedSubject?.id }
            .map { it.chapter.title }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = StringsProvider.text(lang, "ভুলের খাতায় নতুন এন্ট্রি", "Add Mistake Entry"),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { subjectMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedSubject?.let {
                                if (lang == AppLanguage.BN) it.nameBn else it.nameEn
                            } ?: StringsProvider.text(lang, "বিষয়", "Subject")
                        )
                    }
                    DropdownMenu(
                        expanded = subjectMenuOpen,
                        onDismissRequest = { subjectMenuOpen = false }
                    ) {
                        subjects.forEach { subj ->
                            DropdownMenuItem(
                                text = { Text(if (lang == AppLanguage.BN) subj.nameBn else subj.nameEn) },
                                onClick = {
                                    selectedSubject = subj
                                    subjectMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Box {
                    OutlinedTextField(
                        value = chapterTitle,
                        onValueChange = { chapterTitle = it },
                        label = { Text(StringsProvider.text(lang, "অধ্যায়", "Chapter")) },
                        trailingIcon = {
                            TextButton(onClick = { chapterMenuOpen = true }) {
                                Text(StringsProvider.text(lang, "তালিকা", "List"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = chapterMenuOpen,
                        onDismissRequest = { chapterMenuOpen = false }
                    ) {
                        chaptersForSubject.forEach { title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    chapterTitle = title
                                    chapterMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(StringsProvider.text(lang, "ভুল প্রশ্ন", "Wrong Question")) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_question_input")
                )

                OutlinedTextField(
                    value = correctAnswer,
                    onValueChange = { correctAnswer = it },
                    label = { Text(StringsProvider.text(lang, "সঠিক উত্তর / ব্যাখ্যা", "Correct Answer / Note")) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_answer_input")
                )

                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text(StringsProvider.text(lang, "তারিখ (YYYY-MM-DD)", "Date (YYYY-MM-DD)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = question.isNotBlank() && correctAnswer.isNotBlank() && selectedSubject != null,
                onClick = {
                    val subj = selectedSubject ?: return@Button
                    onSave(subj.id, chapterTitle, question, correctAnswer, dateIso)
                },
                modifier = Modifier.testTag("save_mistake_button")
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
