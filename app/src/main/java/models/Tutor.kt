package models

data class Tutor(
    var UserId: String = "",
    var Name: String = "",
    var Surname: String = "",
    var Email: String = "",
    var PhoneNumber: String = "",
    var Role: String = "Tutor",
    var Expertise: String = "",
    var Qualifications: String = "",
    var ProfileImageBase64: String = "",
    var Description: String = "",
    var AverageRating: Double = 0.0,
    var OneOnOneHourlyRate: Double
)