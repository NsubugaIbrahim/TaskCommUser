package com.example.taskcomm1.data.repository

import com.example.taskcomm1.TaskCommApplication
import com.example.taskcomm1.data.SupabaseClientProvider
import com.example.taskcomm1.data.local.*
import com.example.taskcomm1.data.models.*
import com.example.taskcomm1.data.remote.FirebaseService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.InputStream

class TaskCommRepository(
    private val firebaseService: FirebaseService,
    private val userProfileDao: UserProfileDao,
    private val instructionDao: InstructionDao,
    private val taskDao: TaskDao,
    private val chatMessageDao: ChatMessageDao
) {
    
    // Authentication
    suspend fun signUp(email: String, password: String, name: String): Result<FirebaseUser> {
        return firebaseService.signUp(email, password, name)
    }
    
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return firebaseService.signIn(email, password)
    }
    
    suspend fun signOut() {
        firebaseService.signOut()
    }
    
    fun getCurrentUser(): FirebaseUser? = firebaseService.getCurrentUser()
    
    // User Profile
    suspend fun updateUserProfile(userProfile: UserProfile): Result<Unit> {
        val result = firebaseService.updateUserProfile(userProfile)
        if (result.isSuccess) {
            userProfileDao.insertUser(userProfile)
        }
        return result
    }
    
    suspend fun getUserProfile(userId: String): UserProfile? {
        return userProfileDao.getUserById(userId) ?: run {
            val result = firebaseService.getUserProfile(userId)
            if (result.isSuccess) {
                result.getOrNull()?.let { userProfile ->
                    userProfileDao.insertUser(userProfile)
                    userProfile
                }
            } else null
        }
    }
    
    fun observeAllUsers(): Flow<List<UserProfile>> {
        return firebaseService.observeAllUsers()
    }
    
    // Instructions (Supabase)
    suspend fun createInstruction(instruction: Instruction): Result<String> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val row = InstructionRow(
                userId = instruction.userId,
                title = instruction.title,
                description = instruction.description,
                status = instruction.status
            )
            postgrest["instructions"].insert(row)
            // We don't rely on returned id in the UI; return empty string
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateInstruction(instruction: Instruction): Result<Unit> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val row = InstructionRow(
                id = instruction.instructionId,
                userId = instruction.userId,
                title = instruction.title,
                description = instruction.description,
                status = instruction.status
            )
            postgrest["instructions"].upsert(row)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeInstructionsByUserId(userId: String): Flow<List<Instruction>> {
        return flow {
            try {
                val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                val rows = postgrest["instructions"].select {
                    filter { eq("user_id", userId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<InstructionRow>()
                emit(rows.map { it.toInstruction() })
            } catch (_: Exception) {
                emit(emptyList())
            }
        }
    }
    
    fun observeAllInstructions(): Flow<List<Instruction>> {
        return flow {
            try {
                val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                val rows = postgrest["instructions"].select {
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<InstructionRow>()
                emit(rows.map { it.toInstruction() })
            } catch (_: Exception) {
                emit(emptyList())
            }
        }
    }
    
    suspend fun getInstructionById(instructionId: String): Instruction? {
        return instructionDao.getInstructionById(instructionId) ?: run {
            // Could implement Firebase fetch here if needed
            null
        }
    }
    
    // Tasks
    suspend fun createTask(task: Task): Result<String> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val row = TaskRowInsert(
                instructionId = task.instructionId,
                title = task.title,
                description = task.description,
                status = task.status
            )
            postgrest["tasks"].insert(row)
            // optional: fetch back id if needed
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateTask(task: Task): Result<Unit> {
        val result = firebaseService.updateTask(task)
        if (result.isSuccess) {
            taskDao.updateTask(task)
        }
        return result
    }
    
    fun observeTasksByInstructionId(instructionId: String): Flow<List<Task>> {
        return flow {
            try {
                val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                val rows = postgrest["tasks"].select {
                    filter { eq("instruction_id", instructionId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<TaskRow>()
                emit(rows.map { it.toTask() })
            } catch (_: Exception) {
                emit(emptyList())
            }
        }
    }
    
    suspend fun getTaskById(taskId: String): Task? {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val rows = postgrest["tasks"].select {
                filter { eq("id", taskId) }
                limit(1)
            }.decodeList<TaskRow>()
            rows.firstOrNull()?.toTask()
        } catch (_: Exception) { null }
    }
    
    suspend fun updateTaskStatus(taskId: String, status: String): Result<Unit> {
        val task = taskDao.getTaskById(taskId)
        return if (task != null) {
            val updatedTask = task.copy(status = status)
            updateTask(updatedTask)
        } else {
            Result.failure(Exception("Task not found"))
        }
    }
    
    // Chat Messages
    suspend fun sendMessage(message: ChatMessage): Result<String> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val row = ChatInsertRow(
                taskId = message.taskId,
                senderId = message.senderId,
                text = message.text,
                mediaUrl = message.mediaUrl,
                fileType = message.fileType,
                fileName = message.fileName,
                senderRole = message.senderRole
            )
            postgrest["chat_messages"].insert(row)
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeMessagesByTaskId(taskId: String): Flow<List<ChatMessage>> {
        return flow {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            while (true) {
                try {
                    val rows = postgrest["chat_messages"].select {
                        filter { eq("task_id", taskId) }
                        order(column = "created_at", order = Order.ASCENDING)
                    }.decodeList<ChatRow>()
                    
                    android.util.Log.d("UserChatRepo", "Fetched ${rows.size} messages for task $taskId")
                    rows.forEachIndexed { index, row ->
                        android.util.Log.d("UserChatRepo", "Message $index: id=${row.id}, createdAt=${row.createdAt}, text=${row.text?.take(20)}")
                    }
                    
                    emit(rows.map { it.toMessage() })
                } catch (_: Exception) {
                    emit(emptyList())
                }
                delay(1500) // Smooth polling like admin app
            }
        }
    }
    
    // Smart polling that respects local changes
    fun observeMessagesByTaskIdWithLocalChanges(
        taskId: String,
        localMessages: List<ChatMessage>
    ): Flow<List<ChatMessage>> {
        return flow {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            while (true) {
                try {
                    // Test table access first
                    try {
                        val testSelect = postgrest["chat_messages"].select {
                            limit(1)
                        }.decodeList<ChatRow>()
                        android.util.Log.d("UserChatRepo", "Table access test passed: ${testSelect.size} rows")
                    } catch (e: Exception) {
                        android.util.Log.e("UserChatRepo", "Table access test failed: ${e.message}")
                    }
                    
                    val rows = postgrest["chat_messages"].select {
                        filter { eq("task_id", taskId) }
                        order(column = "created_at", order = Order.ASCENDING)
                    }.decodeList<ChatRow>()
                    
                    android.util.Log.d("UserChatRepo", "Smart polling: Fetched ${rows.size} messages for task $taskId")
                    rows.forEachIndexed { index, row ->
                        android.util.Log.d("UserChatRepo", "Smart polling Message $index: id=${row.id}, createdAt=${row.createdAt}, text=${row.text?.take(20)}")
                    }
                    
                    // Merge server data with local optimistic changes
                    val serverMessages = rows.map { it.toMessage() }
                    val mergedMessages = mergeLocalAndServerMessages(localMessages, serverMessages)
                    
                    emit(mergedMessages)
                } catch (_: Exception) {
                    emit(localMessages) // Keep local changes if fetch fails
                }
                delay(1500)
            }
        }
    }
    
    private fun mergeLocalAndServerMessages(
        localMessages: List<ChatMessage>, 
        serverMessages: List<ChatMessage>
    ): List<ChatMessage> {
        val localMap = localMessages.associateBy { it.messageId }.toMutableMap()
        val serverMap = serverMessages.associateBy { it.messageId }
        
        val merged = mutableListOf<ChatMessage>()
        
        // Add all server messages
        serverMessages.forEach { serverMsg ->
            val localMsg = localMap[serverMsg.messageId]
            if (localMsg != null && localMsg.text.endsWith(" (edited)")) {
                // Keep local edited version
                merged.add(localMsg)
            } else {
                merged.add(serverMsg)
                // Remove from local map to avoid duplicates
                localMap.remove(serverMsg.messageId)
            }
        }
        
        // Add any local messages that don't exist on server (optimistic sends)
        localMessages.forEach { localMsg ->
            if (!serverMap.containsKey(localMsg.messageId)) {
                merged.add(localMsg)
            }
        }
        
        return merged.sortedBy { it.timestamp }
    }
    
    suspend fun getMessageCountByTaskId(taskId: String): Int {
        return chatMessageDao.getMessageCountByTaskId(taskId)
    }
    
    // Comprehensive diagnostic function for RLS and permissions
    suspend fun diagnosePermissions(messageId: String): String {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val auth = client.pluginManager.getPlugin(io.github.jan.supabase.gotrue.Auth)
            
            val diagnostics = StringBuilder()
            
            // 1. Check authentication
            val session = auth.currentSessionOrNull()
            diagnostics.appendLine("=== AUTHENTICATION DIAGNOSTICS ===")
            diagnostics.appendLine("User ID: ${session?.user?.id}")
            diagnostics.appendLine("User Email: ${session?.user?.email}")
            diagnostics.appendLine("User Role: ${session?.user?.appMetadata?.get("role")}")
            diagnostics.appendLine("Session Valid: ${session != null}")
            diagnostics.appendLine()
            
            // 2. Check if we can read the specific message
            diagnostics.appendLine("=== MESSAGE ACCESS DIAGNOSTICS ===")
            try {
                val message = postgrest["chat_messages"].select {
                    filter { eq("id", messageId) }
                    limit(1)
                }.decodeList<ChatRow>()
                
                if (message.isNotEmpty()) {
                    val msg = message.first()
                    diagnostics.appendLine("Message found: YES")
                    diagnostics.appendLine("Message ID: ${msg.id}")
                    diagnostics.appendLine("Task ID: ${msg.taskId}")
                    diagnostics.appendLine("Sender ID: ${msg.senderId}")
                    diagnostics.appendLine("Sender Role: ${msg.senderRole}")
                    diagnostics.appendLine("Text: ${msg.text}")
                    diagnostics.appendLine("Created: ${msg.createdAt}")
                } else {
                    diagnostics.appendLine("Message found: NO")
                }
            } catch (e: Exception) {
                diagnostics.appendLine("Message read error: ${e.message}")
            }
            diagnostics.appendLine()
            
            // 3. Check RLS policies by trying different operations
            diagnostics.appendLine("=== RLS POLICY DIAGNOSTICS ===")
            
            // Try to read all messages (should be restricted by RLS)
            try {
                val allMessages = postgrest["chat_messages"].select {
                    limit(10)
                }.decodeList<ChatRow>()
                diagnostics.appendLine("Can read all messages: YES (${allMessages.size} messages)")
            } catch (e: Exception) {
                diagnostics.appendLine("Can read all messages: NO - ${e.message}")
            }
            
            // Try to read messages from same task
            try {
                val message = postgrest["chat_messages"].select {
                    filter { eq("id", messageId) }
                    limit(1)
                }.decodeList<ChatRow>()
                
                if (message.isNotEmpty()) {
                    val taskMessages = postgrest["chat_messages"].select {
                        filter { eq("task_id", message.first().taskId ?: "") }
                    }.decodeList<ChatRow>()
                    diagnostics.appendLine("Can read task messages: YES (${taskMessages.size} messages)")
                }
            } catch (e: Exception) {
                diagnostics.appendLine("Can read task messages: NO - ${e.message}")
            }
            
            // Try to read messages from same sender
            try {
                val message = postgrest["chat_messages"].select {
                    filter { eq("id", messageId) }
                    limit(1)
                }.decodeList<ChatRow>()
                
                if (message.isNotEmpty()) {
                    val senderMessages = postgrest["chat_messages"].select {
                        filter { eq("sender_id", message.first().senderId ?: "") }
                    }.decodeList<ChatRow>()
                    diagnostics.appendLine("Can read sender messages: YES (${senderMessages.size} messages)")
                }
            } catch (e: Exception) {
                diagnostics.appendLine("Can read sender messages: NO - ${e.message}")
            }
            diagnostics.appendLine()
            
            // 4. Test write permissions
            diagnostics.appendLine("=== WRITE PERMISSION DIAGNOSTICS ===")
            
            // Try to insert a test message
            try {
                val testMessage = ChatInsertRow(
                    taskId = "test-task-${System.currentTimeMillis()}",
                    senderId = session?.user?.id ?: "test-sender",
                    senderRole = "user",
                    text = "Test message for write permission",
                    fileType = "text"
                )
                
                val insertResult = postgrest["chat_messages"].insert(testMessage)
                diagnostics.appendLine("Can insert messages: YES")
                
                // Clean up test message
                try {
                    postgrest["chat_messages"].delete {
                        filter { eq("text", "Test message for write permission") }
                    }
                    diagnostics.appendLine("Can delete test messages: YES")
                } catch (e: Exception) {
                    diagnostics.appendLine("Can delete test messages: NO - ${e.message}")
                }
            } catch (e: Exception) {
                diagnostics.appendLine("Can insert messages: NO - ${e.message}")
            }
            
            // Try to update the specific message
            try {
                val updateResult = postgrest["chat_messages"].update(mapOf(
                    "text" to "Test update for permission check"
                )) {
                    filter { eq("id", messageId) }
                }
                diagnostics.appendLine("Can update specific message: YES")
                
                // Revert the change
                postgrest["chat_messages"].update(mapOf(
                    "text" to "Original message text"
                )) {
                    filter { eq("id", messageId) }
                }
            } catch (e: Exception) {
                diagnostics.appendLine("Can update specific message: NO - ${e.message}")
            }
            
            // Try to delete the specific message
            try {
                val deleteResult = postgrest["chat_messages"].delete {
                    filter { eq("id", messageId) }
                }
                diagnostics.appendLine("Can delete specific message: YES")
            } catch (e: Exception) {
                diagnostics.appendLine("Can delete specific message: NO - ${e.message}")
            }
            
            diagnostics.toString()
        } catch (e: Exception) {
            "Diagnostic failed: ${e.message}\n${e.stackTraceToString()}"
        }
    }
    
    // File Upload
    suspend fun uploadFile(fileName: String, inputStream: InputStream, contentType: String): Result<String> {
        return firebaseService.uploadFile(fileName, inputStream, contentType)
    }
    
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            
            android.util.Log.d("UserChatRepo", "Starting delete operation for message: $messageId")
            
            // First check if message exists and get details
            val existingMessage = postgrest["chat_messages"].select {
                filter { eq("id", messageId) }
                limit(1)
            }.decodeList<ChatRow>()
            
            if (existingMessage.isEmpty()) {
                android.util.Log.w("UserChatRepo", "Message not found for deletion: $messageId")
                return Result.success(Unit) // Already deleted
            }
            
            val message = existingMessage.first()
            android.util.Log.d("UserChatRepo", "Found message for deletion: $messageId")
            android.util.Log.d("UserChatRepo", "Message details: taskId=${message.taskId}, senderId=${message.senderId}, senderRole=${message.senderRole}")
            
            // Check authentication and permissions
            try {
                val auth = client.pluginManager.getPlugin(io.github.jan.supabase.gotrue.Auth)
                val session = auth.currentSessionOrNull()
                android.util.Log.d("UserChatRepo", "Current user: ${session?.user?.id}, role: ${session?.user?.appMetadata?.get("role")}")
            } catch (e: Exception) {
                android.util.Log.e("UserChatRepo", "Auth check failed: ${e.message}")
            }
            
            // Attempt the delete operation
            android.util.Log.d("UserChatRepo", "Attempting to delete message: $messageId")
            val result = postgrest["chat_messages"].delete {
                filter { eq("id", messageId) }
            }
            android.util.Log.d("UserChatRepo", "Delete operation completed: $result")
            
            // Wait a bit for the operation to be processed
            kotlinx.coroutines.delay(200)
            
            // Verify deletion by trying to fetch the message
            val verifyResult = postgrest["chat_messages"].select {
                filter { eq("id", messageId) }
                limit(1)
            }.decodeList<ChatRow>()
            
            if (verifyResult.isNotEmpty()) {
                android.util.Log.e("UserChatRepo", "Delete verification failed: message still exists: $messageId")
                android.util.Log.e("UserChatRepo", "Remaining message text: ${verifyResult.first().text}")
                return Result.failure(Exception("Message still exists after delete"))
            } else {
                android.util.Log.d("UserChatRepo", "Delete verification successful: message no longer exists")
            }
            
            // Additional verification: check if message is gone from task messages
            val taskMessages = postgrest["chat_messages"].select {
                filter { eq("task_id", message.taskId ?: "") }
            }.decodeList<ChatRow>()
            val messageStillInTask = taskMessages.any { it.id == messageId }
            if (messageStillInTask) {
                android.util.Log.e("UserChatRepo", "Message still appears in task messages after delete")
                return Result.failure(Exception("Message still appears in task messages after delete"))
            } else {
                android.util.Log.d("UserChatRepo", "Message successfully removed from task messages")
            }
            
            android.util.Log.d("UserChatRepo", "Successfully deleted message: $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserChatRepo", "Delete operation failed: ${e.message}")
            android.util.Log.e("UserChatRepo", "Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun editMessage(messageId: String, newText: String): Result<Unit> {
        return try {
            val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            
            android.util.Log.d("UserChatRepo", "Starting edit operation for message: $messageId")
            android.util.Log.d("UserChatRepo", "New text: $newText")
            
            // First check if message exists and get current text
            val existingMessage = postgrest["chat_messages"].select {
                filter { eq("id", messageId) }
                limit(1)
            }.decodeList<ChatRow>()
            
            if (existingMessage.isEmpty()) {
                android.util.Log.w("UserChatRepo", "Message not found for edit: $messageId")
                return Result.failure(Exception("Message not found for edit"))
            }
            
            val message = existingMessage.first()
            android.util.Log.d("UserChatRepo", "Found message for edit: $messageId")
            android.util.Log.d("UserChatRepo", "Current text: ${message.text}")
            android.util.Log.d("UserChatRepo", "Message details: taskId=${message.taskId}, senderId=${message.senderId}, senderRole=${message.senderRole}")
            
            // Check authentication and permissions
            try {
                val auth = client.pluginManager.getPlugin(io.github.jan.supabase.gotrue.Auth)
                val session = auth.currentSessionOrNull()
                android.util.Log.d("UserChatRepo", "Current user: ${session?.user?.id}, role: ${session?.user?.appMetadata?.get("role")}")
            } catch (e: Exception) {
                android.util.Log.e("UserChatRepo", "Auth check failed: ${e.message}")
            }
            
            // Attempt the update operation
            android.util.Log.d("UserChatRepo", "Attempting to update message: $messageId")
            val textWithEditedFlag = newText + " (edited)"
            val result = postgrest["chat_messages"].update(mapOf(
                "text" to textWithEditedFlag
            )) {
                filter { eq("id", messageId) }
            }
            android.util.Log.d("UserChatRepo", "Update operation completed: $result")
            
            // Wait a bit for the operation to be processed
            kotlinx.coroutines.delay(200)
            
            // Verify edit by fetching the message
            val verifyResult = postgrest["chat_messages"].select {
                filter { eq("id", messageId) }
                limit(1)
            }.decodeList<ChatRow>()
            
            if (verifyResult.isEmpty()) {
                android.util.Log.e("UserChatRepo", "Edit verification failed: message no longer exists: $messageId")
                return Result.failure(Exception("Message no longer exists after edit"))
            }
            
            val updatedMessage = verifyResult.first()
            if (updatedMessage.text != textWithEditedFlag) {
                android.util.Log.e("UserChatRepo", "Edit verification failed: text not updated")
                android.util.Log.e("UserChatRepo", "Expected: $textWithEditedFlag")
                android.util.Log.e("UserChatRepo", "Actual: ${updatedMessage.text}")
                return Result.failure(Exception("Message text not updated after edit"))
            } else {
                android.util.Log.d("UserChatRepo", "Edit verification successful: text updated correctly")
            }
            
            // Additional verification: check if message appears correctly in task messages
            val taskMessages = postgrest["chat_messages"].select {
                filter { eq("task_id", message.taskId ?: "") }
            }.decodeList<ChatRow>()
            val updatedMessageInTask = taskMessages.find { it.id == messageId }
            if (updatedMessageInTask?.text != textWithEditedFlag) {
                android.util.Log.e("UserChatRepo", "Message not updated correctly in task messages")
                return Result.failure(Exception("Message not updated correctly in task messages"))
            } else {
                android.util.Log.d("UserChatRepo", "Message successfully updated in task messages")
            }
            
            android.util.Log.d("UserChatRepo", "Successfully edited message: $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserChatRepo", "Edit operation failed: ${e.message}")
            android.util.Log.e("UserChatRepo", "Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    // Local Database Operations
    suspend fun syncLocalData() {
        // Sync local data with Firebase when needed
        // This could be called on app startup or when network is restored
    }
    
    suspend fun clearLocalData() {
        // Clear local data when user signs out
        userProfileDao.getAllUsers().collect { users ->
            users.forEach { userProfileDao.deleteUser(it) }
        }
        instructionDao.getAllInstructions().collect { instructions ->
            instructions.forEach { instructionDao.deleteInstruction(it) }
        }
        taskDao.getAllTasks().collect { tasks ->
            tasks.forEach { taskDao.deleteTask(it) }
        }
    }
}

@Serializable
private data class InstructionRow(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("status") val status: String? = null
)

private fun InstructionRow.toInstruction(): Instruction {
    return Instruction(
        instructionId = id ?: "",
        userId = userId ?: "",
        title = title ?: "",
        description = description ?: "",
        createdAt = Timestamp.now(),
        status = status ?: "pending"
    )
}

@Serializable
private data class TaskRow(
    @SerialName("id") val id: String? = null,
    @SerialName("instruction_id") val instructionId: String? = null,
    @SerialName("admin_id") val adminId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class TaskRowInsert(
    @SerialName("instruction_id") val instructionId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String
)

private fun TaskRow.toTask(): Task = Task(
    taskId = id ?: "",
    instructionId = instructionId ?: "",
    adminId = adminId ?: "",
    title = title ?: "",
    description = description ?: "",
    status = status ?: "pending",
    createdAt = Timestamp.now()
)

@Serializable
private data class ChatRow(
    @SerialName("id") val id: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_role") val senderRole: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class ChatInsertRow(
    @SerialName("task_id") val taskId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("text") val text: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("file_type") val fileType: String = "text",
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("sender_role") val senderRole: String
)

private fun ChatRow.toMessage(): ChatMessage {
    val timestamp = try {
        if (createdAt != null) {
            android.util.Log.d("UserChatRepo", "Parsing timestamp: $createdAt")
            // Parse ISO 8601 timestamp from Supabase using OffsetDateTime like admin app
            val odt = java.time.OffsetDateTime.parse(createdAt)
            val date = java.util.Date.from(odt.toInstant())
            val result = Timestamp(date)
            android.util.Log.d("UserChatRepo", "Parsed timestamp successfully: ${result.toDate()}")
            result
        } else {
            android.util.Log.w("UserChatRepo", "CreatedAt is null, using now()")
            Timestamp.now()
        }
    } catch (e: Exception) {
        android.util.Log.e("UserChatRepo", "Failed to parse timestamp: $createdAt, error: ${e.message}, using now()")
        Timestamp.now()
    }
    
    return ChatMessage(
        messageId = id ?: "",
        taskId = taskId ?: "",
        senderRole = senderRole ?: "",
        senderId = senderId ?: "",
        text = text ?: "",
        mediaUrl = mediaUrl,
        fileType = fileType ?: "text",
        fileName = fileName,
        timestamp = timestamp
    )
}


