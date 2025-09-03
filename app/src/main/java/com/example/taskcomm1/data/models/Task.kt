package com.example.taskcomm1.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    @DocumentId
    val taskId: String = "",
    val instructionId: String = "",
    val adminId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "pending", // pending, completed
    val createdAt: Timestamp = Timestamp.now(),
    val completedAt: Timestamp? = null
)

