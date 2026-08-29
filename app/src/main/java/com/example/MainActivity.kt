package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.ui.MusicPlayerApp
import com.example.ui.MusicViewModel
import com.example.ui.MusicViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        // Initialize Room Database, DAO and Repository
        val database = MusicDatabase.getDatabase(applicationContext)
        val musicDao = database.musicDao()
        val repository = MusicRepository(musicDao)

        // Instantiate core ViewModel
        val viewModel = ViewModelProvider(
            this,
            MusicViewModelFactory(application, repository)
        )[MusicViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MusicPlayerApp(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
