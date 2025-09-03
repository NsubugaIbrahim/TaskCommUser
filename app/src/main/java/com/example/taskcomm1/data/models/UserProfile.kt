package com.example.taskcomm1.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    @DocumentId
    val userId: String = "",
    val name: String = "",
    val address: String = "",
    val businessField: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val email: String = "",
    val isAdmin: Boolean = false
)

