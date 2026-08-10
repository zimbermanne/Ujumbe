package com.ujumbe.sms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ArchiveListActivity : BaseActivity() {

    private lateinit var smsRepository: SmsRepository
    private lateinit var blockRepository: BlockRepository
    private lateinit var archiveRepository: ArchiveRepository
    private lateinit var adapter: ConversationAdapter

    private lateinit var recyclerArchiveList: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var editSearchArchive: EditText
    private lateinit var buttonClearSearch: ImageButton

    private var allArchivedConversations = listOf<ConversationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive_list)

        smsRepository = SmsRepository(this)
        blockRepository = BlockRepository(this)
        archiveRepository = ArchiveRepository(this)

        // Initialize Views
        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        recyclerArchiveList = findViewById(R.id.recyclerArchiveList)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        editSearchArchive = findViewById(R.id.editSearchArchive)
        buttonClearSearch = findViewById(R.id.buttonClearSearch)

        buttonBack.setOnClickListener { finish() }

        recyclerArchiveList.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter(this, emptyList(), blockRepository,
            onItemClick = { item ->
                val intent = Intent(this, ThreadActivity::class.java).apply {
                    putExtra("thread_id", item.threadId)
                    putExtra("address", item.address)
                }
                startActivity(intent)
            },
            onItemLongClick = { item ->
                showArchiveOptionsDialog(item)
            }
        )
        recyclerArchiveList.adapter = adapter

        // Search text watcher
        editSearchArchive.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                buttonClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filter(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        buttonClearSearch.setOnClickListener {
            editSearchArchive.setText("")
        }
    }

    override fun onResume() {
        super.onResume()
        loadArchivedConversations()
    }

    private fun loadArchivedConversations() {
        lifecycleScope.launch {
            val list = smsRepository.getConversations()
            // Filter archived & not blocked
            allArchivedConversations = list.filter { archiveRepository.isArchived(it.threadId) && !blockRepository.isBlocked(it.address) }
            filter(editSearchArchive.text?.toString() ?: "")
        }
    }

    private fun filter(query: String) {
        val filtered = if (query.isEmpty()) {
            allArchivedConversations
        } else {
            allArchivedConversations.filter {
                val contactName = ContactsHelper.getContactName(this, it.address)
                contactName.contains(query, ignoreCase = true) ||
                        it.address.contains(query, ignoreCase = true) ||
                        it.snippet.contains(query, ignoreCase = true)
            }
        }

        adapter.updateList(filtered)

        if (filtered.isEmpty()) {
            val textEmpty: TextView = findViewById(R.id.textEmpty)
            if (query.isEmpty()) {
                textEmpty.text = getString(R.string.no_archived_conversations)
            } else {
                textEmpty.text = getString(R.string.no_search_results)
            }
            layoutEmpty.visibility = View.VISIBLE
        } else {
            layoutEmpty.visibility = View.GONE
        }
    }

    private fun showArchiveOptionsDialog(item: ConversationItem) {
        val options = arrayOf(
            getString(R.string.unarchive),
            getString(R.string.delete_conversation)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.conversation_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Unarchive
                        archiveRepository.setArchived(item.threadId, false)
                        Toast.makeText(this, getString(R.string.unarchive_success), Toast.LENGTH_SHORT).show()
                        loadArchivedConversations()
                    }
                    1 -> { // Delete
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.delete_conversation))
                            .setMessage(getString(R.string.delete_conversation_confirm))
                            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                lifecycleScope.launch {
                                    val success = smsRepository.deleteConversation(item.threadId)
                                    if (success) {
                                        loadArchivedConversations()
                                        Toast.makeText(this@ArchiveListActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@ArchiveListActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
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
}
