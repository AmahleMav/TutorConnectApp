package models

import com.google.firebase.Timestamp

data class Message(
    var MessageText: String = "",
    var SenderId: String = "",
    var SentAt: Timestamp? = null,

)
