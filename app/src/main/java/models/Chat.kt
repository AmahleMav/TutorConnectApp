package models

import com.google.firebase.Timestamp

data class Chat(
    var ChatId: String = "",
    var StudentId: String = "",
    var TutorId: String = "",
    var Messages: MutableList<Message> = mutableListOf(),
    var CreatedAt: Timestamp = Timestamp.now(),
    var LastReadByStudent: Timestamp? = null,
    var UnreadBy: MutableList<String> = mutableListOf(), // <- Changed from Any? to MutableList<String>

    var TutorName: String? = null,
    var TutorImageBase64: String? = null,

    var StudentName: String? = null,
    var StudentImageBase64: String? = null
)
