package com.voicetotextapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return
        val format = bundle.getString("format")

        Log.d("SmsReceiver", "Thread khi nhận SMS: ${Thread.currentThread().name}")
        Log.d("SmsReceiver", "Context: $context (ignore, dùng AppContextHolder)")

        for (pdu in pdus) {
            val sms = (pdu as? ByteArray)?.let { SmsMessage.createFromPdu(it, format) } ?: continue
            val from = sms.originatingAddress ?: ""
            val body = sms.messageBody ?: ""

            Log.d("SmsReceiver", "📩 Nhận SMS từ: $from, nội dung: $body")

            // ✅ Luôn dùng AppContextHolder để đảm bảo có ReactApplication
            SmsModule.enqueueSmsStatic(from, body, AppContextHolder.app)
        }
    }
}
