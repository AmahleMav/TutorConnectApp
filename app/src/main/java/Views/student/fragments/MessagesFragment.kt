package Views.student.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import Views.shared.ChatDetailActivity
import Views.shared.adapters.ChatAdapter
import models.Chat
import kotlin.coroutines.CoroutineContext

class MessagesFragment : Fragment(), CoroutineScope {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private var chatListener: ListenerRegistration? = null
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_messages, container, false)

        recyclerMessages = view.findViewById(R.id.recyclerMessages)
        recyclerMessages.layoutManager = LinearLayoutManager(requireContext())

        chatAdapter = ChatAdapter(chatList, "Student") { chat ->
            openChatDetail(chat)
        }
        recyclerMessages.adapter = chatAdapter

        job = Job()
        listenToChatsRealtime()

        return view
    }

    private fun listenToChatsRealtime() {
        if (currentUserId == null) return

        chatListener = db.collection("Chats")
            .whereEqualTo("StudentId", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MessagesFragment", "Real-time listener error", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    chatList.clear()

                    // Step 1: Build chat objects without tutor info
                    val chats = snapshot.documents.map { doc ->
                        val chatId = doc.id
                        val studentId = doc.getString("StudentId") ?: ""
                        val tutorId = doc.getString("TutorId") ?: ""
                        val createdAt = doc.getTimestamp("CreatedAt") ?: Timestamp.now()
                        val lastReadByStudent = doc.getTimestamp("LastReadByStudent")

                        val rawUnread = doc.get("UnreadBy")
                        val unreadBy: MutableList<String> = when (rawUnread) {
                            is String -> mutableListOf(rawUnread)
                            is List<*> -> rawUnread.filterIsInstance<String>().toMutableList()
                            else -> mutableListOf()
                        }

                        val rawMessages = doc.get("Messages") as? List<Map<String, Any>> ?: emptyList()
                        val messages = rawMessages.mapNotNull { msg ->
                            try {
                                models.Message(
                                    SenderId = msg["SenderId"] as? String ?: return@mapNotNull null,
                                    MessageText = msg["MessageText"] as? String ?: "",
                                    SentAt = msg["SentAt"] as? Timestamp ?: Timestamp.now()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.toMutableList()

                        Chat(
                            ChatId = chatId,
                            StudentId = studentId,
                            TutorId = tutorId,
                            Messages = messages,
                            CreatedAt = createdAt,
                            LastReadByStudent = lastReadByStudent,
                            UnreadBy = unreadBy
                        )
                    }

                    chatList.addAll(chats)

                    // Step 2: Fetch all tutor profiles in one query
                    val tutorIds = chats.map { it.TutorId }.distinct()
                    if (tutorIds.isNotEmpty()) {
                        db.collection("TutorProfiles")
                            .whereIn("UserId", tutorIds)
                            .get()
                            .addOnSuccessListener { profilesSnapshot ->

                                val profileMap = profilesSnapshot.documents
                                    .associateBy { it.getString("UserId") ?: "" }

                                chatList.forEach { chat ->
                                    val profileDoc = profileMap[chat.TutorId]
                                    if (profileDoc != null) {
                                        chat.TutorImageBase64 = profileDoc.getString("ProfileImageBase64") ?: ""

                                        // Fetch Name/Surname from Tutors collection if needed
                                        db.collection("Tutors").document(chat.TutorId).get()
                                            .addOnSuccessListener { tutorDoc ->
                                                chat.TutorName = listOfNotNull(
                                                    tutorDoc.getString("Name"),
                                                    tutorDoc.getString("Surname")
                                                ).joinToString(" ")
                                                chatAdapter.notifyDataSetChanged()
                                            }
                                    }
                                }

                                // Step 3: Fetch students info
                                GlobalScope.launch(Dispatchers.Main) {
                                    val studentTasks = chatList.map { chat ->
                                        async {
                                            val studentDoc = db.collection("Students").document(chat.StudentId).get().await()
                                            chat.StudentName = listOfNotNull(
                                                studentDoc.getString("Name"),
                                                studentDoc.getString("Surname")
                                            ).joinToString(" ")
                                            chat.StudentImageBase64 = studentDoc.getString("ProfileImageBase64") ?: ""
                                        }
                                    }
                                    studentTasks.awaitAll()

                                    chatList.sortByDescending { it.Messages.lastOrNull()?.SentAt?.toDate() ?: it.CreatedAt.toDate() }
                                    chatAdapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MessagesFragment", "Failed to fetch tutor profiles: ${e.message}")
                            }
                    }
                }
            }
    }

    private fun openChatDetail(chat: Chat) {
        val studentId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatRef = db.collection("Chats").document(chat.ChatId)

        // Remove student from UnreadBy
        val updatedUnread = chat.UnreadBy.toMutableList().apply { remove(studentId) }
        chatRef.update(
            mapOf(
                "UnreadBy" to updatedUnread,
                "LastReadByStudent" to Timestamp.now()
            )
        ).addOnSuccessListener {
            chat.UnreadBy = updatedUnread
            chatAdapter.notifyDataSetChanged()
        }.addOnFailureListener { e ->
            Log.e("MessagesFragment", "Failed to mark chat as read: ${e.message}")
        }

        // Open ChatDetail
        GlobalScope.launch(Dispatchers.Main) {
            val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                putExtra("ChatId", chat.ChatId)
                putExtra("StudentId", chat.StudentId)
                putExtra("TutorId", chat.TutorId)
                putExtra("tutorName", chat.TutorName ?: "")
                putExtra("studentName", chat.StudentName ?: "")
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        chatListener?.remove()
    }
}
