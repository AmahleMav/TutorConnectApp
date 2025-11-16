# TutorConnect – Mobile App  
*A Smart Tutor Booking System*

TutorConnect is a modern mobile application designed to streamline the process of booking tutoring sessions between students and tutors. The app enables students to browse tutors, 
view availability, book sessions, receive notifications, and maintain learning continuity all from a clean, intuitive mobile interface.

While the platform includes admin dashboards and web-based tools, this README focuses exclusively on the mobile app.

---

## Demo  
Watch the live mobile demo on YouTube:

[![TutorConnect Demo](https://img.youtube.com/vi/OCuXfT2uZ_w/0.jpg)](https://youtu.be/OCuXfT2uZ_w)

---

## Overview

TutorConnect bridges the gap between learners and educators by offering:

- A user-friendly mobile app interface  
- Real-time booking and scheduling  
- Secure communication tools  
- Transparent tutor hour tracking  
- Reliable session management  

The platform ensures a smooth experience for both students and tutors, making academic support more accessible and organized.

---

## Key Features (Mobile App)

### Tutor Browsing
- Students can scroll through a list of tutors.
- Each profile includes:
  - Bio and qualifications  
  - Area of Expertise 
  - Ratings and reviews  
  - Availability  

### Real-Time Availability  
- Firebase-powered dynamic scheduling.  
- Students only see available slots.  
- Tutors can update availability instantly.

### Seamless Session Booking  
- Booking flow: Select Tutor → Choose Slot → Confirm  
- Supports one-on-one and group sessions  
- Students can view session history and upcoming appointments

### Tutor Check-In & Hour Tracking  
- Tutors check into sessions via the app  
- Hours are automatically logged  
- Weekly data syncs with admin dashboards

### In-App Chat & Notifications  
- Real-time messaging  
- Push notifications for:
  - Booking confirmations  
  - Session reminders  
  - Tutor updates  
  - Admin announcements  

### Reviews & Ratings  
- Students rate tutors after sessions  
- Written feedback helps maintain quality

### Admin Dashboard Integration  
*(Admin tools are web-based but use mobile data)*  
Admins can view:
- Tutor activity  
- Bookings  
- Logged hours  
- Ratings and performance metrics

---

Use the credentials below to explore the app:

### **Tutor Test Account**
- **Email:** TutorsAccount@gmail.com  
- **Password:** Test123.

### **Student Test Account**
- **Email:** TestAccount@gmail.com  
- **Password:** Test123.

---

## Tech Stack (Mobile App)

| Category | Technology |
|---------|------------|
| Language | Kotlin |
| Framework | Native Android |
| Architecture | MVVM |
| Authentication | Firebase Authentication |
| Database | Firebase Firestore |
| Storage | Firebase Storage |
| Messaging | Firebase Cloud Messaging |
| UI Components | RecyclerView, Fragments, Material Components |
| Images | Glide / Picasso |

---

## Core Screens

- Login / Register  
- Tutor List  
- Tutor Profile  
- Availability Calendar  
- Booking Confirmation  
- Chat  
- Session History  
- Tutor Check-In  
- Profile & Settings  

---

## Installation & Setup

1. Clone the repository:
   ```sh
   git clone https://github.com/AmahleMav/TutorConnectApp.git
2. Open the project in Android Studio.
3. Configure Firebase:
    - Add google-services.json into the /app folder
    - Enable Firebase Authentication
    - Create Firestore database
    - Enable Firebase Storage
4. Build and run the app:
   ```sh
     Run → Run 'app'

## Future Enhancements
- AI-based tutor matching
- Google Calendar sync
- In-app video calling
- Learning analytics
- Group chat
- Dark mode
- Validation for completed sessions
