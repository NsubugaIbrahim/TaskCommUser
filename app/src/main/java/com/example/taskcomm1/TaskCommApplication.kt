package com.example.taskcomm1

import android.app.Application
import com.example.taskcomm1.data.local.TaskCommDatabase
import com.example.taskcomm1.data.remote.FirebaseService
import com.example.taskcomm1.data.repository.TaskCommRepository
import com.example.taskcomm1.ui.viewmodels.AuthViewModel
import com.example.taskcomm1.ui.viewmodels.ChatViewModel
import com.example.taskcomm1.ui.viewmodels.InstructionViewModel
import com.google.firebase.FirebaseApp

class TaskCommApplication : Application() {
    // Database
    lateinit var database: TaskCommDatabase
        private set
    
    // Remote service
    lateinit var firebaseService: FirebaseService
        private set
    
    // Repository
    lateinit var repository: TaskCommRepository
        private set
    
    // ViewModels
    lateinit var authViewModel: AuthViewModel
        private set
    
    lateinit var instructionViewModel: InstructionViewModel
        private set
    
    lateinit var chatViewModel: ChatViewModel
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize database
        database = TaskCommDatabase.getDatabase(this)
        
        // Initialize Firebase service
        firebaseService = FirebaseService()
        
        // Initialize repository
        repository = TaskCommRepository(
            firebaseService = firebaseService,
            userProfileDao = database.userProfileDao(),
            instructionDao = database.instructionDao(),
            taskDao = database.taskDao(),
            chatMessageDao = database.chatMessageDao()
        )
        
        // Initialize ViewModels
        authViewModel = AuthViewModel(repository)
        instructionViewModel = InstructionViewModel(repository)
        chatViewModel = ChatViewModel(repository)
    }
    
    companion object {
        lateinit var instance: TaskCommApplication
            private set
    }
}
