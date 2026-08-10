package com.ujumbe.sms

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ComposeActivity : BaseActivity() {

    companion object {
        private const val PICK_CONTACT_REQUEST = 1
    }

    private lateinit var editPhoneNumber: EditText
    private lateinit var editMessageBody: EditText
    private lateinit var smsRepository: SmsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose)

        smsRepository = SmsRepository(this)

        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        editPhoneNumber = findViewById(R.id.editPhoneNumber)
        editMessageBody = findViewById(R.id.editMessageBody)
        val buttonPickContact: TextView = findViewById(R.id.buttonPickContact)
        val buttonSendNew: MaterialButton = findViewById(R.id.buttonSendNew)

        buttonBack.setOnClickListener { finish() }

        buttonPickContact.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                startActivityForResult(intent, PICK_CONTACT_REQUEST)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to pick contact", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSendNew.setOnClickListener {
            val phoneNumber = editPhoneNumber.text.toString().trim()
            val body = editMessageBody.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (body.isEmpty()) {
                Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val success = smsRepository.sendSms(phoneNumber, body)
                if (success) {
                    Toast.makeText(this@ComposeActivity, "Message sent successfully", Toast.LENGTH_SHORT).show()
                    try {
                        val threadId = Telephony.Threads.getOrCreateThreadId(this@ComposeActivity, phoneNumber).toString()
                        val intent = Intent(this@ComposeActivity, ThreadActivity::class.java).apply {
                            putExtra("thread_id", threadId)
                            putExtra("address", phoneNumber)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to list activity if thread id lookup fails
                    }
                    finish()
                } else {
                    Toast.makeText(this@ComposeActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            val contactUri: Uri = data?.data ?: return
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            try {
                contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numberIndex != -1) {
                            val number = cursor.getString(numberIndex)
                            editPhoneNumber.setText(number)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading contact details", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
