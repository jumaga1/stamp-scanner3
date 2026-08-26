package com.filatelia.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.data.AppDatabase
import com.filatelia.scanner.data.StampRepository
import com.filatelia.scanner.ui.navigation.AppNavigation
import com.filatelia.scanner.ui.theme.StampScannerTheme
import com.filatelia.scanner.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instanciación directa y segura de Room Database
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "stamps.db"
        ).fallbackToDestructiveMigration().build()

        val stampRepository = StampRepository(db.stampDao())
        val aiRepository = AiRecognitionRepository()
        val viewModelFactory = ViewModelFactory(stampRepository, aiRepository)

        setContent {
            StampScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(factory = viewModelFactory)
                }
            }
        }
    }
}
