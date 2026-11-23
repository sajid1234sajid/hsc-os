package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.ChapterStatus
import com.example.data.SessionTimerState
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.TimerPhase
import com.example.ui.components.ChapterStatusDropdown
import com.example.ui.components.StudySegmentedPhaseBar
import com.example.ui.components.subjectAccentColor
import com.example.ui.viewmodel.StudyUiState

@Composable
fun TodayScreen(
    state: StudyUiState,
    onToggleTimer: (Int) -> Unit,
    onResetTimer: (Int) -> Unit,
    onToggleSlotDone: (Int, Boolean) -> Unit,
    onStartBreak: (Int) -> Unit,
    onStopBreak: () -> Unit,
    onUpdateChapterStatus: (String, ChapterStatus) -> Unit,
    onNavigateToRoadmap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.settings.appLanguage()
    val completedCount = state.todaySessions.count { it.isDoneToday }
    val totalCount = state.todaySessions.size.coerceAtLeast(1)
    val dailyFraction = completedCount.toFloat() / totalCount.toFloat()
    val (p1Min, p2Min, p3Min) = StudyDateUtils.phaseDurationsMinutes(state.settings.dailyHours)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopLayout = maxWidth >= 840.dp

        if (isDesktopLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Left Primary Workspace: 2x2 Grid of 4 Sessions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (StudyDateUtils.dayKeyForRoutine(state.todayIso) == "FRIDAY") {
                            StringsProvider.text(lang, "শুক্রবার — সাপ্তাহিক পরীক্ষা ও রিভিশন সেশন", "Friday — Weekly Exam & Revision")
                        } else {
                            StringsProvider.text(lang, "আজকের ৪টি স্টাডি সেশন", "Today's 4 Study Sessions")
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 330.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.todaySessions, key = { it.slot }) { session ->
                            SessionTimerCard(
                                session = session,
                                dailyHours = state.settings.dailyHours,
                                lang = lang,
                                onToggleTimer = { onToggleTimer(session.slot) },
                                onResetTimer = { onResetTimer(session.slot) },
                                onToggleDone = { done -> onToggleSlotDone(session.slot, done) },
                                onUpdateChapterStatus = onUpdateChapterStatus
                            )
                        }
                    }
                }

                // Right Supporting Pane: Hero + Rollover + Break Timer
                LazyColumn(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TodayHeroCard(
                            state = state,
                            lang = lang,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            dailyFraction = dailyFraction,
                            phaseTuple = Triple(p1Min, p2Min, p3Min)
                        )
                    }
                    if (state.rolloverCount > 0) {
                        item {
                            RolloverAlertCard(
                                rolloverCount = state.rolloverCount,
                                lang = lang,
                                onClick = onNavigateToRoadmap
                            )
                        }
                    }
                    item {
                        BreakHelperCard(
                            breakRemainingSeconds = state.breakRemainingSeconds,
                            breakRunning = state.breakRunning,
                            lang = lang,
                            onStartBreak = onStartBreak,
                            onStopBreak = onStopBreak
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TodayHeroCard(
                            state = state,
                            lang = lang,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            dailyFraction = dailyFraction,
                            phaseTuple = Triple(p1Min, p2Min, p3Min)
                        )
                    }

                    if (state.rolloverCount > 0) {
                        item {
                            RolloverAlertCard(
                                rolloverCount = state.rolloverCount,
                                lang = lang,
                                onClick = onNavigateToRoadmap
                            )
                        }
                    }

                    item {
                        BreakHelperCard(
                            breakRemainingSeconds = state.breakRemainingSeconds,
                            breakRunning = state.breakRunning,
                            lang = lang,
                            onStartBreak = onStartBreak,
                            onStopBreak = onStopBreak
                        )
                    }

                    item {
                        Text(
                            text = if (StudyDateUtils.dayKeyForRoutine(state.todayIso) == "FRIDAY") {
                                StringsProvider.text(lang, "শুক্রবার — সাপ্তাহিক পরীক্ষা ও রিভিশন সেশন", "Friday — Weekly Exam & Revision")
                            } else {
                                StringsProvider.text(lang, "আজকের ৪টি স্টাডি সেশন", "Today's 4 Study Sessions")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(state.todaySessions, key = { it.slot }) { session ->
                        SessionTimerCard(
                            session = session,
                            dailyHours = state.settings.dailyHours,
                            lang = lang,
                            onToggleTimer = { onToggleTimer(session.slot) },
                            onResetTimer = { onResetTimer(session.slot) },
                            onToggleDone = { done -> onToggleSlotDone(session.slot, done) },
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
private fun RolloverAlertCard(
    rolloverCount: Int,
    lang: AppLanguage,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = StringsProvider.text(
                        lang,
                        "স্মার্ট রোলওভার: ${StudyDateUtils.toBnDigits(rolloverCount)}টি অধ্যায় বকেয়া আছে",
                        "Smart Rollover: $rolloverCount overdue chapters"
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = StringsProvider.text(
                        lang,
                        "কঠিন অধ্যায় বাদ না দিয়ে এই সপ্তাহে শেষ করুন। রোডম্যাপ দেখতে ট্যাপ করুন।",
                        "Finish rolled-over chapters this week without skipping. Tap to view Roadmap."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun TodayHeroCard(
    state: StudyUiState,
    lang: AppLanguage,
    completedCount: Int,
    totalCount: Int,
    dailyFraction: Float,
    phaseTuple: Triple<Int, Int, Int>
) {
    val (p1, p2, p3) = phaseTuple
    val totalSessionMin = p1 + p2 + p3
    Card(
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = StudyDateUtils.formatReadableDate(state.todayIso, lang),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "সপ্তাহ ${StudyDateUtils.toBnDigits(state.currentWeek)}/২৬",
                                "Week ${state.currentWeek}/26"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (state.isBeforePlanStart) {
                    Text(
                        text = StringsProvider.text(
                            lang,
                            "স্টাডি প্ল্যান ২৪ নভেম্বর ২০২৬ থেকে শুরু — আজকের রুটিন প্র্যাকটিস করতে পারেন।",
                            "Plan officially starts 24 Nov 2026 — you can start today's routine right away."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = StringsProvider.text(
                        lang,
                        "দৈনিক ${StudyDateUtils.toBnDigits(state.settings.dailyHours)} ঘণ্টা • ${StudyDateUtils.toBnDigits(totalSessionMin)} মি. × ৪ সেশন (${StudyDateUtils.toBnDigits(p1)}/${StudyDateUtils.toBnDigits(p2)}/${StudyDateUtils.toBnDigits(p3)})",
                        "Daily ${state.settings.dailyHours}h • $totalSessionMin min × 4 sessions ($p1/$p2/$p3)"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = StringsProvider.text(lang, "আজকের অগ্রগতি", "Daily Progress"),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${StringsProvider.num(completedCount, lang)}/${StringsProvider.num(totalCount, lang)} (${StringsProvider.num((dailyFraction * 100).toInt(), lang)}%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { dailyFraction },
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
private fun BreakHelperCard(
    breakRemainingSeconds: Int,
    breakRunning: Boolean,
    lang: AppLanguage,
    onStartBreak: (Int) -> Unit,
    onStopBreak: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Coffee,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (breakRunning) {
                            StringsProvider.text(
                                lang,
                                "বিরতি চলছে: ${StudyDateUtils.formatTimerMmSs(breakRemainingSeconds, lang)}",
                                "Break timer: ${StudyDateUtils.formatTimerMmSs(breakRemainingSeconds, lang)}"
                            )
                        } else {
                            StringsProvider.text(lang, "সেশনের মাঝে ১০–১৫ মি. বিরতি", "10–15 min break between sessions")
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (breakRunning) {
                OutlinedButton(
                    onClick = onStopBreak,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(StringsProvider.text(lang, "বন্ধ", "Stop"))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { onStartBreak(10) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(StringsProvider.text(lang, "১০ মি.", "10m"))
                    }
                    FilledTonalButton(
                        onClick = { onStartBreak(15) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(StringsProvider.text(lang, "১৫ মি.", "15m"))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTimerCard(
    session: SessionTimerState,
    dailyHours: Int,
    lang: AppLanguage,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onUpdateChapterStatus: (String, ChapterStatus) -> Unit
) {
    val accent = subjectAccentColor(session.group)
    val currentPhase = session.currentPhase(dailyHours)
    val (p1Min, p2Min, p3Min) = StudyDateUtils.phaseDurationsMinutes(dailyHours)
    val phaseHint = when (currentPhase) {
        TimerPhase.NEW_TOPIC -> StringsProvider.text(
            lang,
            "ধাপ ১/৩ (${StudyDateUtils.toBnDigits(p1Min)} মি.): নতুন টপিক বা অধ্যায় পড়া",
            "Phase 1/3 (${p1Min}m): Read new topic or chapter"
        )
        TimerPhase.PRACTICE -> StringsProvider.text(
            lang,
            "ধাপ ২/৩ (${StudyDateUtils.toBnDigits(p2Min)} মি.): অঙ্ক, MCQ, সৃজনশীল অনুশীলন",
            "Phase 2/3 (${p2Min}m): Math, MCQ, or CQ practice"
        )
        TimerPhase.RECALL -> StringsProvider.text(
            lang,
            "ধাপ ৩/৩ (${StudyDateUtils.toBnDigits(p3Min)} মি.): বই বন্ধ করে নিজের ভাষায় মনে করা",
            "Phase 3/3 (${p3Min}m): Closed-book active recall"
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (session.isRunning) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (session.isRunning) 1.5.dp else 1.dp,
            color = if (session.isRunning) accent else MaterialTheme.colorScheme.outlineVariant
        )
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = StringsProvider.num(session.slot, lang),
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (lang == AppLanguage.BN) session.subjectLabelBn else session.subjectLabelEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "সেশন ${StudyDateUtils.toBnDigits(session.slot)}",
                                "Session ${session.slot}"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = StringsProvider.text(lang, "সম্পন্ন", "Done"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (session.isDoneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Checkbox(
                        checked = session.isDoneToday,
                        onCheckedChange = onToggleDone,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("session_done_checkbox_${session.slot}")
                    )
                }
            }

            val ch = session.activeChapter
            if (ch != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (ch.isOverdue) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (ch.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (ch.isRolledOver) {
                                StringsProvider.text(
                                    lang,
                                    "রোলওভার (সপ্তাহ ${StudyDateUtils.toBnDigits(ch.chapter.weekNumber)} থেকে) • ${ch.chapter.paper}",
                                    "Rolled over (Wk ${ch.chapter.weekNumber}) • ${ch.chapter.paper}"
                                )
                            } else {
                                StringsProvider.text(
                                    lang,
                                    "সপ্তাহের নির্ধারিত পাঠ • ${ch.chapter.paper}",
                                    "Chapter of the week • ${ch.chapter.paper}"
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ch.isOverdue) MaterialTheme.colorScheme.error else accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = ch.chapter.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = StringsProvider.text(
                                    lang,
                                    "শেষ তারিখ: ${StudyDateUtils.formatShortDate(ch.chapter.deadline, lang)}",
                                    "Due: ${StudyDateUtils.formatShortDate(ch.chapter.deadline, lang)}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ChapterStatusDropdown(
                                currentStatus = ch.chapter.chapterStatus(),
                                lang = lang,
                                isOverdue = ch.isOverdue,
                                testTag = "today_status_dropdown_${session.slot}",
                                onStatusSelected = { status ->
                                    onUpdateChapterStatus(ch.chapter.id, status)
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = phaseHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StudySegmentedPhaseBar(
                elapsedSeconds = session.elapsedSeconds,
                dailyHours = dailyHours
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = StudyDateUtils.formatTimerMmSs(session.remainingSeconds, lang),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.isRunning) accent else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onResetTimer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("session_reset_button_${session.slot}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = StringsProvider.text(lang, "টাইমার রিসেট", "Reset timer")
                        )
                    }
                    Button(
                        onClick = onToggleTimer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (session.isRunning) MaterialTheme.colorScheme.secondary else accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("session_play_pause_${session.slot}")
                    ) {
                        Icon(
                            imageVector = if (session.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (session.isRunning) {
                                StringsProvider.text(lang, "বিরতি", "Pause")
                            } else {
                                StringsProvider.text(lang, "শুরু", "Start")
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = session.todayChaptersCount > 1) {
                Text(
                    text = StringsProvider.text(
                        lang,
                        "এই বিষয়ে এই সপ্তাহে আরও ${StudyDateUtils.toBnDigits(session.todayChaptersCount - 1)}টি পাঠ/বকেয়া রয়েছে (রোডম্যাপে দেখুন)।",
                        "${session.todayChaptersCount - 1} more chapters due for this subject this week."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
