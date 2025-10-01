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
        private const val TAG = "SmsModule"

        fun enqueueSmsStatic(from: String, body: String, appContext: Context? = null) {
            Log.d(TAG, "⚡ enqueueSmsStatic: context=${appContext?.javaClass?.name ?: "null"}")
            val app = AppContextHolder.app
            val reactHost = if (app is com.facebook.react.ReactApplication) app.reactHost else null
            val reactCtx = reactHost?.currentReactContext
            val isReady = reactCtx != null && reactCtx.hasActiveReactInstance()

            // Lấy module bằng class hoặc tên
            val module = reactCtx?.getNativeModule(SmsModule::class.java)
                ?: reactCtx?.getNativeModule("SmsModule") as? SmsModule

            Log.d(TAG, "👉 module null? ${module == null}, isReady=$isReady")

            if (isReady && module != null) {
               module.enqueueSms(from, body)
            } else {
                // fallback → cache và sẽ flush sau
                appContext?.let { ctx -> saveSmsToCache(ctx, from, body) }
                module?.flushCachedSmsToJS()
                Log.d(TAG, "📥 enqueueSmsStatic (cache only): $from → $body")
            }
        }


        private fun saveSmsToCache(context: Context, from: String, body: String) {
            val prefs = context.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("sms_list", "[]")
            val list = JSONArray(json)
            val obj = JSONObject().apply {
                put("from", from)
                put("body", body)
                put("emitted", false)
            }
            list.put(obj)
            prefs.edit().putString("sms_list", list.toString()).apply()
        }
    }

    override fun getName(): String = "SmsModule"

    override fun initialize() {
        super.initialize()
        reactContext.addLifecycleEventListener(this)
        isReactReady = reactContext.hasActiveReactInstance()
        Log.d(TAG, "📱 initialize: isReactReady=$isReactReady")
        flushCachedSmsToJS()
    }

    fun setListenerReady() {
        if (!isListenerReady) {
            Log.d(TAG, "✅ JS listener mounted, flush cache")
            isListenerReady = true
            flushCachedSmsToJS()
        }
    }

    fun enqueueSms(from: String, body: String) {
        saveSmsToCache(reactContext, from, body)
        Log.d(TAG, "📥 enqueueSms: $from → $body | isReactReady=$isReactReady | listenerReady=$isListenerReady")

        if (isReactReady && isListenerReady) {
            // Thay vì emit ngay -> flush cache để đồng bộ
            flushCachedSmsToJS()
        } else {
            Log.d(TAG, "⚠️ enqueueSms: chưa sẵn sàng, chỉ cache")
        }
    }

    private fun flushCachedSmsToJS() {
        if (!isReactReady || !isListenerReady) {
            Log.d(TAG, "⚠️ flushCachedSmsToJS bị hoãn: isReactReady=$isReactReady, isListenerReady=$isListenerReady")
            return
        }

        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        var changed = false
        var emittedCount = 0

        for (i in 0 until list.length()) {
            val sms = list.getJSONObject(i)
            if (!sms.optBoolean("emitted", false)) {
                emitSms(sms.getString("from"), sms.getString("body"))
                sms.put("emitted", true)
                changed = true
                emittedCount++
            }
        }

        if (changed) {
            prefs.edit().putString("sms_list", list.toString()).apply()
        }
        Log.d(TAG, "🚀 flushCachedSmsToJS: $emittedCount SMS vừa được emit (${list.length()} trong cache)")
    }

    private fun emitSms(from: String, body: String) {
        val params = Arguments.createMap().apply {
            putString("from", from)
            putString("body", body)
        }
        reactContext.runOnUiQueueThread {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onSmsReceived", params)
        }
        Log.d(TAG, "📤 Emit SMS: $from → $body")
    }

    @ReactMethod
    fun flushCachedSmsToJSForJS() {
        Log.d(TAG, "📌 JS gọi flushCachedSmsToJSForJS()")
        setListenerReady()
    }

    override fun onHostResume() {
        Log.d(TAG, "onHostResume called, reactContext=$reactContext")
        isReactReady = true
        flushCachedSmsToJS()
    }

    override fun onHostPause() {}
    override fun onHostDestroy() {}
}
