package com.filatelia.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.filatelia.scanner.ui.navigation.AppNavigation
import com.filatelia.scanner.ui.theme.StampScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as StampScannerApp

        setContent {
            StampScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(app = app)
                }
            }
        }
    }
}
