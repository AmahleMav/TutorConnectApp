package services

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import models.Chat
import models.Message
import java.util.*

class ChatService {

    private val db = FirebaseFirestore.getInstance()
    private val chatsRef = db.collection("Chats")
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    suspend fun getOrCreateChat(tutorId: String): Chat? {
        try {
            // Check if chat already exists
            val existingChat = chatsRef
                .whereEqualTo("TutorId", tutorId)
                .whereEqualTo("StudentId", currentUserId)
                .get()
                .await()

            if (!existingChat.isEmpty) {
                val chat = existingChat.documents.first().toObject(Chat::class.java)
                chat?.ChatId = existingChat.documents.first().id
                return chat
            }

            // Create a new chat
            val newChatId = UUID.randomUUID().toString()
            val newChat = Chat(
                ChatId = newChatId,
                TutorId = tutorId,
                StudentId = currentUserId,
                CreatedAt = Timestamp.now()
            )

            chatsRef.document(newChatId).set(newChat).await()
            return newChat

        } catch (e: Exception) {
            Log.e("ChatService", "❌ Error getting/creating chat: ${e.message}")
            return null
        }
    }

    suspend fun sendMessage(chatId: String, messageText: String) {
        try {
            val message = Message(
                MessageText = messageText,
                SenderId = currentUserId,
                SentAt = Timestamp.now()
            )

            val chatDoc = chatsRef.document(chatId)
            chatDoc.update("Messages", com.google.firebase.firestore.FieldValue.arrayUnion(message)).await()
            chatDoc.update("UnreadBy", "Tutor").await()

        } catch (e: Exception) {
            Log.e("ChatService", "❌ Failed to send message: ${e.message}")
        }
    }

    suspend fun getMessages(chatId: String): List<Message> {
        return try {
            val chatDoc = chatsRef.document(chatId).get().await()
            val chat = chatDoc.toObject(Chat::class.java)
            chat?.Messages ?: emptyList()
        } catch (e: Exception) {
            Log.e("ChatService", "❌ Error fetching messages: ${e.message}")
            emptyList()
        }
    }
}
