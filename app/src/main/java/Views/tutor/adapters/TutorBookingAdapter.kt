package Views.tutor.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import models.Booking

class TutorBookingAdapter(
    private val bookings: MutableList<Booking>,
    private val onActionClick: ((Booking, String) -> Unit)? = null
) : RecyclerView.Adapter<TutorBookingAdapter.BookingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTutorName: TextView = view.findViewById(R.id.txtTutorName)
        val txtTutorEmail: TextView = view.findViewById(R.id.txtTutorEmail)
        val txtDay: TextView = view.findViewById(R.id.txtDay)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtTime: TextView = view.findViewById(R.id.txtTime)
        val txtType: TextView = view.findViewById(R.id.txtType)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val btnConfirm: Button = view.findViewById(R.id.btnConfirm)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
        val btnChat: Button = view.findViewById(R.id.btnComplete)
        val actionLayout: ViewGroup = view.findViewById(R.id.actionLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_tutor, parent, false)
        return BookingViewHolder(view)
    }

    override fun getItemCount(): Int = bookings.size

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.txtTutorName.text = "Student: ${booking.studentName.ifBlank { "N/A" }}"
        holder.txtTutorEmail.text = "Email: ${booking.studentEmail ?: "N/A"}"
        holder.txtDay.text = "Day: ${booking.day ?: ""}"
        holder.txtDate.text = "Date of Session: ${booking.date ?: ""}"
        holder.txtTime.text = "Time: ${booking.time ?: ""}"
        holder.txtType.text = "Type: ${booking.sessionType ?: "One-on-One"}"

        val status = booking.status ?: "Upcoming"
        holder.txtStatus.text = "Status: $status"
        when (status.lowercase()) {
            "completed", "complete" -> holder.txtStatus.setTextColor(Color.parseColor("#2E7D32"))
            "cancelled", "canceled" -> holder.txtStatus.setTextColor(Color.parseColor("#D32F2F"))
            else -> holder.txtStatus.setTextColor(Color.parseColor("#F39C12"))
        }

        holder.actionLayout.visibility =
            if (booking.isCancelled || booking.isCompleted) View.GONE else View.VISIBLE

        holder.btnConfirm.text = if (!booking.isCompleted) "Complete" else "Completed"
        holder.btnConfirm.isEnabled = !booking.isCompleted

        holder.btnConfirm.setOnClickListener { markBookingComplete(booking, holder) }
        holder.btnCancel.setOnClickListener { cancelBooking(booking, holder) }
        holder.btnChat.setOnClickListener { onActionClick?.invoke(booking, "chat") }
    }

    private fun markBookingComplete(booking: Booking, holder: BookingViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("Bookings").document(booking.bookingId)
                    .update(
                        "IsCompleted", true,
                        "Status", "Completed",
                        "CompletionTimestamp", FieldValue.serverTimestamp()
                    ).await()

                booking.isCompleted = true
                booking.status = "Completed"

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: Completed"
                    holder.txtStatus.setTextColor(Color.parseColor("#2E7D32"))
                    holder.btnConfirm.text = "Completed"
                    holder.btnConfirm.isEnabled = false
                    holder.actionLayout.visibility = View.GONE
                    Toast.makeText(
                        holder.itemView.context,
                        "✅ Session marked complete!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        holder.itemView.context,
                        "Failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun cancelBooking(booking: Booking, holder: BookingViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("Bookings").document(booking.bookingId)
                    .update(
                        "Status", "Cancelled",
                        "IsCancelled", true,
                        "UpdatedAt", FieldValue.serverTimestamp()
                    ).await()

                booking.isCancelled = true
                booking.status = "Cancelled"

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: Cancelled"
                    holder.txtStatus.setTextColor(Color.parseColor("#D32F2F"))
                    holder.actionLayout.visibility = View.GONE
                    Toast.makeText(
                        holder.itemView.context,
                        "⚠️ Session cancelled!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        holder.itemView.context,
                        "Failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun updateBookings(newBookings: List<Booking>) {
        bookings.clear()
        bookings.addAll(newBookings)
        notifyDataSetChanged()
    }
}
