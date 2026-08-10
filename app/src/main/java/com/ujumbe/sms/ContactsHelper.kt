package com.ujumbe.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactsHelper {
    fun getContactName(context: Context, phoneNumber: String): String {
        var name = phoneNumber
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
                        name = cursor.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }
}

fun String.cleanPhoneNumber(): String {
    return this.replace("\\s".toRegex(), "").replace("-", "").replace("(", "").replace(")", "")
}
