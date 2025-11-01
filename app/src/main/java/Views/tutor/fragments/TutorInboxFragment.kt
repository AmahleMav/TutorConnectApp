package com.example.tutorconnect.Views.tutor.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import Views.shared.adapters.ChatAdapter
import Views.shared.ChatDetailActivity
import models.Chat
import models.Message

class TutorInboxFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val chatList = mutableListOf<Chat>()
    private var chatListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_inbox, container, false)
        recyclerView = view.findViewById(R.id.recyclerTutorChats)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ChatAdapter(chatList, "Tutor") { chat ->
            openChatDetail(chat)
        }
        recyclerView.adapter = adapter

        listenToChatsRealtime()
        return view
    }

    private fun listenToChatsRealtime() {
        val tutorId = auth.currentUser?.uid ?: return

        chatListener = db.collection("Chats")
            .whereEqualTo("TutorId", tutorId)
            .orderBy("CreatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("TutorInboxFragment", "❌ Error loading chats", error)
                    Toast.makeText(requireContext(), "Failed to load chats", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                chatList.clear()
                snapshots?.documents?.forEach { doc ->
                    try {
                        val chatId = doc.id
                        val studentId = doc.getString("StudentId") ?: ""
                        val tutorIdField = doc.getString("TutorId") ?: ""
                        val createdAt = doc.getTimestamp("CreatedAt") ?: Timestamp.now()
                        val lastReadByStudent = doc.getTimestamp("LastReadByStudent")

                        // Normalize UnreadBy
                        val rawUnread = doc.get("UnreadBy")
                        val unreadBy: MutableList<String> = when (rawUnread) {
                            is String -> mutableListOf(rawUnread)
                            is List<*> -> rawUnread.filterIsInstance<String>().toMutableList()
                            else -> mutableListOf()
                        }

                        // Deserialize Messages
                        val rawMessages = doc.get("Messages") as? List<Map<String, Any>> ?: emptyList()
                        val messages = rawMessages.mapNotNull { msg ->
                            try {
                                Message(
                                    SenderId = msg["SenderId"] as? String ?: return@mapNotNull null,
                                    MessageText = msg["MessageText"] as? String ?: "",
                                    SentAt = msg["SentAt"] as? Timestamp ?: Timestamp.now()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.toMutableList()

                        val chat = Chat(
                            ChatId = chatId,
                            StudentId = studentId,
                            TutorId = tutorIdField,
                            Messages = messages,
                            CreatedAt = createdAt,
                            LastReadByStudent = lastReadByStudent,
                            UnreadBy = unreadBy
                        )

                        chatList.add(chat)
                    } catch (e: Exception) {
                        Log.e("TutorInboxFragment", "❌ Failed to parse chat doc ${doc.id}", e)
                    }
                }
                adapter.notifyDataSetChanged()
                Log.d("TutorInboxFragment", "📡 Updated chat list in real-time (${chatList.size})")
            }
    }

    private fun openChatDetail(chat: Chat) {
        val tutorId = auth.currentUser?.uid ?: return

        // Remove this tutor from UnreadBy
        val updatedUnread = chat.UnreadBy.toMutableList().apply { remove(tutorId) }
        db.collection("Chats").document(chat.ChatId)
            .update("UnreadBy", updatedUnread)
            .addOnSuccessListener {
                chat.UnreadBy = updatedUnread
                adapter.notifyDataSetChanged()
                Log.d("TutorInboxFragment", "✅ Marked chat as read: ${chat.ChatId}")
            }
            .addOnFailureListener {
                Log.e("TutorInboxFragment", "⚠️ Failed to mark chat as read", it)
            }

        // Fetch names
        db.collection("Students").document(chat.StudentId).get()
            .addOnSuccessListener { studentDoc ->
                val studentName = listOfNotNull(
                    studentDoc.getString("Name"),
                    studentDoc.getString("Surname")
                ).joinToString(" ").ifBlank { "Student" }

                db.collection("Tutors").document(tutorId).get()
                    .addOnSuccessListener { tutorDoc ->
                        val tutorName = listOfNotNull(
                            tutorDoc.getString("Name"),
                            tutorDoc.getString("Surname")
                        ).joinToString(" ").ifBlank { "Tutor" }

                        val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                            putExtra("ChatId", chat.ChatId)
                            putExtra("TutorId", tutorId)
                            putExtra("StudentId", chat.StudentId)
                            putExtra("tutorName", tutorName)
                            putExtra("studentName", studentName)
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener {
                        Log.e("TutorInboxFragment", "Failed to fetch tutor name", it)
                    }
            }
            .addOnFailureListener {
                Log.e("TutorInboxFragment", "Failed to fetch student name", it)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatListener?.remove()
    }
}
