package Views.shared.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.example.tutorconnect.utils.loadProfileImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import models.Chat

class ChatAdapter(
    private val chats: List<Chat>,
    private val currentUserRole: String, // "Student" or "Tutor"
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_shared, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgProfile = itemView.findViewById<ImageView>(R.id.imgChatProfile)
        private val txtName = itemView.findViewById<TextView>(R.id.txtChatName)
        private val txtLastMessage = itemView.findViewById<TextView>(R.id.txtLastMessage)
        private val unreadDot = itemView.findViewById<View>(R.id.txtUnreadIndicator)

        fun bind(chat: Chat) {
            val otherUserId = if (currentUserRole == "Student") chat.TutorId else chat.StudentId

            // Prefer preloaded fields from Chat model
            val nameFromModel = if (currentUserRole == "Student") chat.TutorName else chat.StudentName
            val imageBase64FromModel = if (currentUserRole == "Student") chat.TutorImageBase64 else chat.StudentImageBase64

            // Set name if available (fallback to Unknown)
            txtName.text = if (!nameFromModel.isNullOrBlank()) nameFromModel else "Unknown"

            // Debug log: what adapter sees
            android.util.Log.d(
                "ChatAdapter",
                "bind() chat=${chat.ChatId} role=$currentUserRole modelImageLen=${imageBase64FromModel?.length ?: 0}"
            )

            // If model already contains image use it (preferred)
            if (!imageBase64FromModel.isNullOrBlank()) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        imgProfile.loadProfileImage(imageBase64FromModel)
                    } catch (ex: Exception) {
                        android.util.Log.e("ChatAdapter", "Error loading image for ${chat.ChatId}: ${ex.message}")
                        imgProfile.setImageResource(R.drawable.ic_person)
                    }
                }
            } else {
                // Minimal fallback: query the collection that actually stores the profile image for the role
                if (currentUserRole == "Student") {
                    // For Students viewing chats, tutor images typically live in TutorProfiles
                    db.collection("TutorProfiles")
                        .whereEqualTo("UserId", otherUserId)
                        .get()
                        .addOnSuccessListener { snap ->
                            val doc = snap.documents.firstOrNull()
                            val fallbackBase64 = doc?.getString("ProfileImageBase64").orEmpty()
                            val fallbackName = listOfNotNull(doc?.getString("Name"), doc?.getString("Surname")).joinToString(" ")
                            if (fallbackName.isNotBlank() && nameFromModel.isNullOrBlank()) {
                                chat.TutorName = fallbackName
                                txtName.text = fallbackName
                            }
                            if (fallbackBase64.isNotBlank()) {
                                chat.TutorImageBase64 = fallbackBase64 // update model to avoid future reads
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        imgProfile.loadProfileImage(fallbackBase64)
                                    } catch (ex: Exception) {
                                        android.util.Log.e("ChatAdapter", "Fallback load failed for ${chat.ChatId}: ${ex.message}")
                                        imgProfile.setImageResource(R.drawable.ic_person)
                                    }
                                }
                            } else {
                                imgProfile.setImageResource(R.drawable.ic_person)
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("ChatAdapter", "Failed to fetch TutorProfiles fallback: ${e.message}")
                            imgProfile.setImageResource(R.drawable.ic_person)
                        }
                } else {
                    // For Tutors viewing chats, student images are in Students collection
                    db.collection("Students").document(otherUserId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val fallbackBase64 = doc.getString("ProfileImageBase64").orEmpty()
                            val fallbackName = listOfNotNull(doc.getString("Name"), doc.getString("Surname")).joinToString(" ")
                            if (fallbackName.isNotBlank() && nameFromModel.isNullOrBlank()) {
                                chat.StudentName = fallbackName
                                txtName.text = fallbackName
                            }
                            if (fallbackBase64.isNotBlank()) {
                                chat.StudentImageBase64 = fallbackBase64
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        imgProfile.loadProfileImage(fallbackBase64)
                                    } catch (ex: Exception) {
                                        android.util.Log.e("ChatAdapter", "Fallback load failed for ${chat.ChatId}: ${ex.message}")
                                        imgProfile.setImageResource(R.drawable.ic_person)
                                    }
                                }
                            } else {
                                imgProfile.setImageResource(R.drawable.ic_person)
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("ChatAdapter", "Failed to fetch Student fallback: ${e.message}")
                            imgProfile.setImageResource(R.drawable.ic_person)
                        }
                }
            }

            // Last message preview
            if (chat.Messages.isNotEmpty()) {
                val lastMsg = chat.Messages.last()
                val senderLabel =
                    if (currentUserRole == "Student" && lastMsg.SenderId == chat.StudentId) "You: "
                    else if (currentUserRole == "Tutor" && lastMsg.SenderId == chat.TutorId) "You: "
                    else ""
                txtLastMessage.text = "$senderLabel${lastMsg.MessageText}"
            } else {
                txtLastMessage.text = "No messages yet"
            }

            // Unread indicator logic: check current authenticated user id
            val authUserId = FirebaseAuth.getInstance().currentUser?.uid
            val shouldShowUnread = authUserId != null && chat.UnreadBy.contains(authUserId)

            if (shouldShowUnread) {
                if (unreadDot.visibility != View.VISIBLE) fadeIn(unreadDot)
                unreadDot.visibility = View.VISIBLE
            } else {
                if (unreadDot.visibility == View.VISIBLE) fadeOut(unreadDot)
                unreadDot.visibility = View.GONE
            }

            // Click handler
            itemView.setOnClickListener { onChatClick(chat) }
        }

        // Fade animations for unread dot
        private fun fadeIn(view: View) {
            val anim = AlphaAnimation(0f, 1f)
            anim.duration = 250
            view.startAnimation(anim)
        }

        private fun fadeOut(view: View) {
            val anim = AlphaAnimation(1f, 0f)
            anim.duration = 200
            view.startAnimation(anim)
        }
    }
}
