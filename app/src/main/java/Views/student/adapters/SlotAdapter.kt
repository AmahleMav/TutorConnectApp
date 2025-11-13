package Views.student.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.android.material.button.MaterialButton
import models.TimeSlot

class SlotAdapter(
    private val slots: List<TimeSlot>,
    private val onBookClicked: (TimeSlot) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private lateinit var context: Context

    class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtDayTime: TextView = itemView.findViewById(R.id.txtDayTime)
        val txtSessionType: TextView = itemView.findViewById(R.id.txtSessionType)
        val txtAvailability: TextView = itemView.findViewById(R.id.txtAvailability)
        val btnBookSlot: MaterialButton = itemView.findViewById(R.id.btnBookSlot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]

        // Display day and time
        holder.txtDayTime.text = "${slot.day} • ${slot.time}"

        // Display session type
        holder.txtSessionType.text = "Session Type: ${slot.sessionType}"

        // Display availability
        if (slot.isAvailable) {
            holder.txtAvailability.text = "Available"
            holder.txtAvailability.setTextColor(ContextCompat.getColor(context, R.color.green_success))
            holder.btnBookSlot.isEnabled = true
            holder.btnBookSlot.alpha = 1f
        } else {
            holder.txtAvailability.text = "Booked"
            holder.txtAvailability.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            holder.btnBookSlot.isEnabled = false
            holder.btnBookSlot.alpha = 0.6f
        }

        // Click listener
        holder.btnBookSlot.setOnClickListener {
            if (slot.isAvailable) {
                if (slot.tutorId.isNotBlank()) {
                    onBookClicked(slot)
                } else {
                    android.widget.Toast.makeText(context, "Tutor not found for this slot.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int = slots.size
}
