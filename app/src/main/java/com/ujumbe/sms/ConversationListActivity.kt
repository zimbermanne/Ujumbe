package com.ujumbe.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationListActivity : BaseActivity() {

    private lateinit var smsRepository: SmsRepository
    private lateinit var blockRepository: BlockRepository
    private lateinit var archiveRepository: ArchiveRepository
    private lateinit var adapter: ConversationAdapter

    private lateinit var recyclerConversations: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var headerNormal: RelativeLayout
    private lateinit var editSearch: EditText

    private var allConversations = listOf<ConversationItem>()
    private var searchJob: Job? = null

    private val newSmsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadConversations()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_list)

        smsRepository = SmsRepository(this)
        blockRepository = BlockRepository(this)
        archiveRepository = ArchiveRepository(this)

        // Initialize Views
        recyclerConversations = findViewById(R.id.recyclerConversations)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        headerNormal = findViewById(R.id.headerNormal)
        editSearch = findViewById(R.id.editSearch)

        recyclerConversations.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter(this, emptyList(), blockRepository,
            onItemClick = { item ->
                val intent = Intent(this, ThreadActivity::class.java).apply {
                    putExtra("thread_id", item.threadId)
                    putExtra("address", item.address)
                }
                startActivity(intent)
            },
            onItemLongClick = { item ->
                showOptionsDialog(item)
            }
        )
        recyclerConversations.adapter = adapter

        // Set up Header Actions
        findViewById<ImageButton>(R.id.buttonSearch).setOnClickListener {
            editSearch.requestFocus()
        }
        findViewById<View>(R.id.buttonAdd).setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }

        // Search filtering
        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterConversations(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Settings button setup
        findViewById<ImageButton>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Register Receiver for Real-time incoming SMS
        ContextCompat.registerReceiver(
            this,
            newSmsReceiver,
            IntentFilter(SmsReceiver.ACTION_NEW_SMS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(newSmsReceiver)
    }

    private fun loadConversations() {
        if (!PermissionsHelper.hasPermissions(this)) return

        lifecycleScope.launch {
            // Read all conversations
            val list = smsRepository.getConversations()

            // Filter out those from blocked or archived contacts
            allConversations = list.filter { !blockRepository.isBlocked(it.address) && !archiveRepository.isArchived(it.threadId) }

            val searchQuery = editSearch.text.toString()
            if (searchQuery.isNotEmpty()) {
                filterConversations(searchQuery)
            } else {
                updateUiList(allConversations)
            }
        }
    }

    private fun filterConversations(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(150) // Debounce search input
            
            val filtered = withContext(Dispatchers.Default) {
                allConversations.filter {
                    val contactName = ContactsHelper.getContactName(this@ConversationListActivity, it.address)
                    contactName.contains(query, ignoreCase = true) ||
                            it.address.contains(query, ignoreCase = true) ||
                            it.snippet.contains(query, ignoreCase = true)
                }
            }
            updateUiList(filtered)
        }
    }

    private fun updateUiList(list: List<ConversationItem>) {
        adapter.updateList(list)
        if (list.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
        } else {
            layoutEmpty.visibility = View.GONE
        }
    }

    private fun showSearchHeader(show: Boolean) {
        // Search is now always in a glassy pill below header or handled differently
        // Keeping this for potential future toggle logic, but currently simplified
    }

    private fun showOptionsDialog(item: ConversationItem) {
        val options = arrayOf(
            getString(R.string.archive),
            getString(R.string.delete_conversation)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.conversation_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Archive
                        archiveRepository.setArchived(item.threadId, true)
                        Toast.makeText(this, getString(R.string.archive_success), Toast.LENGTH_SHORT).show()
                        loadConversations()
                    }
                    1 -> { // Delete conversation
                        showDeleteConversationDialog(item)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteConversationDialog(item: ConversationItem) {
        val contactName = ContactsHelper.getContactName(this, item.address)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_conversation))
            .setMessage(getString(R.string.delete_conversation_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    val success = smsRepository.deleteConversation(item.threadId)
                    if (success) {
                        loadConversations()
                        Toast.makeText(this@ConversationListActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ConversationListActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
