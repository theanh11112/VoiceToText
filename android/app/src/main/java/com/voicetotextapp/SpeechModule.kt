package com.voicetotextapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class SpeechModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getName(): String = "SpeechModule"

    // ---- Public API ----
    @ReactMethod
    fun startListening() {
        val context = reactApplicationContext

        // Kiểm tra quyền mic
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendEvent("onSpeechError", Arguments.createMap().apply {
                putString("message", "Microphone permission not granted")
            })
            return
        }

        // Kiểm tra khả năng nhận dạng
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            sendEvent("onSpeechError", Arguments.createMap().apply {
                putString("message", "Speech recognition not available")
            })
            return
        }

        if (listening) {
            Log.d("SpeechModule", "Đang nghe rồi, bỏ qua")
            return
        }

        mainHandler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechModule", "onReadyForSpeech")
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechModule", "onBeginningOfSpeech")
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        Log.d("SpeechModule", "onRmsChanged: $rmsdB")
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("SpeechModule", "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        val msg = errorMessage(error)
                        Log.e("SpeechModule", "onError: $error ($msg)")
                        sendEvent("onSpeechError", Arguments.createMap().apply {
                            putInt("code", error)
                            putString("message", msg)
                        })
                        listening = false
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.getOrNull(0) ?: ""
                        Log.d("SpeechModule", "onResults: $text")
                        sendEvent("onSpeechResult", Arguments.createMap().apply { putString("text", text) })
                        listening = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.getOrNull(0) ?: ""
                        Log.d("SpeechModule", "onPartialResults: $text")
                        sendEvent("onSpeechPartial", Arguments.createMap().apply { putString("text", text) })
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            listening = true
            sendEvent("onSpeechStarted", null)
        }
    }

    @ReactMethod
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}

            mainHandler.postDelayed({
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null
            }, 1500) // tăng delay an toàn

            listening = false
            sendEvent("onSpeechStopped", null)
        }
    }

    // ---- Helpers ----
    private fun sendEvent(eventName: String, params: WritableMap?) {
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } catch (e: Exception) {
            Log.e("SpeechModule", "sendEvent error: ${e.message}")
        }
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Unknown error"
    }

    // NativeEventEmitter yêu cầu
    @ReactMethod fun addListener(eventName: String) {}
    @ReactMethod fun removeListeners(count: Int) {}

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            listening = false
        }
    }
}
