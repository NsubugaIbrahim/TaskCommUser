package com.example.taskcomm1.data.local

import androidx.room.*
import com.example.taskcomm1.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getMessagesByTaskId(taskId: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessage?
    
    @Query("SELECT * FROM chat_messages WHERE senderId = :senderId ORDER BY timestamp DESC")
    fun getMessagesBySenderId(senderId: String): Flow<List<ChatMessage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Update
    suspend fun updateMessage(message: ChatMessage)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessage)
    
    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)
    
    @Query("DELETE FROM chat_messages WHERE taskId = :taskId")
    suspend fun deleteMessagesByTaskId(taskId: String)
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE taskId = :taskId")
    suspend fun getMessageCountByTaskId(taskId: String): Int
}

