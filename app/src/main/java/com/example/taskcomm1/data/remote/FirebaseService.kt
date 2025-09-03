package com.example.taskcomm1.data.remote

import com.example.taskcomm1.data.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.InputStream

class FirebaseService {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    
    // Authentication
    suspend fun signUp(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                // Create user profile
                val userProfile = UserProfile(
                    userId = user.uid,
                    name = name,
                    email = email,
                    isAdmin = false
                )
                firestore.collection("users").document(user.uid).set(userProfile).await()
            }
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut() {
        auth.signOut()
    }
    
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    // User Profile
    suspend fun updateUserProfile(userProfile: UserProfile): Result<Unit> {
        return try {
            firestore.collection("users").document(userProfile.userId).set(userProfile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            val userProfile = document.toObject(UserProfile::class.java)
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeAllUsers(): Flow<List<UserProfile>> = callbackFlow {
        val listener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)
                } ?: emptyList()
                
                trySend(users)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Instructions
    suspend fun createInstruction(instruction: Instruction): Result<String> {
        return try {
            val docRef = firestore.collection("instructions").add(instruction).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateInstruction(instruction: Instruction): Result<Unit> {
        return try {
            firestore.collection("instructions").document(instruction.instructionId).set(instruction).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeInstructionsByUserId(userId: String): Flow<List<Instruction>> = callbackFlow {
        val listener = firestore.collection("instructions")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val instructions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Instruction::class.java)?.copy(instructionId = doc.id)
                } ?: emptyList()
                
                trySend(instructions)
            }
        
        awaitClose { listener.remove() }
    }
    
    fun observeAllInstructions(): Flow<List<Instruction>> = callbackFlow {
        val listener = firestore.collection("instructions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val instructions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Instruction::class.java)?.copy(instructionId = doc.id)
                } ?: emptyList()
                
                trySend(instructions)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Tasks
    suspend fun createTask(task: Task): Result<String> {
        return try {
            val docRef = firestore.collection("tasks").add(task).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateTask(task: Task): Result<Unit> {
        return try {
            firestore.collection("tasks").document(task.taskId).set(task).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeTasksByInstructionId(instructionId: String): Flow<List<Task>> = callbackFlow {
        val listener = firestore.collection("tasks")
            .whereEqualTo("instructionId", instructionId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Task::class.java)?.copy(taskId = doc.id)
                } ?: emptyList()
                
                trySend(tasks)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Chat Messages
    suspend fun sendMessage(message: ChatMessage): Result<String> {
        return try {
            val docRef = firestore.collection("messages").add(message).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeMessagesByTaskId(taskId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = firestore.collection("messages")
            .whereEqualTo("taskId", taskId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(messageId = doc.id)
                } ?: emptyList()
                
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    
    // File Upload
    suspend fun uploadFile(fileName: String, inputStream: InputStream, contentType: String): Result<String> {
        return try {
            val storageRef = storage.reference.child("uploads/$fileName")
            val uploadTask = storageRef.putStream(inputStream)
            val downloadUrl = uploadTask.await().storage.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

