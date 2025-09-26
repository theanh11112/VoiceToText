package com.voicetotextapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class SpeechModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getName(): String = "SpeechModule"

    @ReactMethod
    fun startListening() {
        val context = reactApplicationContext
        Log.d("SpeechModule", "isRecognitionAvailable: ${SpeechRecognizer.isRecognitionAvailable(context)}")

        if (listening) {
            Log.d("SpeechModule", "startListening: đang nghe rồi, bỏ qua")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechModule", "Speech recognition không khả dụng trên thiết bị")
            sendEvent("onSpeechError", Arguments.createMap().apply {
                putString("message", "Speech recognition not available")
            })
            return
        }

        mainHandler.post {
            Log.d("SpeechModule", "Tạo SpeechRecognizer instance...")
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Log.d("SpeechModule", "SpeechRecognizer instance tạo thành công")

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { Log.d("SpeechModule", "onReadyForSpeech") }
                override fun onBeginningOfSpeech() { Log.d("SpeechModule", "onBeginningOfSpeech") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { Log.d("SpeechModule", "onEndOfSpeech") }

                override fun onError(error: Int) {
                    Log.e("SpeechModule", "onError: $error")
                    sendEvent("onSpeechError", Arguments.createMap().apply { putInt("code", error) })
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

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            }

            Log.d("SpeechModule", "Bắt đầu startListening...")
            speechRecognizer?.startListening(intent)
            listening = true
            sendEvent("onSpeechStarted", null)
        }
    }

    @ReactMethod
    fun stopListening() {
        mainHandler.post {
            Log.d("SpeechModule", "stopListening called")
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            listening = false
            sendEvent("onSpeechStopped", null)
            Log.d("SpeechModule", "stopListening finished")
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } catch (e: Exception) {
            Log.e("SpeechModule", "sendEvent lỗi: ${e.message}")
        }
    }

    // 🔹 BẮT BUỘC phải có cho NativeEventEmitter
    @ReactMethod
    fun addListener(eventName: String) {
        // no-op
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // no-op
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.d("SpeechModule", "onCatalystInstanceDestroy called")
        speechRecognizer?.destroy()
    }
}
