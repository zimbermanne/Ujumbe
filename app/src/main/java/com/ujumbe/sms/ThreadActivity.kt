package com.ujumbe.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ThreadActivity : BaseActivity() {

    private lateinit var threadId: String
    private lateinit var address: String

    private lateinit var smsRepository: SmsRepository
    private lateinit var blockRepository: BlockRepository
    private lateinit var archiveRepository: ArchiveRepository
    private lateinit var adapter: MessageAdapter

    private lateinit var recyclerMessages: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var buttonSend: Button

    private var receiversRegistered = false

    private val newMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra("sender") ?: ""
            // Refresh if the message is from this contact
            if (sender.cleanPhoneNumber() == address.cleanPhoneNumber()) {
                loadMessages()
                lifecycleScope.launch {
                    smsRepository.markAsRead(threadId)
                }
            }
        }
    }

    private val sendResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultAddress = intent?.getStringExtra(SmsStatusReceiver.EXTRA_ADDRESS) ?: ""
            val success = intent?.getBooleanExtra(SmsStatusReceiver.EXTRA_SUCCESS, true) ?: true
            if (resultAddress.cleanPhoneNumber() == address.cleanPhoneNumber()) {
                loadMessages()
                if (!success) {
                    Toast.makeText(
                        this@ThreadActivity,
                        "Message failed to send. Check your signal and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        threadId = intent.getStringExtra("thread_id") ?: ""
        address = intent.getStringExtra("address") ?: ""

        if (threadId.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Error: Conversation details missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        smsRepository = SmsRepository(this)
        blockRepository = BlockRepository(this)
        archiveRepository = ArchiveRepository(this)

        // Initialize Views
        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        val textThreadAvatar: TextView = findViewById(R.id.textThreadAvatar)
        val textThreadTitle: TextView = findViewById(R.id.textThreadTitle)
        val buttonMore: ImageButton = findViewById(R.id.buttonMore)
        val buttonCall: ImageButton = findViewById(R.id.buttonCall)

        recyclerMessages = findViewById(R.id.recyclerMessages)
        editMessage = findViewById(R.id.editMessage)
        buttonSend = findViewById(R.id.buttonSend)

        // Header binding
        val contactName = ContactsHelper.getContactName(this, address)
        textThreadTitle.text = contactName
        textThreadAvatar.text = initialsFor(contactName)
        textThreadAvatar.background.setTint(colorFor(contactName))

        buttonBack.setOnClickListener { finish() }

        buttonCall.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to launch dialer", Toast.LENGTH_SHORT).show()
            }
        }

        buttonMore.setOnClickListener {
            showMoreOptionsDialog()
        }

        // Set up recycler view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerMessages.layoutManager = layoutManager

        adapter = MessageAdapter(this, emptyList(),
            onItemLongClick = { message ->
                showDeleteMessageDialog(message)
            }
        )
        recyclerMessages.adapter = adapter

        // Send action
        buttonSend.setOnClickListener {
            val body = editMessage.text.toString().trim()
            if (body.isNotEmpty()) {
                lifecycleScope.launch {
                    val success = smsRepository.sendSms(address, body)
                    if (success) {
                        editMessage.setText("")
                        // Wait 500ms to allow system to write sent message, then reload
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadMessages()
                        }, 500)
                    } else {
                        Toast.makeText(this@ThreadActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Register receivers
        ContextCompat.registerReceiver(
            this,
            newMessageReceiver,
            IntentFilter(SmsReceiver.ACTION_NEW_SMS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            sendResultReceiver,
            IntentFilter(SmsStatusReceiver.ACTION_SEND_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiversRegistered = true
        lifecycleScope.launch {
            smsRepository.markAsRead(threadId)
        }
    }

    override fun onResume() {
        super.onResume()
        ActiveThreadTracker.activeThreadAddress = address.cleanPhoneNumber()
        NotificationHelper.cancelNotificationForThread(this, threadId)
        loadMessages()
    }

    override fun onPause() {
        super.onPause()
        if (ActiveThreadTracker.activeThreadAddress == address.cleanPhoneNumber()) {
            ActiveThreadTracker.activeThreadAddress = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Guard against the early-return path above (missing thread_id/address),
        // where these receivers were never registered - unregistering them
        // unconditionally would throw IllegalArgumentException.
        if (receiversRegistered) {
            unregisterReceiver(newMessageReceiver)
            unregisterReceiver(sendResultReceiver)
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val messages = smsRepository.getMessagesForThread(threadId)
            adapter.updateList(messages)
            if (messages.isNotEmpty()) {
                recyclerMessages.smoothScrollToPosition(messages.size - 1)
            }
        }
    }

    private fun showDeleteMessageDialog(message: SmsMessageItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_message))
            .setMessage(getString(R.string.delete_message_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    val success = smsRepository.deleteMessage(message.id)
                    if (success) {
                        loadMessages()
                        Toast.makeText(this@ThreadActivity, "Message deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ThreadActivity, "Failed to delete message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showMoreOptionsDialog() {
        val isArchived = archiveRepository.isArchived(threadId)
        val options = arrayOf(
            if (blockRepository.isBlocked(address)) getString(R.string.unblock_number) else getString(R.string.block_number),
            if (isArchived) getString(R.string.unarchive) else getString(R.string.archive),
            getString(R.string.delete_conversation)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.conversation_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Block/Unblock toggle
                        val isBlocked = blockRepository.isBlocked(address)
                        blockRepository.setBlocked(address, !isBlocked)
                        if (!isBlocked) {
                            Toast.makeText(this, getString(R.string.conversation_blocked_toast, address), Toast.LENGTH_SHORT).show()
                            finish() // Exit conversation thread if blocked
                        } else {
                            Toast.makeText(this, getString(R.string.conversation_unblocked_toast, address), Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> { // Archive/Unarchive toggle
                        archiveRepository.setArchived(threadId, !isArchived)
                        val toastMsg = if (!isArchived) getString(R.string.archive_success) else getString(R.string.unarchive_success)
                        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    2 -> { // Delete conversation
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.delete_conversation))
                            .setMessage(getString(R.string.delete_conversation_confirm))
                            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                lifecycleScope.launch {
                                    val success = smsRepository.deleteConversation(threadId)
                                    if (success) {
                                        Toast.makeText(this@ThreadActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                                        finish()
                                    } else {
                                        Toast.makeText(this@ThreadActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun initialsFor(name: String): String {
        val cleaned = name.trim().replace("+", "")
        if (cleaned.isEmpty()) return "U"
        val parts = cleaned.split("\\s+".toRegex())
        if (parts.size >= 2) {
            val first = parts[0].firstOrNull() ?: ' '
            val second = parts[1].firstOrNull() ?: ' '
            if (first.isLetter() && second.isLetter()) {
                return "$first$second".uppercase()
            }
        }
        return cleaned.take(2).uppercase()
    }

    private fun colorFor(name: String): Int {
        val colors = arrayOf(
            R.color.avatar_1,
            R.color.avatar_2,
            R.color.avatar_3,
            R.color.avatar_4,
            R.color.avatar_5,
            R.color.avatar_6
        )
        val index = Math.abs(name.hashCode()) % colors.size
        return ContextCompat.getColor(this, colors[index])
    }
}
