package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.ChapterStatus
import com.example.data.ChapterWithRollover
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.UserChapter
import com.example.ui.components.ChapterStatusDropdown
import com.example.ui.components.EditChapterDialog
import com.example.ui.components.subjectAccentColor
import com.example.ui.viewmodel.StudyUiState

private enum class RoadmapFilterMode {
    ALL, THIS_WEEK, OVERDUE
}

@Composable
fun RoadmapScreen(
    state: StudyUiState,
    onUpdateStatus: (String, ChapterStatus) -> Unit,
    onEditChapter: (String, String, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.settings.appLanguage()
    val subjects = state.plan?.subjects.orEmpty()
    var selectedSubjectId by remember { mutableStateOf("ALL") }
    var filterMode by remember { mutableStateOf(RoadmapFilterMode.ALL) }
    var query by remember { mutableStateOf("") }
    var editingChapter by remember { mutableStateOf<UserChapter?>(null) }

    val filteredChapters = remember(state.chaptersWithRollover, selectedSubjectId, filterMode, query) {
        state.chaptersWithRollover.filter { item ->
            val matchesSubject = selectedSubjectId == "ALL" || item.chapter.subjectId == selectedSubjectId
            val matchesMode = when (filterMode) {
                RoadmapFilterMode.ALL -> true
                RoadmapFilterMode.THIS_WEEK -> item.isCurrentWeek
                RoadmapFilterMode.OVERDUE -> item.isOverdue
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                item.chapter.title.contains(q, ignoreCase = true) ||
                item.subjectNameBn.contains(q, ignoreCase = true) ||
                item.subjectNameEn.contains(q, ignoreCase = true) ||
                item.chapter.paper.contains(q, ignoreCase = true)
            matchesSubject && matchesMode && matchesQuery
        }
    }

    val activeSubjectObj = subjects.firstOrNull { it.id == selectedSubjectId }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopLayout = maxWidth >= 840.dp

        if (isDesktopLayout) {
            // Canonical Desktop List-Detail Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Subject List Pane
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = StringsProvider.text(lang, "বিষয়সমূহ", "Subjects"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        item {
                            SubjectSidebarItem(
                                name = StringsProvider.text(lang, "সব বিষয়", "All Subjects"),
                                subtitle = "${StringsProvider.num(state.chaptersWithRollover.size, lang)} ${StringsProvider.text(lang, "টি অধ্যায়", "chapters")}",
                                percent = state.overallCompletionPercent,
                                selected = selectedSubjectId == "ALL",
                                accent = MaterialTheme.colorScheme.primary,
                                onClick = { selectedSubjectId = "ALL" }
                            )
                        }
                        items(state.subjectSummaries, key = { it.subjectId }) { summary ->
                            SubjectSidebarItem(
                                name = if (lang == AppLanguage.BN) summary.nameBn else summary.nameEn,
                                subtitle = "${StringsProvider.num(summary.completedChapters, lang)}/${StringsProvider.num(summary.totalChapters, lang)}",
                                percent = summary.percent,
                                selected = selectedSubjectId == summary.subjectId,
                                accent = subjectAccentColor(summary.group),
                                onClick = { selectedSubjectId = summary.subjectId }
                            )
                        }
                    }
                }

                // Right Chapters Detail Grid
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = {
                                Text(StringsProvider.text(lang, "অধ্যায় বা বিষয় খুঁজুন...", "Search chapter or subject..."))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("roadmap_search_input")
                        )
                        FilterModeChipsRow(
                            filterMode = filterMode,
                            state = state,
                            lang = lang,
                            onSelectMode = { filterMode = it }
                        )
                    }

                    if (activeSubjectObj != null && activeSubjectObj.noteBn.isNotBlank()) {
                        SubjectNoteBanner(note = activeSubjectObj.noteBn)
                    }

                    if (filteredChapters.isEmpty()) {
                        RoadmapEmptyCard(lang = lang)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredChapters, key = { it.chapter.id }) { item ->
                                RoadmapChapterCard(
                                    item = item,
                                    lang = lang,
                                    onStatusSelected = { status -> onUpdateStatus(item.chapter.id, status) },
                                    onEditClick = { editingChapter = item.chapter }
                                )
                            }
                        }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = {
                                Text(StringsProvider.text(lang, "অধ্যায় বা বিষয় খুঁজুন...", "Search chapter or subject..."))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("roadmap_search_input")
                        )
                    }

                    item {
                        FilterModeChipsRow(
                            filterMode = filterMode,
                            state = state,
                            lang = lang,
                            onSelectMode = { filterMode = it }
                        )
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSubjectId == "ALL",
                                    onClick = { selectedSubjectId = "ALL" },
                                    label = { Text(StringsProvider.text(lang, "সব বিষয়", "All Subjects")) },
                                    modifier = Modifier.testTag("roadmap_subject_ALL")
                                )
                            }
                            items(subjects, key = { it.id }) { subj ->
                                FilterChip(
                                    selected = selectedSubjectId == subj.id,
                                    onClick = { selectedSubjectId = subj.id },
                                    label = { Text(if (lang == AppLanguage.BN) subj.nameBn else subj.nameEn) },
                                    modifier = Modifier.testTag("roadmap_subject_${subj.id}")
                                )
                            }
                        }
                    }

                    if (activeSubjectObj != null && activeSubjectObj.noteBn.isNotBlank()) {
                        item { SubjectNoteBanner(note = activeSubjectObj.noteBn) }
                    }

                    if (filteredChapters.isEmpty()) {
                        item { RoadmapEmptyCard(lang = lang) }
                    } else {
                        items(filteredChapters, key = { it.chapter.id }) { item ->
                            RoadmapChapterCard(
                                item = item,
                                lang = lang,
                                onStatusSelected = { status -> onUpdateStatus(item.chapter.id, status) },
                                onEditClick = { editingChapter = item.chapter }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    editingChapter?.let { ch ->
        EditChapterDialog(
            chapter = ch,
            lang = lang,
            onDismiss = { editingChapter = null },
            onSave = { title, deadline, wk ->
                onEditChapter(ch.id, title, deadline, wk)
                editingChapter = null
            }
        )
    }
}

@Composable
private fun SubjectSidebarItem(
    name: String,
    subtitle: String,
    percent: Int,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun FilterModeChipsRow(
    filterMode: RoadmapFilterMode,
    state: StudyUiState,
    lang: AppLanguage,
    onSelectMode: (RoadmapFilterMode) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = filterMode == RoadmapFilterMode.ALL,
                onClick = { onSelectMode(RoadmapFilterMode.ALL) },
                label = { Text(StringsProvider.text(lang, "সব অধ্যায়", "All")) }
            )
        }
        item {
            FilterChip(
                selected = filterMode == RoadmapFilterMode.THIS_WEEK,
                onClick = { onSelectMode(RoadmapFilterMode.THIS_WEEK) },
                label = {
                    Text(
                        StringsProvider.text(
                            lang,
                            "এই সপ্তাহ (${StudyDateUtils.toBnDigits(state.currentWeek)})",
                            "Week ${state.currentWeek}"
                        )
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = filterMode == RoadmapFilterMode.OVERDUE,
                onClick = { onSelectMode(RoadmapFilterMode.OVERDUE) },
                label = {
                    Text(
                        StringsProvider.text(
                            lang,
                            "বকেয়া (${StudyDateUtils.toBnDigits(state.chaptersWithRollover.count { it.isOverdue })})",
                            "Overdue (${state.chaptersWithRollover.count { it.isOverdue }})"
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun SubjectNoteBanner(note: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun RoadmapEmptyCard(lang: AppLanguage) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = StringsProvider.text(lang, "কোনো অধ্যায় পাওয়া যায়নি", "No chapters matched"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = StringsProvider.text(lang, "ফিল্টার পরিবর্তন করে দেখুন।", "Try changing the subject or filter."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoadmapChapterCard(
    item: ChapterWithRollover,
    lang: AppLanguage,
    onStatusSelected: (ChapterStatus) -> Unit,
    onEditClick: () -> Unit
) {
    val ch = item.chapter
    val status = ch.chapterStatus()
    val accent = subjectAccentColor(ch.subjectGroup)

    val containerColor = when {
        item.isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        item.isCurrentWeek -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = when {
        item.isOverdue -> MaterialTheme.colorScheme.error
        item.isCurrentWeek -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (item.isOverdue || item.isCurrentWeek) 1.5.dp else 1.dp, borderColor)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.BN) item.subjectNameBn else item.subjectNameEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = ch.paper,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.isOverdue) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = StringsProvider.text(lang, "বকেয়া", "OVERDUE"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (item.isCurrentWeek) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = StringsProvider.text(lang, "এই সপ্তাহ", "THIS WEEK"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ch.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_chapter_${ch.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = StringsProvider.text(lang, "অধ্যায় এডিট করুন", "Edit chapter"),
                        tint = if (ch.isEditablePlaceholder) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    val weekRangeText = if (ch.startWeek < ch.weekNumber) {
                        StringsProvider.text(
                            lang,
                            "সপ্তাহ ${StudyDateUtils.toBnDigits(ch.startWeek)}–${StudyDateUtils.toBnDigits(ch.weekNumber)}",
                            "Weeks ${ch.startWeek}–${ch.weekNumber}"
                        )
                    } else {
                        StringsProvider.text(
                            lang,
                            "সপ্তাহ ${StudyDateUtils.toBnDigits(ch.weekNumber)}",
                            "Week ${ch.weekNumber}"
                        )
                    }
                    Text(
                        text = "$weekRangeText • ${StudyDateUtils.formatShortDate(ch.deadline, lang)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ChapterStatusDropdown(
                    currentStatus = status,
                    lang = lang,
                    isOverdue = item.isOverdue,
                    testTag = "roadmap_status_${ch.id}",
                    onStatusSelected = onStatusSelected
                )
            }
        }
    }
}
