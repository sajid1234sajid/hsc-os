package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.onGoogleSignInClicked
import com.example.data.AppLanguage
import com.example.data.StringsProvider
import com.example.data.StudyDateUtils
import com.example.data.UserChapter
import com.example.notifications.StudyNotificationHelper
import com.example.ui.components.AddChapterDialog
import com.example.ui.components.EditChapterDialog
import com.example.ui.viewmodel.StudyUiState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: StudyUiState,
    isSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String?,
    onSignInSuccess: () -> Unit,
    onSignOut: () -> Unit,
    onSetDailyHours: (Int) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onEditChapter: (String, String, String, Int) -> Unit,
    onAddChapter: (String, String, String, Int) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val lang = state.settings.appLanguage()
    val subjects = state.plan?.subjects.orEmpty()

    var selectedEditSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "english_1") }
    var editingChapter by remember { mutableStateOf<UserChapter?>(null) }
    var showAddChapter by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetNotificationsEnabled(true)
            statusNotice = StringsProvider.text(lang, "দৈনিক রিমাইন্ডার চালু হয়েছে", "Daily reminder enabled")
        } else {
            onSetNotificationsEnabled(false)
            statusNotice = StringsProvider.text(lang, "নোটিফিকেশন অনুমতি দেওয়া হয়নি", "Notification permission denied")
        }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = onExportJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    statusNotice = StringsProvider.text(lang, "ব্যাকআপ JSON সংরক্ষিত হয়েছে", "Backup JSON exported")
                }.onFailure {
                    statusNotice = StringsProvider.text(lang, "ব্যাকআপ সেভ করতে ব্যর্থ হয়েছে", "Failed to export backup")
                }
            }
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) {
                    onImportJson(text)
                }
            }.onFailure {
                statusNotice = StringsProvider.text(lang, "ফাইল পড়তে ব্যর্থ হয়েছে", "Failed to read file")
            }
        }
    }

    val editableChapters = remember(state.chaptersWithRollover, selectedEditSubjectId) {
        state.chaptersWithRollover
            .filter { it.chapter.subjectId == selectedEditSubjectId }
            .map { it.chapter }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statusNotice?.let { notice ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // Google Account & Optional Cloud Sync
            item {
                SettingsSectionCard(
                    title = StringsProvider.text(
                        lang,
                        "ক্লাউড সিঙ্ক ও Google অ্যাকাউন্ট (ঐচ্ছিক)",
                        "Cloud Sync & Google Account (Optional)"
                    )
                ) {
                    if (!isSignedIn) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = StringsProvider.text(
                                        lang,
                                        "বর্তমানে অফলাইন লোকাল মোডে চলছে — লগইন ছাড়াই সব পড়া ও রুটিন এই ডিভাইসে সেভ থাকছে। অন্য ডিভাইস বা মোবাইলে সিঙ্ক করতে চাইলে Google দিয়ে সাইন-ইন করুন।",
                                        "Running in offline local mode — all progress saves on this device without login. Sign in with Google anytime to sync across devices."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (!isSigningIn) {
                                    isSigningIn = true
                                    statusNotice = null
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            statusNotice = StringsProvider.text(
                                                lang,
                                                "Google দিয়ে সাইন-ইন সফল হয়েছে! অগ্রগতি ক্লাউডে সিঙ্ক হচ্ছে।",
                                                "Signed in with Google! Your progress is syncing to the cloud."
                                            )
                                            onSignInSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            statusNotice = msg
                                        },
                                        onAuthCancelled = {
                                            isSigningIn = false
                                        },
                                        scope = scope
                                    )
                                }
                            },
                            enabled = !isSigningIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("google_sign_in_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(StringsProvider.text(lang, "সাইন-ইন হচ্ছে...", "Signing in..."))
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    StringsProvider.text(
                                        lang,
                                        "Google দিয়ে ক্লাউড সিঙ্ক চালু করুন",
                                        "Sign in with Google for Cloud Sync"
                                    )
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName.ifBlank { StringsProvider.text(lang, "শিক্ষার্থী", "Student") },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val syncIcon = when {
                            state.hasPendingWrites -> Icons.Filled.Sync
                            state.isFromCache -> Icons.Filled.CloudOff
                            else -> Icons.Filled.CloudDone
                        }
                        val syncText = when {
                            state.hasPendingWrites -> StringsProvider.text(
                                lang,
                                "অফলাইন পরিবর্তন সেভড — Wi-Fi এলে অটো আপডেট হবে",
                                "Offline changes saved — will auto-sync when online"
                            )
                            state.isFromCache -> StringsProvider.text(
                                lang,
                                "অফলাইন ক্যাশ মোড চালু — Wi-Fi এলে সিঙ্ক হবে",
                                "Offline cache active — auto-syncs on Wi-Fi"
                            )
                            else -> StringsProvider.text(
                                lang,
                                "ক্লাউড ডাটাবেসের সাথে লাইভ সিঙ্কড",
                                "Synced live with Cloud Firestore"
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.hasPendingWrites || state.isFromCache) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = syncIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = syncText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(StringsProvider.text(lang, "সাইন-আউট করুন (লোকাল মোডে থাকুন)", "Sign Out (Keep Local Mode)"))
                        }
                    }
                }
            }

            // Daily Study Hours
            item {
                SettingsSectionCard(title = StringsProvider.text(lang, "দৈনিক পড়ার সময়", "Daily Study Hours")) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.settings.dailyHours == 5,
                            onClick = { onSetDailyHours(5) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("hours_5_button")
                        ) {
                            Text(StringsProvider.text(lang, "৫ ঘণ্টা (৭৫ মি. × ৪)", "5h (75m × 4)"))
                        }
                        SegmentedButton(
                            selected = state.settings.dailyHours == 3,
                            onClick = { onSetDailyHours(3) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("hours_3_button")
                        ) {
                            Text(StringsProvider.text(lang, "৩ ঘণ্টা (৪৫ মি. × ৪)", "3h (45m × 4)"))
                        }
                    }
                }
            }

            // Language & Theme
            item {
                SettingsSectionCard(title = StringsProvider.text(lang, "ভাষা ও থিম", "Language & Theme")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(StringsProvider.text(lang, "ভাষা", "Language"), style = MaterialTheme.typography.bodyLarge)
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = lang == AppLanguage.BN,
                                onClick = { onSetLanguage(AppLanguage.BN) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("বাংলা")
                            }
                            SegmentedButton(
                                selected = lang == AppLanguage.EN,
                                onClick = { onSetLanguage(AppLanguage.EN) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("English")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(StringsProvider.text(lang, "ডার্ক মোড", "Dark Mode"), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.settings.darkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }

            // Notifications
            item {
                SettingsSectionCard(title = StringsProvider.text(lang, "রিমাইন্ডার ও নোটিফিকেশন", "Reminders & Notifications")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = StringsProvider.text(lang, "দৈনিক পড়ার রিমাইন্ডার", "Daily Study Reminder"),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = StringsProvider.text(
                                    lang,
                                    "প্রতিদিন সন্ধ্যা ৬:০০টায় ও সেশন ধাপ শেষে অ্যালার্ট",
                                    "Daily 6:00 PM reminder & session phase alerts"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !StudyNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSetNotificationsEnabled(enable)
                                }
                            },
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !StudyNotificationHelper.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSendTestNotification()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(StringsProvider.text(lang, "টেস্ট নোটিফিকেশন পাঠান", "Send Test Notification"))
                    }
                }
            }

            // Backup & Reset
            item {
                SettingsSectionCard(title = StringsProvider.text(lang, "ব্যাকআপ ও রিসেট (JSON)", "Backup & Reset (JSON)")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportDocumentLauncher.launch("hsc_study_backup_${state.todayIso}.json") },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_json_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(StringsProvider.text(lang, "এক্সপোর্ট", "Export"))
                        }
                        OutlinedButton(
                            onClick = { importDocumentLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("import_json_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(StringsProvider.text(lang, "ইমপোর্ট", "Import"))
                        }
                    }

                    Button(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("reset_all_button")
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(StringsProvider.text(lang, "সব অগ্রগতি রিসেট করুন", "Reset All Progress"))
                    }
                }
            }

            // Editable Syllabus Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = StringsProvider.text(lang, "অধ্যায় তালিকা ও ডেডলাইন এডিট", "Edit Chapters & Deadlines"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StringsProvider.text(
                                lang,
                                "২০২৮ বোর্ড সিলেবাস বা ইংরেজি ইউনিট অনুযায়ী পরিবর্তন করুন",
                                "Customize for official 2028 syllabus or English units"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddChapter = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(StringsProvider.text(lang, "যোগ", "Add"))
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjects, key = { it.id }) { subj ->
                        FilterChip(
                            selected = selectedEditSubjectId == subj.id,
                            onClick = { selectedEditSubjectId = subj.id },
                            label = { Text(if (lang == AppLanguage.BN) subj.nameBn else subj.nameEn) }
                        )
                    }
                }
            }

            items(editableChapters, key = { it.id }) { chapter ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${chapter.paper} • ${StringsProvider.text(lang, "সপ্তাহ ${StudyDateUtils.toBnDigits(chapter.weekNumber)}", "Wk ${chapter.weekNumber}")} • ${chapter.deadline}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(
                                onClick = { editingChapter = chapter },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = StringsProvider.text(lang, "এডিট", "Edit")
                                )
                            }
                            IconButton(
                                onClick = { onDeleteChapter(chapter.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = StringsProvider.text(lang, "মুছুন", "Delete"),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
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

    if (showAddChapter) {
        AddChapterDialog(
            subjects = subjects,
            lang = lang,
            onDismiss = { showAddChapter = false },
            onAdd = { subjId, paper, title, wk ->
                onAddChapter(subjId, paper, title, wk)
                showAddChapter = false
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(StringsProvider.text(lang, "সব ডেটা রিসেট করবেন?", "Reset all data?")) },
            text = {
                Text(
                    StringsProvider.text(
                        lang,
                        "আপনার সব অধ্যায়ের অগ্রগতি, সাপ্তাহিক চেকলিস্ট ও ভুলের খাতা মুছে ডিফল্ট রোডম্যাপ ফিরে আসবে।",
                        "All chapter statuses, weekly checklists, and Mistake Notebook entries will be cleared."
                    )
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onResetAll()
                        showResetConfirm = false
                    }
                ) {
                    Text(StringsProvider.text(lang, "হ্যাঁ, রিসেট করুন", "Yes, Reset"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(StringsProvider.text(lang, "বাতিল", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}
