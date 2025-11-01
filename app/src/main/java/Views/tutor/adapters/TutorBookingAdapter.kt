package Views.tutor.adapters

import android.content.Intent
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
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtDateTime: TextView = view.findViewById(R.id.txtDateTime)
        val txtSessionType: TextView = view.findViewById(R.id.txtSessionType)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val btnComplete: Button = view.findViewById(R.id.btnConfirm) // now Complete
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
        val btnChat: Button = view.findViewById(R.id.btnComplete) // now Chat
        val actionLayout: ViewGroup = view.findViewById(R.id.actionLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_tutor, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.txtName.text = "Student: ${booking.studentName ?: "N/A"}"
        holder.txtDateTime.text = "${booking.day}, ${booking.time}"
        holder.txtSessionType.text = "Type: ${booking.sessionType}"
        holder.txtStatus.text = "Status: ${booking.status}"

        // Hide action buttons if cancelled or completed
        holder.actionLayout.visibility =
            if (booking.isCancelled || booking.isCompleted) View.GONE else View.VISIBLE

        // Complete button
        holder.btnComplete.text = if (!booking.isCompleted) "Complete" else "Completed"
        holder.btnComplete.isEnabled = !booking.isCompleted
        holder.btnComplete.setOnClickListener {
            markBookingComplete(booking, holder)
        }

        // Cancel button
        holder.btnCancel.setOnClickListener {
            cancelBooking(booking, holder)
        }

        // Chat button
        holder.btnChat.text = "Chat"
        holder.btnChat.setOnClickListener {
            onActionClick?.invoke(booking, "chat")
        }
    }

    private fun markBookingComplete(booking: Booking, holder: BookingViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("Bookings").document(booking.bookingId)
                    .update(
                        "IsCompleted", true,
                        "Status", "Completed",
                        "CompletionTimestamp", FieldValue.serverTimestamp(),
                        "UpdatedAt", FieldValue.serverTimestamp()
                    ).await()

                // Update local object
                booking.isCompleted = true
                booking.status = "Completed"

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: Completed"
                    holder.btnComplete.text = "Completed"
                    holder.btnComplete.isEnabled = false
                    holder.actionLayout.visibility = View.GONE
                    Toast.makeText(
                        holder.itemView.context,
                        "✅ Session marked complete!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
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
                        "AmountEarned", 0,
                        "HoursWorked", 0,
                        "UpdatedAt", FieldValue.serverTimestamp()
                    ).await()

                // Update local object
                booking.status = "Cancelled"
                booking.isCancelled = true
                booking.amountEarned = 0.0
                booking.hoursWorked = 0

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: Cancelled"
                    holder.actionLayout.visibility = View.GONE
                    Toast.makeText(
                        holder.itemView.context,
                        "⚠️ Session cancelled!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int = bookings.size

    fun updateBookings(newBookings: List<Booking>) {
        bookings.clear()
        bookings.addAll(newBookings)
        notifyDataSetChanged()
    }
}
