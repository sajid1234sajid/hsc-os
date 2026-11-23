package com.example.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppLanguage
import com.example.data.StringsProvider
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "AiChatViewModel"

enum class TutorRole(
    val modelId: String,
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val subtitleEn: String,
    val systemPrompt: String
) {
    GENERAL_TUTOR(
        modelId = "gemini-4-hydrogen",
        titleBn = "সাধারণ স্টাডি টিউটর",
        titleEn = "General Study Tutor",
        subtitleBn = "ধারণা, বাংলা, ইংরেজি ও ICT সহায়তা",
        subtitleEn = "Concepts, Bangla, English & ICT",
        systemPrompt = "You are a warm, knowledgeable HSC 1st-year Science tutor in Bangladesh (HSC 2028 batch). " +
            "Explain concepts clearly in natural Bangla (or English if the student writes in English), align with NCTB textbooks, " +
            "and support the student's 26-week 50/20/5 study routine. Keep formatting clean and easy to read on mobile and desktop."
    ),
    DEEP_REASONING(
        modelId = "gemini-4-carbon",
        titleBn = "গভীর বিশ্লেষক (গণিত ও পদার্থ)",
        titleEn = "Deep Math & Physics",
        subtitleBn = "ধাপে ধাপে অঙ্ক, প্রমাণ ও বিশ্লেষণ",
        subtitleEn = "Step-by-step proofs & derivations",
        systemPrompt = "You are a senior HSC Physics, Higher Math, and Chemistry professor in Bangladesh. " +
            "Show complete step-by-step mathematical derivations, formulas, SI units, and conceptual reasoning. " +
            "Highlight common board-exam mistakes and explain why each step works. Answer in clear Bangla unless asked in English."
    ),
    INTERACTIVE_COACH(
        modelId = "gemini-4-neon",
        titleBn = "সৃজনশীল ও পরীক্ষা কোচ",
        titleEn = "CQ & Exam Coach",
        subtitleBn = "ইন্টারেক্টিভ কুইজ, CQ গাইড ও মক ভাইভা",
        subtitleEn = "Interactive quizzes, CQ & exam prep",
        systemPrompt = "You are an interactive HSC Exam & Creative Question (CQ) Coach for Bangladesh Science students. " +
            "When quizzing, ask ONE focused MCQ or CQ sub-question (ক/খ/গ/ঘ) at a time, wait for the student's response, " +
            "then evaluate, give hints, and suggest what to write in their Mistake Notebook if they err. Respond in Bangla by default."
    ),
    FAST_RECALL(
        modelId = "gemini-3.1-flash-lite",
        titleBn = "দ্রুত ফ্ল্যাশ-রিকল",
        titleEn = "Fast Flash Recall",
        subtitleBn = "সংজ্ঞা, সূত্র ও ৫ মিনিটের রিভিশন",
        subtitleEn = "Instant definitions & 5-min recall",
        systemPrompt = "You are a fast HSC Science quick-recall assistant for the 5-minute closed-book recall phase. " +
            "Provide crisp, high-yield bullet points: key definitions, formulas, mnemonics, and 3 quick recall questions. " +
            "Be concise and accurate. Respond in Bangla by default."
    );

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.BN) titleBn else titleEn
    fun subtitle(lang: AppLanguage): String = if (lang == AppLanguage.BN) subtitleBn else subtitleEn
}

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val roleUsed: TutorRole,
    val isStreaming: Boolean = false
)

sealed interface ChatSendState {
    data object Idle : ChatSendState
    data object Loading : ChatSendState
    data object Streaming : ChatSendState
    data class Error(val message: String, val failedPrompt: String? = null) : ChatSendState
}

data class AiChatUiState(
    val selectedRole: TutorRole = TutorRole.GENERAL_TUTOR,
    val messagesByRole: Map<TutorRole, List<ChatMessageItem>> = emptyMap(),
    val sendState: ChatSendState = ChatSendState.Idle,
    val studyContextSummary: String = ""
) {
    val activeMessages: List<ChatMessageItem>
        get() = messagesByRole[selectedRole].orEmpty()
}

class AiChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    // Lazy models keyed by TutorRole so Firebase.ai is only evaluated upon first use
    private val modelsByRole: Map<TutorRole, Lazy<GenerativeModel>> = TutorRole.entries.associateWith { role ->
        lazy {
            Firebase.ai.generativeModel(
                modelName = role.modelId,
                systemInstruction = content {
                    text(role.systemPrompt)
                }
            )
        }
    }

    private val chatsByRole = mutableMapOf<TutorRole, Chat>()

    fun updateStudyContext(week: Int, todaySubjects: List<String>, activeChapters: List<String>) {
        val summary = buildString {
            append("Student context — Week $week/26")
            if (todaySubjects.isNotEmpty()) {
                append(", Today's subjects: ${todaySubjects.joinToString(", ")}")
            }
            if (activeChapters.isNotEmpty()) {
                append(", Active chapters: ${activeChapters.joinToString("; ")}")
            }
        }
        _uiState.value = _uiState.value.copy(studyContextSummary = summary)
    }

    fun selectRole(role: TutorRole) {
        _uiState.value = _uiState.value.copy(
            selectedRole = role,
            sendState = ChatSendState.Idle
        )
    }

    fun clearCurrentChat() {
        val role = _uiState.value.selectedRole
        chatsByRole.remove(role)
        val updated = _uiState.value.messagesByRole.toMutableMap().apply {
            put(role, emptyList())
        }
        _uiState.value = _uiState.value.copy(
            messagesByRole = updated,
            sendState = ChatSendState.Idle
        )
    }

    fun sendMessage(rawPrompt: String, lang: AppLanguage) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        if (_uiState.value.sendState == ChatSendState.Loading || _uiState.value.sendState == ChatSendState.Streaming) {
            return
        }

        val role = _uiState.value.selectedRole
        val userMessage = ChatMessageItem(
            isUser = true,
            text = prompt,
            roleUsed = role
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val currentList = _uiState.value.messagesByRole[role].orEmpty()
        val withUser = currentList + userMessage

        updateRoleMessages(role, withUser)
        _uiState.value = _uiState.value.copy(sendState = ChatSendState.Loading)

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val chat = chatsByRole.getOrPut(role) {
                    modelsByRole.getValue(role).value.startChat()
                }
                val isFirstTurn = currentList.isEmpty()
                val contextPrefix = if (isFirstTurn && _uiState.value.studyContextSummary.isNotBlank()) {
                    "[${_uiState.value.studyContextSummary}]\n\n"
                } else {
                    ""
                }

                withTimeout(60_000L) {
                    chat.sendMessageStream(contextPrefix + prompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            _uiState.value = _uiState.value.copy(sendState = ChatSendState.Streaming)
                            val streamingMsg = ChatMessageItem(
                                id = assistantMsgId,
                                isUser = false,
                                text = accumulated.toString(),
                                roleUsed = role,
                                isStreaming = true
                            )
                            updateRoleMessages(role, withUser + streamingMsg)
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    val finalMsg = ChatMessageItem(
                        id = assistantMsgId,
                        isUser = false,
                        text = finalText,
                        roleUsed = role,
                        isStreaming = false
                    )
                    updateRoleMessages(role, withUser + finalMsg)
                    _uiState.value = _uiState.value.copy(sendState = ChatSendState.Idle)
                } else {
                    _uiState.value = _uiState.value.copy(
                        sendState = ChatSendState.Error(
                            message = StringsProvider.text(
                                lang,
                                "মডেল কোনো উত্তর দেয়নি। আবার চেষ্টা করুন।",
                                "Model returned an empty response. Please try again."
                            ),
                            failedPrompt = prompt
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat request timed out", e)
                _uiState.value = _uiState.value.copy(
                    sendState = ChatSendState.Error(
                        message = StringsProvider.text(
                            lang,
                            "৬০ সেকেন্ডে উত্তর পাওয়া যায়নি (টাইমআউট)। নেটওয়ার্ক যাচাই করে আবার চেষ্টা করুন।",
                            "Request timed out after 60 seconds. Check your connection and retry."
                        ),
                        failedPrompt = prompt
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat generation failed", e)
                _uiState.value = _uiState.value.copy(
                    sendState = ChatSendState.Error(
                        message = e.localizedMessage ?: StringsProvider.text(
                            lang,
                            "উত্তর তৈরি করতে সমস্যা হয়েছে।",
                            "Failed to generate response."
                        ),
                        failedPrompt = prompt
                    )
                )
            }
        }
    }

    private fun updateRoleMessages(role: TutorRole, messages: List<ChatMessageItem>) {
        val updated = _uiState.value.messagesByRole.toMutableMap().apply {
            put(role, messages)
        }
        _uiState.value = _uiState.value.copy(messagesByRole = updated)
    }
}
