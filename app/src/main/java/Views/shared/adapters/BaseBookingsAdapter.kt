package Views.shared.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import models.Booking

abstract class BaseBookingsAdapter(
    protected val bookings: List<Booking>
) : RecyclerView.Adapter<BaseBookingsAdapter.BaseBookingViewHolder>() {

    open class BaseBookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtDateTime: TextView = view.findViewById(R.id.txtDateTime)
        val txtSessionType: TextView = view.findViewById(R.id.txtSessionType)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun getItemCount(): Int = bookings.size

    protected fun bindCommonData(holder: BaseBookingViewHolder, booking: Booking, role: String) {
        holder.txtName.text = if (role == "Student") {
            "Tutor: ${booking.tutorName}"
        } else {
            "Student: ${booking.studentName}"
        }

        holder.txtDateTime.text = "${booking.day}, ${booking.time}"
        holder.txtSessionType.text = "Type: ${booking.sessionType}"
        holder.txtStatus.text = "Status: ${booking.status}"
    }
}
