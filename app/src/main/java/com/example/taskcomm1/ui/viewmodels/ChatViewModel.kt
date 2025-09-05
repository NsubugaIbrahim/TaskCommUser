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
                repository.observeMessagesByTaskIdWithLocalChanges(taskId, _messages.value).collect { messages ->
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
    
    fun diagnosePermissions(messageId: String) {
        viewModelScope.launch {
            try {
                val diagnostics = repository.diagnosePermissions(messageId)
                android.util.Log.d("UserChatVM", "Permission Diagnostics for message $messageId:")
                android.util.Log.d("UserChatVM", diagnostics)
            } catch (e: Exception) {
                android.util.Log.e("UserChatVM", "Diagnostic failed: ${e.message}")
            }
        }
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            android.util.Log.d("UserChatVM", "Starting delete operation for message: $messageId")
            
            // Store original messages for potential rollback
            val originalMessages = _messages.value.toList()
            
            // Optimistic UI update - remove immediately
            _messages.value = _messages.value.filterNot { it.messageId == messageId }
            
            try {
                val result = repository.deleteMessage(messageId)
                if (result.isFailure) {
                    android.util.Log.e("UserChatVM", "Delete operation failed for message: $messageId")
                    _chatState.value = ChatState.Error(result.exceptionOrNull()?.message ?: "Failed to delete message from server")
                    // Rollback optimistic update
                    _messages.value = originalMessages
                    // Run diagnostics to understand why it failed
                    diagnosePermissions(messageId)
                } else {
                    android.util.Log.d("UserChatVM", "Delete operation successful for message: $messageId")
                    // Add small delay to ensure Supabase operation is committed
                    kotlinx.coroutines.delay(500)
                }
            } catch (e: Exception) {
                android.util.Log.e("UserChatVM", "Delete operation exception: ${e.message}")
                _chatState.value = ChatState.Error(e.message ?: "Delete failed")
                // Rollback optimistic update
                _messages.value = originalMessages
                // Run diagnostics to understand why it failed
                diagnosePermissions(messageId)
            }
        }
    }
    
    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            android.util.Log.d("UserChatVM", "Starting edit operation for message: $messageId")
            android.util.Log.d("UserChatVM", "New text: $newText")
            
            // Store original messages for potential rollback
            val originalMessages = _messages.value.toList()
            
            // Optimistic UI update - change text immediately
            _messages.value = _messages.value.map { message ->
                if (message.messageId == messageId) {
                    message.copy(text = newText + " (edited)")
                } else message
            }
            
            try {
                val result = repository.editMessage(messageId, newText)
                if (result.isFailure) {
                    android.util.Log.e("UserChatVM", "Edit operation failed for message: $messageId")
                    _chatState.value = ChatState.Error(result.exceptionOrNull()?.message ?: "Failed to edit message on server")
                    // Rollback optimistic update
                    _messages.value = originalMessages
                    // Run diagnostics to understand why it failed
                    diagnosePermissions(messageId)
                } else {
                    android.util.Log.d("UserChatVM", "Edit operation successful for message: $messageId")
                    // Add small delay to ensure Supabase operation is committed
                    kotlinx.coroutines.delay(500)
                }
            } catch (e: Exception) {
                android.util.Log.e("UserChatVM", "Edit operation exception: ${e.message}")
                _chatState.value = ChatState.Error(e.message ?: "Edit failed")
                // Rollback optimistic update
                _messages.value = originalMessages
                // Run diagnostics to understand why it failed
                diagnosePermissions(messageId)
            }
        }
    }
}

sealed class ChatState {
    object Idle : ChatState()
    object Loading : ChatState()
    object Success : ChatState()
    data class Error(val message: String) : ChatState()
}

