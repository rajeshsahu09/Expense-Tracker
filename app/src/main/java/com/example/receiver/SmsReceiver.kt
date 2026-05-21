package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ExpenseEntity
import com.example.ml.OnDeviceProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val extras = intent.extras ?: return
        try {
            val pdus = extras.get("pdus") as? Array<*> ?: return
            val format = extras.getString("format")
            
            for (pdu in pdus) {
                val smsMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                } ?: continue

                val sender = smsMessage.originatingAddress ?: "Unknown"
                val body = smsMessage.messageBody ?: continue

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val processor = OnDeviceProcessor()
                        val parsed = processor.processText(body)
                        if (parsed.amount > 0.0) {
                            val db = AppDatabase.getDatabase(context)
                            db.expenseDao().insertExpense(
                                ExpenseEntity(
                                    amount = parsed.amount,
                                    description = if (parsed.description == "Generic Transaction" || parsed.description == "Generic Expenses" || parsed.description == "Generic Transaction") {
                                        "SMS from $sender"
                                    } else {
                                        parsed.description
                                    },
                                    category = parsed.category,
                                    paymentMethod = parsed.paymentMethod,
                                    dateMillis = parsed.dateMillis,
                                    note = "Auto-parsed SMS from $sender"
                                )
                            )
                            Log.d("SmsReceiver", "Automatically saved on-device SMS expense: $${parsed.amount}")
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Failed parsing incoming background SMS: ${e.localizedMessage}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "SMS PDU format mismatch: ${e.localizedMessage}")
        }
    }
}
