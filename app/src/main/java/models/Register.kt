package com.example.tutorconnect.models

data class Register(
    var userId: String = "",
    var name: String = "",
    var surname: String = "",
    var email: String = "",
    var password: String = "",
    var phoneNumber: String = "",
    var role: String = "",
    var qualifications: String? = null,
    var expertise: String? = null,
    var profileImageBase64: String? = null
)
