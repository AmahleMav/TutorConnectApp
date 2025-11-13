package Views.tutor.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import models.Booking

class HoursLogAdapter(
    private val bookings: List<Booking>,
    private val onItemClick: ((Booking) -> Unit)? = null
) : RecyclerView.Adapter<HoursLogAdapter.HoursLogViewHolder>() {

    class HoursLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtStudent: TextView = itemView.findViewById(R.id.txtStudent)
        val txtSessionType: TextView = itemView.findViewById(R.id.txtSessionType)
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val txtHours: TextView = itemView.findViewById(R.id.txtHours)
        val txtEarned: TextView = itemView.findViewById(R.id.txtEarned)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val txtReview: TextView = itemView.findViewById(R.id.txtReview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoursLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hours_log, parent, false)
        return HoursLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: HoursLogViewHolder, position: Int) {
        val booking = bookings[position]

        // Student name (resolved in activity)
        holder.txtStudent.text = booking.studentName.ifBlank { "Student" }

        // Session type
        val sessionTypeText = if (booking.isGroup) "Group session" else "One-on-one"
        holder.txtSessionType.text = sessionTypeText

        // Date & Time
        holder.txtDate.text = booking.date.ifBlank { "—" }
        holder.txtTime.text = booking.time.ifBlank { "—" }

        // Hours worked (formatted)
        val hoursWorked = booking.hoursWorked.takeIf { it > 0 } ?: 0.0
        holder.txtHours.text = String.format("%.1f hrs", hoursWorked)

        // Earnings (PricePaid)
        val pricePaid = booking.pricePaid ?: 0.0
        holder.txtEarned.text = String.format("R%.2f", pricePaid)

        // Rating
        val ratingFloat = (booking.rating ?: 0.0).toFloat().coerceIn(0f, 5f)
        holder.ratingBar.rating = ratingFloat

        // Review visibility
        val review = booking.comment?.trim().orEmpty()
        if (review.isNotEmpty()) {
            holder.txtReview.visibility = View.VISIBLE
            holder.txtReview.text = review
        } else {
            holder.txtReview.visibility = View.GONE
            holder.txtReview.text = ""
        }

        holder.itemView.setOnClickListener { onItemClick?.invoke(booking) }
    }

    override fun getItemCount(): Int = bookings.size
}
