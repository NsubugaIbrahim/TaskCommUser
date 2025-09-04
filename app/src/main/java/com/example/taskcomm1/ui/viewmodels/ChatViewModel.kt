package com.example.taskcomm1.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskcomm1.data.models.ChatMessage
import com.example.taskcomm1.data.repository.TaskCommRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

class ChatViewModel(
    private val repository: TaskCommRepository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    fun loadMessages(taskId: String) {
        viewModelScope.launch {
            _chatState.value = ChatState.Loading
            
            try {
                repository.observeMessagesByTaskId(taskId).collect { messages ->
                    _messages.value = messages
                    _chatState.value = ChatState.Success
                }
            } catch (e: Exception) {
                _chatState.value = ChatState.Error(e.message ?: "Failed to load messages")
            }
        }
    }
    
    fun sendTextMessage(taskId: String, text: String, senderId: String, senderRole: String) {
        viewModelScope.launch {
            // Optimistic UI update
            val optimistic = ChatMessage(
                messageId = "local-" + System.currentTimeMillis(),
                taskId = taskId,
                senderId = senderId,
                senderRole = senderRole,
                text = text,
                fileType = "text"
            )
            _messages.value = _messages.value + optimistic
            _chatState.value = ChatState.Success

            val result = repository.sendMessage(
                optimistic.copy(messageId = "", /* server will assign id */)
            )

            if (result.isFailure) {
                // Roll back optimistic message on failure
                _messages.value = _messages.value.filterNot { it.messageId == optimistic.messageId }
                _chatState.value = ChatState.Error(result.exceptionOrNull()?.message ?: "Failed to send message")
            }
        }
    }
    
    fun sendFileMessage(
        taskId: String,
        senderId: String,
        senderRole: String,
        fileName: String,
        inputStream: InputStream,
        contentType: String
    ) {
        viewModelScope.launch {
            _chatState.value = ChatState.Success
            // Optimistic UI entry
            val optimistic = ChatMessage(
                messageId = "local-" + System.currentTimeMillis(),
                taskId = taskId,
                senderId = senderId,
                senderRole = senderRole,
                text = "File: $fileName",
                fileType = if (contentType.startsWith("image/")) "image" else "document",
                fileName = fileName
            )
            _messages.value = _messages.value + optimistic

            try {
                val uploadResult = repository.uploadFile(fileName, inputStream, contentType)
                
                if (uploadResult.isSuccess) {
                    val mediaUrl = uploadResult.getOrNull() ?: ""
                    val fileType = when {
                        contentType.startsWith("image/") -> "image"
                        else -> "document"
                    }
                    
                    val message = optimistic.copy(mediaUrl = mediaUrl, fileType = fileType)
                    val sendResult = repository.sendMessage(message.copy(messageId = ""))
                    
                    _chatState.value = if (sendResult.isSuccess) {
                        ChatState.Success
                    } else {
                        // rollback
                        _messages.value = _messages.value.filterNot { it.messageId == optimistic.messageId }
                        ChatState.Error(sendResult.exceptionOrNull()?.message ?: "Failed to send file message")
                    }
                } else {
                    // rollback
                    _messages.value = _messages.value.filterNot { it.messageId == optimistic.messageId }
                    _chatState.value = ChatState.Error(uploadResult.exceptionOrNull()?.message ?: "Failed to upload file")
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.filterNot { it.messageId == optimistic.messageId }
                _chatState.value = ChatState.Error(e.message ?: "Failed to send file message")
            }
        }
    }
    
    fun clearError() {
        if (_chatState.value is ChatState.Error) {
            _chatState.value = ChatState.Idle
        }
    }
}

sealed class ChatState {
    object Idle : ChatState()
    object Loading : ChatState()
    object Success : ChatState()
    data class Error(val message: String) : ChatState()
}

