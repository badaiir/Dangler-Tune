package com.dangler.tune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.dangler.tune.ui.TunerScreen
import com.dangler.tune.ui.theme.MaterialBrutal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MaterialBrutal) {
                TunerScreen()
            }
        }
    }
}
