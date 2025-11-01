package Views.shared

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import models.Message
import Views.shared.adapters.MessageAdapter

class ChatDetailActivity : AppCompatActivity() {

    private lateinit var chatToolbar: MaterialToolbar
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: ImageButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid
    private var chatId: String? = null
    private var studentId: String? = null
    private var tutorId: String? = null
    private var chatPartnerName: String? = null

    private lateinit var adapter: MessageAdapter
    private val messagesList = mutableListOf<Message>()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        // Toolbar setup
        chatToolbar = findViewById(R.id.chatToolbar)
        chatToolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        chatToolbar.setNavigationOnClickListener { finish() }

        // UI setup
        recyclerMessages = findViewById(R.id.recyclerChatMessages)
        edtMessage = findViewById(R.id.edtMessageInput)
        btnSend = findViewById(R.id.btnSendMessage)

        // Intent Data
        chatId = intent.getStringExtra("ChatId")
        studentId = intent.getStringExtra("StudentId")
        tutorId = intent.getStringExtra("TutorId")

        val passedTutorName = intent.getStringExtra("tutorName")
        val passedStudentName = intent.getStringExtra("studentName")

        // Determine who the chat partner is
        chatPartnerName = when (currentUserId) {
            tutorId -> passedStudentName
            studentId -> passedTutorName
            else -> "Chat"
        }

        // Set toolbar title dynamically
        chatToolbar.title = chatPartnerName ?: "Chat"

        // Recycler setup
        adapter = MessageAdapter(messagesList, currentUserId)
        recyclerMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerMessages.adapter = adapter

        // Listen for messages
        if (chatId != null) listenForMessages()

        // Send button
        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }
    }

    /**
     * Real-time listener for chat messages
     */
    private fun listenForMessages() {
        listener = db.collection("Chats").document(chatId!!).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("ChatDetailActivity", "Listen failed", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val messagesArray = snapshot.get("Messages") as? List<Map<String, Any>> ?: emptyList()

                messagesList.clear()
                for (msgData in messagesArray) {
                    val message = Message(
                        MessageText = msgData["MessageText"] as? String ?: "",
                        SenderId = msgData["SenderId"] as? String ?: "",
                        SentAt = msgData["SentAt"] as? Timestamp
                    )
                    messagesList.add(message)
                }

                messagesList.sortBy { it.SentAt?.toDate()?.time ?: 0L }
                adapter.notifyDataSetChanged()
                recyclerMessages.scrollToPosition(messagesList.size - 1)
            }
        }
    }

    /**
     * Send message to Firestore
     */
    private fun sendMessage(text: String) {
        if (chatId == null || currentUserId == null) return

        val message = mapOf(
            "MessageText" to text,
            "SenderId" to currentUserId,
            "SentAt" to Timestamp.now()
        )

        val chatRef = db.collection("Chats").document(chatId!!)
        chatRef.update(
            "Messages", FieldValue.arrayUnion(message),
            "UnreadBy", if (currentUserId == studentId) tutorId else studentId,
            "LastReadByStudent", if (currentUserId == studentId) FieldValue.serverTimestamp() else null
        ).addOnSuccessListener {
            edtMessage.text.clear()
        }.addOnFailureListener { e ->
            Log.e("ChatDetailActivity", "Error sending message", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
