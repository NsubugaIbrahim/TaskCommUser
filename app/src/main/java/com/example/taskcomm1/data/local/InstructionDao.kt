package com.example.taskcomm1.data.local

import androidx.room.*
import com.example.taskcomm1.data.models.Instruction
import kotlinx.coroutines.flow.Flow

@Dao
interface InstructionDao {
    
    @Query("SELECT * FROM instructions ORDER BY createdAt DESC")
    fun getAllInstructions(): Flow<List<Instruction>>
    
    @Query("SELECT * FROM instructions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getInstructionsByUserId(userId: String): Flow<List<Instruction>>
    
    @Query("SELECT * FROM instructions WHERE instructionId = :instructionId")
    suspend fun getInstructionById(instructionId: String): Instruction?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstruction(instruction: Instruction)
    
    @Update
    suspend fun updateInstruction(instruction: Instruction)
    
    @Delete
    suspend fun deleteInstruction(instruction: Instruction)
    
    @Query("DELETE FROM instructions WHERE instructionId = :instructionId")
    suspend fun deleteInstructionById(instructionId: String)
    
    @Query("UPDATE instructions SET status = :status WHERE instructionId = :instructionId")
    suspend fun updateInstructionStatus(instructionId: String, status: String)
}

