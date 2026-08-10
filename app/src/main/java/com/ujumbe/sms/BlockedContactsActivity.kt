package com.ujumbe.sms

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockedContactsActivity : BaseActivity() {

    private lateinit var blockRepository: BlockRepository
    private lateinit var smsRepository: SmsRepository
    private lateinit var adapter: BlockListAdapter

    private lateinit var editSearchBlocked: EditText
    private lateinit var buttonClearSearch: ImageButton
    private lateinit var recyclerBlockList: RecyclerView
    private lateinit var textNoConversations: TextView

    private var allConversations = listOf<ConversationItem>()
    private var filterJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_contacts)

        blockRepository = BlockRepository(this)
        smsRepository = SmsRepository(this)

        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        editSearchBlocked = findViewById(R.id.editSearchBlocked)
        buttonClearSearch = findViewById(R.id.buttonClearSearch)
        recyclerBlockList = findViewById(R.id.recyclerBlockList)
        textNoConversations = findViewById(R.id.textNoConversations)

        buttonBack.setOnClickListener { finish() }

        // Set up Block List Recycler View
        recyclerBlockList.layoutManager = LinearLayoutManager(this)
        adapter = BlockListAdapter(this, emptyList(), blockRepository) { item, isBlocked ->
            val contactName = ContactsHelper.getContactName(this, item.address)
            val msg = if (isBlocked) {
                getString(R.string.conversation_blocked_toast, contactName)
            } else {
                getString(R.string.conversation_unblocked_toast, contactName)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        recyclerBlockList.adapter = adapter

        // Search text watcher
        editSearchBlocked.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                buttonClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filter(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        buttonClearSearch.setOnClickListener {
            editSearchBlocked.setText("")
        }

        loadContactsList()
    }

    private fun loadContactsList() {
        lifecycleScope.launch {
            allConversations = smsRepository.getConversations()
            filter(editSearchBlocked.text?.toString() ?: "")
        }
    }

    private fun filter(query: String) {
        filterJob?.cancel()
        filterJob = lifecycleScope.launch {
            if (query.isNotEmpty()) delay(150) // Debounce

            val filteredList = if (query.isEmpty()) {
                allConversations
            } else {
                withContext(Dispatchers.Default) {
                    allConversations.filter { item ->
                        val contactName =
                            ContactsHelper.getContactName(this@BlockedContactsActivity, item.address)
                        contactName.contains(query, ignoreCase = true) || item.address.contains(
                            query,
                            ignoreCase = true
                        )
                    }
                }
            }
            adapter.updateList(filteredList)
            if (filteredList.isEmpty()) {
                if (query.isEmpty()) {
                    textNoConversations.text = getString(R.string.settings_no_blocked)
                } else {
                    textNoConversations.text = getString(R.string.no_search_results)
                }
                textNoConversations.visibility = View.VISIBLE
            } else {
                textNoConversations.visibility = View.GONE
            }
        }
    }
}
