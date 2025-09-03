package com.example.taskcomm1.data.local

import androidx.room.*
import com.example.taskcomm1.data.models.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profiles")
    fun getAllUsers(): Flow<List<UserProfile>>
    
    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserProfile?
    
    @Query("SELECT * FROM user_profiles WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserProfile?
    
    @Query("SELECT * FROM user_profiles WHERE isAdmin = 1")
    fun getAllAdmins(): Flow<List<UserProfile>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserProfile)
    
    @Update
    suspend fun updateUser(user: UserProfile)
    
    @Delete
    suspend fun deleteUser(user: UserProfile)
    
    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)
}

