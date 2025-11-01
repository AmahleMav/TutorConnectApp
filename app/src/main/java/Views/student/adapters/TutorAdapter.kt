package Views.student.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.example.tutorconnect.utils.loadProfileImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import models.Tutor

class TutorAdapter(
    private val tutors: List<Tutor>,
    private val onItemClick: (Tutor) -> Unit
) : RecyclerView.Adapter<TutorAdapter.TutorViewHolder>() {

    inner class TutorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgTutor: ImageView = itemView.findViewById(R.id.imgTutor)
        private val txtName: TextView = itemView.findViewById(R.id.txtTutorName)
        private val txtExpertise: TextView = itemView.findViewById(R.id.txtTutorExpertise)
        private val txtHourlyRate: TextView = itemView.findViewById(R.id.txtTutorHourlyRate) // <-- new

        fun bind(tutor: Tutor) {
            txtName.text = "${tutor.Name} ${tutor.Surname}"
            txtExpertise.text = "Expertise: ${tutor.Expertise.ifEmpty { "Not specified" }}"
            txtHourlyRate.text = "R ${tutor.OneOnOneHourlyRate.toInt()} / hr"

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    imgTutor.loadProfileImage(tutor.ProfileImageBase64)
                } catch (e: Exception) {
                    imgTutor.setImageResource(R.drawable.ic_person)
                    Log.e("TutorAdapter", "❌ Image decode error for ${tutor.Name}: ${e.message}")
                }
            }

            itemView.setOnClickListener { onItemClick(tutor) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tutor, parent, false)
        return TutorViewHolder(view)
    }

    override fun onBindViewHolder(holder: TutorViewHolder, position: Int) {
        holder.bind(tutors[position])
    }

    override fun getItemCount() = tutors.size
}
