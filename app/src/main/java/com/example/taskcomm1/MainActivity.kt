package com.example.taskcomm1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taskcomm1.ui.screens.auth.LoginScreen
import com.example.taskcomm1.ui.screens.dashboard.DashboardScreen
import com.example.taskcomm1.ui.screens.instruction.InstructionDetailScreen
import com.example.taskcomm1.ui.screens.chat.ChatScreen
import com.example.taskcomm1.ui.theme.TaskComm1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskComm1Theme {
                TaskCommApp()
            }
        }
    }
}

@Composable
fun TaskCommApp() {
    val navController = rememberNavController()
    val app = TaskCommApplication.instance
    
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                authViewModel = app.authViewModel,
                onNavigateToSignUp = { /* Already handled in LoginScreen */ },
                onNavigateToDashboard = { navController.navigate("dashboard") }
            )
        }
        
        composable("dashboard") {
            DashboardScreen(
                authViewModel = app.authViewModel,
                instructionViewModel = app.instructionViewModel,
                onNavigateToInstructionDetail = { instructionId ->
                    navController.navigate("instruction_detail/$instructionId")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onSignOut = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable("instruction_detail/{instructionId}") { backStackEntry ->
            val instructionId = backStackEntry.arguments?.getString("instructionId") ?: ""
            InstructionDetailScreen(
                instructionId = instructionId,
                authViewModel = app.authViewModel,
                instructionViewModel = app.instructionViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { taskId ->
                    navController.navigate("chat/$taskId")
                }
            )
        }
        
        composable("chat/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            ChatScreen(
                taskId = taskId,
                authViewModel = app.authViewModel,
                chatViewModel = app.chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("profile") {
            // TODO: Add ProfileScreen
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Text(
                    text = "Profile Screen",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TaskCommAppPreview() {
    TaskComm1Theme {
        TaskCommApp()
    }
}