package Views.shared.adapters

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import models.Booking

/**
 * Base adapter used by both Tutor and Student booking lists.
 * Adjusted to match your existing layouts (txtTutorName, txtDate, etc.)
 */
abstract class BaseBookingsAdapter(
    protected val bookings: List<Booking>
) : RecyclerView.Adapter<BaseBookingsAdapter.BaseBookingViewHolder>() {

    open class BaseBookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView? = view.findViewById(R.id.txtTutorName)     // ✅ actual ID from your XML
        val txtDate: TextView? = view.findViewById(R.id.txtDate)          // ✅ from your XML
        val txtType: TextView? = view.findViewById(R.id.txtType)          // ✅ from your XML
        val txtStatus: TextView? = view.findViewById(R.id.txtStatus)      // ✅ from your XML
        val txtDay: TextView? = view.findViewById(R.id.txtDay)            // optional (your XML has this)
        val txtTime: TextView? = view.findViewById(R.id.txtTime)          // optional
    }

    override fun getItemCount(): Int = bookings.size

    protected open fun bindCommonData(holder: BaseBookingViewHolder, booking: Booking, role: String) {
        // Display name based on role
        val nameText = if (role == "Student") {
            "Tutor: ${booking.tutorName ?: "N/A"}"
        } else {
            "Student: ${booking.studentName ?: "N/A"}"
        }
        holder.txtName?.text = nameText

        // Format date/time safely
        val date = booking.date ?: ""
        val day = booking.day ?: ""
        val time = booking.time ?: ""
        holder.txtDate?.text = "Date: $date"
        holder.txtDay?.text = "Day: $day"
        holder.txtTime?.text = "Time: $time"

        holder.txtType?.text = "Type: ${booking.sessionType ?: "N/A"}"
        holder.txtStatus?.text = "Status: ${booking.status ?: "Upcoming"}"
    }
}
