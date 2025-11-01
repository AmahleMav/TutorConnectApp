package Views.shared.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import models.Message

class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String?
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    // 1 = sent by me (right), 0 = received (left)
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].SenderId == currentUserId) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == 1)
            R.layout.item_message_sent
        else
            R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtMessage = itemView.findViewById<TextView>(R.id.txtMessage)
        fun bind(message: Message) {
            txtMessage.text = message.MessageText
        }
    }
}
