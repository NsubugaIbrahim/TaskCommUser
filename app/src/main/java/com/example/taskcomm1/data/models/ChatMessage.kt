package com.example.taskcomm1.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    @DocumentId
    val messageId: String = "",
    val taskId: String = "",
    val senderRole: String = "", // user, admin
    val senderId: String = "",
    val text: String = "",
    val mediaUrl: String? = null,
    val fileType: String = "text", // image, document, text
    val fileName: String? = null,
    val fileSize: Long? = null,
    val timestamp: Timestamp = Timestamp.now()
)

