package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.ChapterStatus
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.ui.components.subjectAccentColor
import com.example.ui.viewmodel.StudyUiState
import com.example.ui.viewmodel.SubjectProgressSummary

@Composable
fun ProgressScreen(
    state: StudyUiState,
    modifier: Modifier = Modifier
) {
    val lang = state.settings.appLanguage()
    val totalChapters = state.chaptersWithRollover.size
    val completedChapters = state.chaptersWithRollover.count { it.chapter.chapterStatus().isFinishedForRollover() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopLayout = maxWidth >= 840.dp

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isDesktopLayout) 1120.dp else 640.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    if (isDesktopLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CountdownHeroCard(
                                state = state,
                                lang = lang,
                                completedChapters = completedChapters,
                                totalChapters = totalChapters,
                                modifier = Modifier.weight(1.4f)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StreakCard(
                                    icon = Icons.Filled.EmojiEvents,
                                    title = StringsProvider.text(lang, "সাপ্তাহিক স্ট্রিক (৮০%+)", "Weekly Streak (80%+)"),
                                    value = StringsProvider.text(
                                        lang,
                                        "${StudyDateUtils.toBnDigits(state.weeklyStreakCount)} সপ্তাহ",
                                        "${state.weeklyStreakCount} wks"
                                    ),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                StreakCard(
                                    icon = Icons.Filled.LocalFireDepartment,
                                    title = StringsProvider.text(lang, "দৈনিক স্টাডি স্ট্রিক", "Daily Study Streak"),
                                    value = StringsProvider.text(
                                        lang,
                                        "${StudyDateUtils.toBnDigits(state.dailyStreakCount)} দিন",
                                        "${state.dailyStreakCount} days"
                                    ),
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CountdownHeroCard(
                                state = state,
                                lang = lang,
                                completedChapters = completedChapters,
                                totalChapters = totalChapters,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StreakCard(
                                    icon = Icons.Filled.EmojiEvents,
                                    title = StringsProvider.text(lang, "সাপ্তাহিক স্ট্রিক (৮০%+)", "Weekly Streak (80%+)"),
                                    value = StringsProvider.text(
                                        lang,
                                        "${StudyDateUtils.toBnDigits(state.weeklyStreakCount)} সপ্তাহ",
                                        "${state.weeklyStreakCount} wks"
                                    ),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StreakCard(
                                    icon = Icons.Filled.LocalFireDepartment,
                                    title = StringsProvider.text(lang, "দৈনিক স্টাডি স্ট্রিক", "Daily Study Streak"),
                                    value = StringsProvider.text(
                                        lang,
                                        "${StudyDateUtils.toBnDigits(state.dailyStreakCount)} দিন",
                                        "${state.dailyStreakCount} days"
                                    ),
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = StringsProvider.text(lang, "বিষয়ভিত্তিক অগ্রগতি", "Progress by Subject"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isDesktopLayout) {
                    val chunked = state.subjectSummaries.chunked(2)
                    items(chunked) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SubjectProgressCard(
                                summary = pair[0],
                                lang = lang,
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                SubjectProgressCard(
                                    summary = pair[1],
                                    lang = lang,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(state.subjectSummaries, key = { it.subjectId }) { summary ->
                        SubjectProgressCard(
                            summary = summary,
                            lang = lang,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Text(
                        text = StringsProvider.text(lang, "অধ্যায়ের স্ট্যাটাস বিশ্লেষণ", "Status Breakdown"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChapterStatus.entries.forEach { st ->
                                val count = state.chaptersWithRollover.count { it.chapter.chapterStatus() == st }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = st.label(lang),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = StringsProvider.num(count, lang),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CountdownHeroCard(
    state: StudyUiState,
    lang: AppLanguage,
    completedChapters: Int,
    totalChapters: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "২৩ মে ২০২৭ পর্যন্ত কাউন্টডাউন",
                                "Countdown to 23 May 2027"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = StringsProvider.text(
                            lang,
                            "${StudyDateUtils.toBnDigits(state.daysRemaining)} দিন বাকি",
                            "${state.daysRemaining} days left"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = StringsProvider.text(lang, "সামগ্রিক সিলেবাস সম্পন্ন", "Overall Syllabus Progress"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${StringsProvider.num(completedChapters, lang)} / ${StringsProvider.num(totalChapters, lang)} ${StringsProvider.text(lang, "অধ্যায়", "chapters")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${StringsProvider.num(state.overallCompletionPercent, lang)}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { state.overallCompletionPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun StreakCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubjectProgressCard(
    summary: SubjectProgressSummary,
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    val accent = subjectAccentColor(summary.group)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == AppLanguage.BN) summary.nameBn else summary.nameEn,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${StringsProvider.num(summary.completedChapters, lang)}/${StringsProvider.num(summary.totalChapters, lang)} ${StringsProvider.text(lang, "সম্পন্ন", "done")}" +
                            if (summary.overdueChapters > 0) {
                                " • ${StringsProvider.num(summary.overdueChapters, lang)} ${StringsProvider.text(lang, "বকেয়া", "overdue")}"
                            } else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary.overdueChapters > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${StringsProvider.num(summary.percent, lang)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            LinearProgressIndicator(
                progress = { summary.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}
