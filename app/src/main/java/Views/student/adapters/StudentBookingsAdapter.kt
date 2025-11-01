package Views.student.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import models.Booking
import java.text.NumberFormat
import java.util.*

class StudentBookingsAdapter(private val bookings: MutableList<Booking>) :
    RecyclerView.Adapter<StudentBookingsAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTutorName: TextView = view.findViewById(R.id.txtTutorName)
        val txtTutorEmail: TextView = view.findViewById(R.id.txtTutorEmail)
        val txtDay: TextView = view.findViewById(R.id.txtDay)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtTime: TextView = view.findViewById(R.id.txtTime)
        val txtType: TextView = view.findViewById(R.id.txtType)
        val txtAmount: TextView = view.findViewById(R.id.txtAmount)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_student, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        // Tutor info
        holder.txtTutorName.text =
            if (booking.tutorName.isNotEmpty()) booking.tutorName else "Tutor: N/A"
        holder.txtTutorEmail.text =
            if (booking.tutorEmail.isNotEmpty()) "Email: ${booking.tutorEmail}" else "Email: N/A"

        // Session day & date
        holder.txtDay.text =
            if (booking.day.isNotEmpty()) "Day: ${booking.day}" else "Day: N/A"
        holder.txtDate.text =
            if (booking.date.isNotEmpty()) "Date of Session: ${booking.date}" else "Date of Session: N/A"

        // Session time
        holder.txtTime.text =
            if (booking.startHour.isNotEmpty() && booking.endHour.isNotEmpty())
                "Time: ${booking.startHour} - ${booking.endHour}"
            else "Time: N/A"

        // Session type
        holder.txtType.text =
            if (booking.sessionType.isNotEmpty()) "Type: ${booking.sessionType}" else "Type: N/A"

        // Price paid formatted in ZAR
        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            .format(booking.pricePaid ?: 0.0)
        holder.txtAmount.text = "Amount: $formattedAmount"

        // Booking status + color
        val status = booking.status.ifEmpty { "N/A" }
        holder.txtStatus.text = "Status: $status"

        when (status.lowercase(Locale.getDefault())) {
            "upcoming" -> holder.txtStatus.setTextColor(Color.parseColor("#FFC107")) // Yellow
            "completed" -> holder.txtStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            "cancelled" -> holder.txtStatus.setTextColor(Color.parseColor("#F44336")) // Red
            else -> holder.txtStatus.setTextColor(Color.BLACK)
        }
    }

    override fun getItemCount(): Int = bookings.size

    /** Update bookings list safely */
    fun updateBookings(newBookings: List<Booking>) {
        bookings.clear()
        bookings.addAll(newBookings)
        notifyDataSetChanged()
    }
}
