package com.example.taskcomm1.data.local

import androidx.room.*
import com.example.taskcomm1.data.models.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE instructionId = :instructionId ORDER BY createdAt DESC")
    fun getTasksByInstructionId(instructionId: String): Flow<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE adminId = :adminId ORDER BY createdAt DESC")
    fun getTasksByAdminId(adminId: String): Flow<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTaskById(taskId: String): Task?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)
    
    @Update
    suspend fun updateTask(task: Task)
    
    @Delete
    suspend fun deleteTask(task: Task)
    
    @Query("DELETE FROM tasks WHERE taskId = :taskId")
    suspend fun deleteTaskById(taskId: String)
    
    @Query("UPDATE tasks SET status = :status WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String)
    
    @Query("UPDATE tasks SET completedAt = :completedAt WHERE taskId = :taskId")
    suspend fun updateTaskCompletedAt(taskId: String, completedAt: com.google.firebase.Timestamp?)
}

