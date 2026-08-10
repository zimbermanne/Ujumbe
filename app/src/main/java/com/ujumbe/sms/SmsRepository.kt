package com.ujumbe.sms

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val deletedRepository = DeletedRepository(context)

    suspend fun getConversations(): List<ConversationItem> = withContext(Dispatchers.IO) {
        val conversationsMap = LinkedHashMap<String, ConversationItem>()
        val unreadCountsMap = mutableMapOf<String, Int>()

        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use { c ->
                val threadIdIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = c.getColumnIndex(Telephony.Sms.READ)

                while (c.moveToNext()) {
                    val threadId = if (threadIdIdx != -1) c.getString(threadIdIdx) ?: "" else ""
                    if (threadId.isEmpty() || deletedRepository.isThreadDeleted(threadId)) {
                        continue
                    }

                    val readVal = if (readIdx != -1) c.getInt(readIdx) else 1
                    if (readVal == 0) {
                        unreadCountsMap[threadId] = (unreadCountsMap[threadId] ?: 0) + 1
                    }

                    if (!conversationsMap.containsKey(threadId)) {
                        val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                        val snippet = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L

                        conversationsMap[threadId] = ConversationItem(
                            threadId = threadId,
                            address = address,
                            snippet = snippet,
                            date = date,
                            unreadCount = 0
                        )
                    } else {
                        val existing = conversationsMap[threadId]!!
                        if (existing.address.isEmpty()) {
                            val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                            if (address.isNotEmpty()) {
                                conversationsMap[threadId] = existing.copy(address = address)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error reading conversations", e)
        }

        val result = conversationsMap.values.map { item ->
            val unread = unreadCountsMap[item.threadId] ?: 0
            item.copy(unreadCount = unread)
        }

        return@withContext result.sortedByDescending { it.date }
    }

    private fun getUnreadCount(threadId: String): Int {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID)
        val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
        val selectionArgs = arrayOf(threadId)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                return cursor.count
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting unread count", e)
        }
        return 0
    }

    suspend fun getMessagesForThread(threadId: String): List<SmsMessageItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SmsMessageItem>()
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId)

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                val threadIdIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getString(idIdx) else ""
                    if (deletedRepository.isMessageDeleted(id) || deletedRepository.isThreadDeleted(threadId)) {
                        continue
                    }

                    val tId = if (threadIdIdx != -1) c.getString(threadIdIdx) else ""
                    val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val type = if (typeIdx != -1) c.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                    val isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT
                    list.add(SmsMessageItem(id, tId, address, body, date, isSent, false))
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error reading thread messages", e)
        }
        return@withContext list
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        deletedRepository.deleteMessage(messageId)
        try {
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId.toLong())
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error deleting message in system provider", e)
        }
        return@withContext true
    }

    suspend fun deleteConversation(threadId: String): Boolean = withContext(Dispatchers.IO) {
        deletedRepository.deleteThread(threadId)
        try {
            val uri = Uri.parse("content://sms/conversations/$threadId")
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error deleting thread in system provider", e)
        }
        return@withContext true
    }

    suspend fun sendSms(phoneNumber: String, message: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Write to Sent SMS content provider so thread message history reflects sent text
            try {
                val values = android.content.ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, phoneNumber)
                    put(Telephony.Sms.BODY, message)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.READ, 1)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e("SmsRepository", "Could not insert sent message into Telephony provider", e)
            }

            // Attempt to transmit SMS via Telephony SmsManager
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)

                fun sentIntentFor(index: Int): android.app.PendingIntent {
                    val intent = Intent(context, SmsStatusReceiver::class.java).apply {
                        action = SmsStatusReceiver.ACTION_SMS_SENT
                        putExtra(SmsStatusReceiver.EXTRA_ADDRESS, phoneNumber)
                    }
                    val requestCode = (phoneNumber + index + System.nanoTime()).hashCode()
                    return android.app.PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                }

                if (parts.size > 1) {
                    val sentIntents = ArrayList<android.app.PendingIntent>(parts.size)
                    for (i in parts.indices) sentIntents.add(sentIntentFor(i))
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, sentIntentFor(0), null)
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "SmsManager send failure", e)
            }
            true
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error sending SMS", e)
            false
        }
    }

    suspend fun markAsRead(threadId: String) = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
            val selectionArgs = arrayOf(threadId)
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, selection, selectionArgs)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error marking as read", e)
        }
    }
}
