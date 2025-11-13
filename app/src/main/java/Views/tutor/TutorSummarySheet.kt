package com.example.tutorconnect.Views.tutor

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.tutorconnect.R
import com.example.tutorconnect.models.TutorSummary
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TutorSummarySheet(
    private val summary: TutorSummary,
    private val sparkline: android.graphics.Bitmap? = null,
    private val onOpenDetails: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_tutor_summary, null)
        dialog.setContentView(view)
        setupUI(view)
        return dialog
    }

    private fun setupUI(view: View) {
        val cardHours = view.findViewById<View>(R.id.cardHours)
        val cardSessions = view.findViewById<View>(R.id.cardSessions)
        val cardRating = view.findViewById<View>(R.id.cardRating)

        setCardValues(cardHours, summary.totalHours.toString(), "Hours")
        setCardValues(cardSessions, summary.completedSessions.toString(), "Sessions")
        setCardValues(cardRating, summary.averageRating.toString(), "Rating")

        val btnDetails = view.findViewById<Button>(R.id.btnOpenDetails)
        btnDetails.setOnClickListener { onOpenDetails() }
    }

    private fun setCardValues(cardView: View, value: String, label: String) {
        val tvValue = cardView.findViewById<TextView>(R.id.tvStatValue)
        val tvLabel = cardView.findViewById<TextView>(R.id.tvStatLabel)
        tvValue.text = value
        tvLabel.text = label
    }
}
