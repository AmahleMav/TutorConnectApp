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
            val collection = if (currentUserRole == "Student") "Tutors" else "Students"

            // Load name + image
            db.collection(collection).document(otherUserId)
                .get()
                .addOnSuccessListener { doc ->
                    val name = "${doc.getString("Name") ?: "Unknown"} ${doc.getString("Surname") ?: ""}".trim()
                    val imageBase64 = doc.getString("ProfileImageBase64")

                    txtName.text = name
                    CoroutineScope(Dispatchers.Main).launch {
                        imgProfile.loadProfileImage(imageBase64)
                    }
                }
                .addOnFailureListener {
                    txtName.text = "Unknown"
                    imgProfile.setImageResource(R.drawable.ic_person)
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

            // Unread indicator logic (MVC style)
            val shouldShowUnread = when (currentUserRole) {
                "Student" -> chat.UnreadBy.contains(chat.StudentId)
                "Tutor" -> chat.UnreadBy.contains(chat.TutorId)
                else -> false
            }

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
