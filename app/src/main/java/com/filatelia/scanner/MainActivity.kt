package com.filatelia.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.data.AppDatabase
import com.filatelia.scanner.data.StampRepository
import com.filatelia.scanner.ui.navigation.AppNavigation
import com.filatelia.scanner.ui.theme.FilateliaTheme
import com.filatelia.scanner.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val stampRepository = StampRepository(database.stampDao())
        val aiRepository = AiRecognitionRepository()
        val viewModelFactory = ViewModelFactory(stampRepository, aiRepository)

        setContent {
            FilateliaTheme {
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
