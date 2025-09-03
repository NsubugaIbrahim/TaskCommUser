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
        val result = firebaseService.createTask(task)
        if (result.isSuccess) {
            val taskId = result.getOrNull() ?: ""
            val updatedTask = task.copy(taskId = taskId)
            taskDao.insertTask(updatedTask)
        }
        return result
    }
    
    suspend fun updateTask(task: Task): Result<Unit> {
        val result = firebaseService.updateTask(task)
        if (result.isSuccess) {
            taskDao.updateTask(task)
        }
        return result
    }
    
    fun observeTasksByInstructionId(instructionId: String): Flow<List<Task>> {
        return firebaseService.observeTasksByInstructionId(instructionId)
    }
    
    suspend fun getTaskById(taskId: String): Task? {
        return taskDao.getTaskById(taskId)
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
        val result = firebaseService.sendMessage(message)
        if (result.isSuccess) {
            val messageId = result.getOrNull() ?: ""
            val updatedMessage = message.copy(messageId = messageId)
            chatMessageDao.insertMessage(updatedMessage)
        }
        return result
    }
    
    fun observeMessagesByTaskId(taskId: String): Flow<List<ChatMessage>> {
        return firebaseService.observeMessagesByTaskId(taskId)
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

