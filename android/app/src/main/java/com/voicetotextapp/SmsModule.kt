// SmsModule.kt
package com.voicetotextapp

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONArray
import org.json.JSONObject

class SmsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    private val smsQueue = mutableListOf<JSONObject>()
    private var isReactReady = false

    override fun getName(): String = "SmsModule"

    override fun initialize() {
        super.initialize()
        reactContext.addLifecycleEventListener(this)
        isReactReady = reactContext.hasActiveCatalystInstance()
        if (isReactReady) flushQueue()
    }

    private fun flushQueue() {
        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        var changed = false

        // Chỉ emit SMS chưa emit
        for (i in 0 until list.length()) {
            val obj = list.getJSONObject(i)
            if (!obj.optBoolean("emitted", false)) {
                emitSms(obj.getString("from"), obj.getString("body"))
                obj.put("emitted", true)
                changed = true
            }
        }

        // Cập nhật trạng thái đã emit
        if (changed) {
            prefs.edit().putString("sms_list", list.toString()).apply()
        }

        // Flush queue cục bộ
        for (sms in smsQueue) {
            emitSms(sms.getString("from"), sms.getString("body"))
        }
        smsQueue.clear()
    }

    fun enqueueSms(from: String, body: String) {
        val obj = JSONObject().apply {
            put("from", from)
            put("body", body)
            put("emitted", false)
        }

        // Lưu SharedPreferences
        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        list.put(obj)
        prefs.edit().putString("sms_list", list.toString()).apply()

        // Nếu RN chưa sẵn sàng, thêm vào queue cục bộ
        if (isReactReady) {
            emitSms(from, body)
            obj.put("emitted", true)
            prefs.edit().putString("sms_list", list.toString()).apply()
        } else {
            smsQueue.add(obj)
        }
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
        Log.d("SmsModule", "Emit SMS: $from → $body")
    }

    override fun onHostResume() {
        isReactReady = true
        flushQueue()
    }
    override fun onHostPause() { }
    override fun onHostDestroy() { }

    @ReactMethod
    fun getLastSms(promise: Promise) {
        val prefs = reactContext.getSharedPreferences("sms_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("sms_list", "[]")
        val list = JSONArray(json)
        if (list.length() > 0) {
            val last = list.getJSONObject(list.length() - 1)
            val map = Arguments.createMap().apply {
                putString("from", last.getString("from"))
                putString("body", last.getString("body"))
            }
            promise.resolve(map)
        } else {
            promise.resolve(null)
        }
    }
}
