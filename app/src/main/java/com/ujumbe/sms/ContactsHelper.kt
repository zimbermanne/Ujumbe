package com.ujumbe.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

object ContactsHelper {
    private val contactCache = ConcurrentHashMap<String, String>()

    fun getContactName(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return "Unknown"
        
        // Return from cache if available
        contactCache[phoneNumber]?.let { return it }

        var contactName: String = phoneNumber
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        contactName = cursor.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Cache the result
        contactCache[phoneNumber] = contactName
        
        return contactName
    }

    fun clearCache() {
        contactCache.clear()
    }
}

fun String.cleanPhoneNumber(): String {
    return this.replace("\\s".toRegex(), "").replace("-", "").replace("(", "").replace(")", "")
}
