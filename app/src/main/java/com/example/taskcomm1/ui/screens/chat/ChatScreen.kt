package com.example.taskcomm1.ui.screens.chat

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.taskcomm1.data.models.ChatMessage
import com.example.taskcomm1.ui.viewmodels.AuthViewModel
import com.example.taskcomm1.ui.viewmodels.ChatState
import com.example.taskcomm1.ui.viewmodels.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.example.taskcomm1.data.SupabaseClientProvider
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    taskId: String,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val composableContext = LocalContext.current
    val currentUserId by authViewModel.currentUserId.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val chatState by chatViewModel.chatState.collectAsState()
    val listState = rememberLazyListState()
    
    var messageText by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    LaunchedEffect(taskId) {
        chatViewModel.loadMessages(taskId)
        // Fetch admin name and task title for header from Supabase
        try {
            val client = SupabaseClientProvider.getClient(composableContext)
            val postgrest = client.pluginManager.getPlugin(Postgrest)
            val task = withContext(Dispatchers.IO) {
                postgrest["tasks"].select {
                    filter { eq("id", taskId) }
                    limit(1)
                }.decodeList<TaskHeaderRow>()
            }.firstOrNull()
            taskTitle = task?.title ?: ""
            if (task?.adminId != null && task.adminId.isNotBlank()) {
                val profile = withContext(Dispatchers.IO) {
                    postgrest["profiles"].select {
                        filter { eq("id", task.adminId!!) }
                        limit(1)
                    }.decodeList<ProfileHeaderRow>()
                }.firstOrNull()
                adminName = (profile?.name ?: profile?.email ?: "Admin").trim()
            }
        } catch (_: Exception) { }
    }
    
    // Removed manual refresh - now using smooth polling flow in repository
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (adminName.isBlank()) "Chat" else adminName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (taskTitle.isNotBlank()) {
                            Text(
                                text = "Task: " + taskTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4B2E83),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column {
                // Reply indicator above input
                if (replyToMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Reply, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${if (replyToMessage!!.senderRole == "admin") adminName else "You"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = replyToMessage!!.text.take(50) + if (replyToMessage!!.text.length > 50) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            IconButton(onClick = { replyToMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                            }
                        }
                    }
                }
                
                ChatInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onSendMessage = {
                        if (messageText.isNotBlank() && currentUserId != null) {
                            val finalText = if (replyToMessage != null) {
                                val senderName = if (replyToMessage!!.senderRole == "admin") adminName else "You"
                                "↩️ $senderName: \"${replyToMessage!!.text.take(30)}...\"\n\n$messageText"
                            } else messageText
                            chatViewModel.sendTextMessage(
                                taskId = taskId,
                                text = finalText,
                                senderId = currentUserId!!,
                                senderRole = "user"
                            )
                            messageText = ""
                            replyToMessage = null
                        }
                    },
                    onAttachFile = {
                        // TODO: Implement file attachment
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (chatState) {
                is ChatState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ChatState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = (chatState as ChatState.Error).message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No messages yet",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Start the conversation by sending a message",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Task name bubble at top
                            item(key = "task-header") {
                                if (taskTitle.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = taskTitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            
                            // Group messages by date
                            val groupedMessages = messages.groupBy { 
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.timestamp.toDate())
                            }
                            
                            groupedMessages.forEach { (date, messagesForDate) ->
                                // Date header
                                item(key = "date-$date") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = formatDateHeader(date),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                // Messages for this date
                                items(messagesForDate, key = { it.messageId }) { message ->
                                    MessageBubble(
                                        message = message,
                                        isFromCurrentUser = message.senderId == currentUserId,
                                        onLongClick = {
                                            selectedMessage = message
                                            showContextMenu = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Context menu for message actions
    if (showContextMenu && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text("Message Actions") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            replyToMessage = selectedMessage
                            showContextMenu = false
                        }
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reply")
                    }
                    if (selectedMessage!!.senderId == currentUserId) {
                        TextButton(
                            onClick = {
                                editText = selectedMessage!!.text
                                showEditDialog = true
                                showContextMenu = false
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Message")
                        }
                        TextButton(
                            onClick = {
                                chatViewModel.deleteMessage(selectedMessage!!.messageId)
                                showContextMenu = false
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Message")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Edit dialog
    if (showEditDialog && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editText.isNotBlank()) {
                            chatViewModel.editMessage(selectedMessage!!.messageId, editText)
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reply indicator
    if (replyToMessage != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Reply, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replying to:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = replyToMessage!!.text.take(50) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = { replyToMessage = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class TaskHeaderRow(
    val id: String? = null,
    @SerialName("admin_id") val adminId: String? = null,
    @SerialName("title") val title: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class ProfileHeaderRow(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isFromCurrentUser: Boolean,
    onLongClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isFromCurrentUser) {
            // simple avatar placeholder
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val bubbleShape = if (isFromCurrentUser) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
        }

        Surface(
            shape = bubbleShape,
            color = if (isFromCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onDoubleClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (message.fileType) {
                    "image" -> {
                        message.mediaUrl?.let { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    "document" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(message.fileName ?: "Document", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.text.isNotBlank()) {
                    // Check if this is a reply message
                    val isReply = message.text.startsWith("↩️")
                    val (replyPart, actualMessage) = if (isReply) {
                        val parts = message.text.split("\n\n", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else "" to message.text
                    } else {
                        "" to message.text
                    }
                    
                    // Remove "(edited)" from the actual message text
                    val cleanMessage = actualMessage.replace(" (edited)", "")
                    
                    // Show reply context if it's a reply
                    if (isReply && replyPart.isNotBlank()) {
                        Surface(
                            color = if (isFromCurrentUser) 
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f) 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = replyPart.removePrefix("↩️ "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = cleanMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = run {
                                val timestampStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp.toDate())
                                android.util.Log.d("UserChatScreen", "Displaying timestamp for message ${message.messageId}: $timestampStr (raw: ${message.timestamp.toDate()})")
                                timestampStr
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show edited flag if message was edited
                        if (message.text.contains(" (edited)")) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }

        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAttachFile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
            }
            
            TextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = { Text("Type a message…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledIconButton(
                onClick = onSendMessage,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

fun formatDateHeader(dateString: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
        
        when (dateString) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        dateString
    }
}