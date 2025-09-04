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
            try {
                val client = SupabaseClientProvider.getClient(TaskCommApplication.instance)
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                val rows = postgrest["chat_messages"].select {
                    filter { eq("task_id", taskId) }
                    order(column = "created_at", order = Order.ASCENDING)
                }.decodeList<ChatRow>()
                emit(rows.map { it.toMessage() })
            } catch (_: Exception) {
                emit(emptyList())
            }
        }
    }
    
    suspend fun getMessageCountByTaskId(taskId: String): Int {
        return chatMessageDao.getMessageCountByTaskId(taskId)
    }
    
    // File Upload
    suspend fun uploadFile(fileName: String, inputStream: InputStream, contentType: String): Result<String> {
        return firebaseService.uploadFile(fileName, inputStream, contentType)
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

private fun ChatRow.toMessage(): ChatMessage = ChatMessage(
    messageId = id ?: "",
    taskId = taskId ?: "",
    senderRole = senderRole ?: "",
    senderId = senderId ?: "",
    text = text ?: "",
    mediaUrl = mediaUrl,
    fileType = fileType ?: "text",
    fileName = fileName,
    timestamp = Timestamp.now()
)

