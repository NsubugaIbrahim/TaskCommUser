# TaskComm - Task Communication System

A real-time task communication system built with Android Jetpack Compose, Firebase, and Room database.

## Overview

TaskComm consists of two separate apps:
1. **User App** - For creating profiles, sending instructions, and chatting with Admin
2. **Admin App** - For managing users, assigning tasks, and communicating back

Both apps connect to the same real-time backend so that communication and updates happen instantly.

## Features

### User App Features
- **Authentication**: Sign up/login with email/password
- **Dashboard**: Create & edit profile, create instructions, view instruction list
- **Instruction Detail**: View task list from admin, chat inside each task
- **Real-time Communication**: WebSocket-based real-time updates
- **File Sharing**: Upload images and documents in chat
- **Push Notifications**: Get notified when admin replies or adds tasks

### Admin App Features
- **Authentication**: Admin-only login
- **User Management**: View all users, edit profiles, delete accounts
- **Instruction Management**: View instructions from users, create/edit tasks
- **Task Communication**: Real-time multimedia chat per task
- **Email Export**: Export chat/task history to email
- **Multi-User Handling**: Manage multiple users from one admin account

## Tech Stack

- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (local) + Firestore (remote)
- **Authentication**: Firebase Auth
- **Storage**: Firebase Storage
- **Real-time**: Firebase Firestore listeners
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Navigation**: Navigation Compose

## Setup Instructions

### 1. Firebase Setup

1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable Authentication (Email/Password)
3. Create a Firestore database
4. Enable Storage
5. Download the `google-services.json` file and replace the placeholder in `app/google-services.json`

### 2. Build Configuration

The app is configured with:
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### 3. Dependencies

All necessary dependencies are already included in the `build.gradle.kts` files:
- Firebase Auth, Firestore, Storage, Messaging
- Room database with KSP
- Navigation Compose
- Retrofit for networking
- Coil for image loading
- Material 3 components

### 4. Running the App

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Replace the `google-services.json` with your Firebase configuration
4. Build and run the app

## Project Structure

```
app/src/main/java/com/example/taskcomm1/
├── data/
│   ├── local/           # Room database and DAOs
│   ├── models/          # Data classes
│   ├── remote/          # Firebase service
│   └── repository/      # Repository layer
├── ui/
│   ├── screens/         # Compose screens
│   │   ├── auth/        # Authentication screens
│   │   ├── dashboard/   # Dashboard and instruction screens
│   │   └── chat/        # Chat functionality
│   └── viewmodels/      # ViewModels
├── MainActivity.kt      # Main activity
└── TaskCommApplication.kt # Application class
```

## Data Models

### User Profile
```kotlin
data class UserProfile(
    val userId: String,
    val name: String,
    val address: String,
    val businessField: String,
    val email: String,
    val isAdmin: Boolean,
    val createdAt: Timestamp
)
```

### Instruction
```kotlin
data class Instruction(
    val instructionId: String,
    val userId: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Timestamp
)
```

### Task
```kotlin
data class Task(
    val taskId: String,
    val instructionId: String,
    val adminId: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Timestamp,
    val completedAt: Timestamp?
)
```

### Chat Message
```kotlin
data class ChatMessage(
    val messageId: String,
    val taskId: String,
    val senderRole: String,
    val senderId: String,
    val text: String,
    val mediaUrl: String?,
    val fileType: String,
    val fileName: String?,
    val timestamp: Timestamp
)
```

## Security Considerations

- All API calls use HTTPS
- File uploads have size and type restrictions
- Role-based access control (Admin vs User permissions)
- Encrypted token storage
- Input validation on all forms

## Deployment

To build the APK:
1. Run `./gradlew assembleRelease`
2. The APK will be generated in `app/build/outputs/apk/release/`

## Next Steps

1. **Admin App**: Create a separate Android project for the admin app
2. **Push Notifications**: Implement Firebase Cloud Messaging
3. **Email Export**: Add email functionality using SendGrid or similar
4. **File Upload**: Implement camera and file picker functionality
5. **Offline Support**: Enhance offline capabilities with Room database
6. **Testing**: Add unit tests and UI tests

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

# TaskCommUser
