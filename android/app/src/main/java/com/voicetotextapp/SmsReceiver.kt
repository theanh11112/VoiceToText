package com.voicetotextapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import com.facebook.react.ReactApplication

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            val sms = (pdu as? ByteArray)?.let { SmsMessage.createFromPdu(it, format) } ?: continue
            val from = sms.originatingAddress ?: ""
            val body = sms.messageBody ?: ""

            Log.d("SmsReceiver", "📩 Nhận SMS từ: $from, nội dung: $body")

            // Gọi enqueue tĩnh, đảm bảo luôn lưu cache
            SmsModule.enqueueSmsStatic(from, body)
        }
    }
}
