package com.example.roomie

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roomie.ui.theme.RoomieTheme

class MainActivity : ComponentActivity() {
    private var requestedConversation by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedConversation = intent.getStringExtra("conversationId")
        setContent {
            RoomieTheme {
                if (BuildConfig.FIREBASE_CONFIGURED) {
                    val vm: RoomieViewModel = viewModel()
                    RoomieApp(vm, requestedConversation) { requestedConversation = null }
                } else {
                    Surface { Text("Roomie setup is incomplete. Connect the Firebase project before using this build.") }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedConversation = intent.getStringExtra("conversationId")
    }
}
