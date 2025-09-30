package com.voicetotextapp

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONArray
import org.json.JSONObject

class SmsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    private var isReactReady = false
    private var isListenerReady = false

    companion object {
        private var staticReactContext: ReactApplicationContext? = null

        // Cho SmsReceiver gọi khi RN context chưa sẵn sàng
        fun enqueueSmsStatic(from: String, body: String) {
            staticReactContext?.getNativeModule(SmsModule::class.java)?.enqueueSms(from, body)
                ?: run {
                    // Lưu tạm trong SharedPreferences
                    val prefs = staticReactContext?.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
                    prefs?.let {
                        val json = it.getString("sms_list", "[]")
                        val list = JSONArray(json)
                        val obj = JSONObject().apply {
                            put("from", from)
                            put("body", body)
                            put("emitted", false)
                        }
                        list.put(obj)
                        it.edit().putString("sms_list", list.toString()).apply()
                        Log.d("SmsModule", "📥 enqueueSmsStatic: $from → $body")
                    }
                }
        }
    }

    override fun getName(): String = "SmsModule"

    override fun initialize() {
        super.initialize()
        reactContext.addLifecycleEventListener(this)
        staticReactContext = reactContext
        isReactReady = reactContext.hasActiveCatalystInstance()
        Log.d("SmsModule", "📱 initialize: isReactReady=$isReactReady")
        flushCachedSmsToJS()
    }

    fun setListenerReady() {
        isListenerReady = true
        flushCachedSmsToJS()
    }

    fun enqueueSms(from: String, body: String) {
        val obj = JSONObject().apply {
            put("from", from)
            put("body", body)
            put("emitted", false)
        }

        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        list.put(obj)
        prefs.edit().putString("sms_list", list.toString()).apply()
        Log.d("SmsModule", "📥 enqueueSms: $from → $body | isReactReady=$isReactReady | listenerReady=$isListenerReady")

        if (isReactReady && isListenerReady) {
            flushCachedSmsToJS()
        }
    }

    private fun flushCachedSmsToJS() {
        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        var changed = false

        for (i in 0 until list.length()) {
            val sms = list.getJSONObject(i)
            if (!sms.optBoolean("emitted", false)) {
                emitSms(sms.getString("from"), sms.getString("body"))
                sms.put("emitted", true)
                changed = true
            }
        }

        if (changed) {
            prefs.edit().putString("sms_list", list.toString()).apply()
        }
        Log.d("SmsModule", "flushCachedSmsToJS: ${list.length()} SMS trong cache")
    }

    private fun emitSms(from: String, body: String) {
        val params = Arguments.createMap().apply {
            putString("from", from)
            putString("body", body)
        }
        reactContext.runOnUiQueueThread {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onSmsReceived", params)
        }
        Log.d("SmsModule", "📤 Emit SMS: $from → $body")
    }

    @ReactMethod
    fun flushCachedSmsToJSForJS() {
        setListenerReady()
    }

    override fun onHostResume() {
        isReactReady = true
        flushCachedSmsToJS()
    }

    override fun onHostPause() {}
    override fun onHostDestroy() {}
}
