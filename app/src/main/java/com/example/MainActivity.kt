package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.ui.MusicPlayerApp
import com.example.ui.MusicViewModel
import com.example.ui.MusicViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
