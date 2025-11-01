package models

data class TimeSlot(
    var slotId: String = "",
    var tutorId: String = "",
    var day: String = "",
    var time: String = "",
    var sessionType: String = "",
    var isAvailable: Boolean = true,
    var maxStudents: Int = 1,
    var groupPricePerHour: Double = 0.0,
    var oneOnOnePricePerHour: Double = 0.0
)
