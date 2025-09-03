package com.example.taskcomm1.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Entity(tableName = "instructions")
data class Instruction(
    @PrimaryKey
    @DocumentId
    val instructionId: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val status: String = "pending" // pending, in_progress, completed
)

