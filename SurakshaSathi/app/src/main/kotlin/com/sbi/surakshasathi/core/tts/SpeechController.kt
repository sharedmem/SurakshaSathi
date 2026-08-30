package com.sbi.surakshasathi.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class SpeechState { IDLE, SPEAKING, PAUSED }

/**
 * Thin wrapper around the OS-provided [TextToSpeech] engine — zero shipped audio, zero extra
 * dependency, "listen" mode for Advisory articles (§7c Phase 7). The platform API has no native
 * pause/resume, only speak/stop, so article bodies are split into sentence-level utterances and
 * [currentIndex] tracks progress via [UtteranceProgressListener.onDone]; pause stops playback
 * without resetting the index, resume re-queues from it.
 */
@Singleton
class SpeechController
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private var engine: TextToSpeech? = null
        private var engineReady = false
        private var sentences: List<String> = emptyList()
        private var currentIndex = 0

        private val _state = MutableStateFlow(SpeechState.IDLE)
        val state: StateFlow<SpeechState> = _state.asStateFlow()

        init {
            engine =
                TextToSpeech(context) { status -> engineReady = status == TextToSpeech.SUCCESS }.apply {
                    setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                currentIndex++
                                if (currentIndex >= sentences.size) {
                                    currentIndex = 0
                                    _state.value = SpeechState.IDLE
                                }
                            }

                            @Deprecated("Deprecated in TextToSpeech API, no replacement callback needed here")
                            override fun onError(utteranceId: String?) {
                                _state.value = SpeechState.IDLE
                            }
                        },
                    )
                }
        }

        /** Whether the device has a usable TTS voice for [languageTag] — gates showing Listen mode at all. */
        fun isLanguageAvailable(languageTag: String): Boolean {
            val result = engine?.isLanguageAvailable(Locale.forLanguageTag(languageTag)) ?: TextToSpeech.LANG_NOT_SUPPORTED
            return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        fun speak(
            text: String,
            languageTag: String,
        ) {
            val ttsEngine = engine ?: return
            if (!engineReady) return
            ttsEngine.language = Locale.forLanguageTag(languageTag)
            sentences = splitIntoSentences(text)
            currentIndex = 0
            enqueueFrom(0)
            _state.value = SpeechState.SPEAKING
        }

        fun pause() {
            engine?.stop()
            if (_state.value == SpeechState.SPEAKING) _state.value = SpeechState.PAUSED
        }

        fun resume() {
            if (sentences.isEmpty() || currentIndex >= sentences.size) return
            enqueueFrom(currentIndex)
            _state.value = SpeechState.SPEAKING
        }

        fun stop() {
            engine?.stop()
            sentences = emptyList()
            currentIndex = 0
            _state.value = SpeechState.IDLE
        }

        fun shutdown() {
            engine?.shutdown()
        }

        private fun enqueueFrom(startIndex: Int) {
            val ttsEngine = engine ?: return
            sentences.drop(startIndex).forEachIndexed { offset, sentence ->
                val queueMode = if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                ttsEngine.speak(sentence, queueMode, null, "advisory_${startIndex + offset}_${UUID.randomUUID()}")
            }
        }

        private fun splitIntoSentences(text: String): List<String> =
            text.split(Regex("(?<=[.।!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }
