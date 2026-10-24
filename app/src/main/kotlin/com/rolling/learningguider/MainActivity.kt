package com.rolling.learningguider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rolling.learningguider.ui.AppRoot
import com.rolling.learningguider.ui.theme.LearningGuiderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LearningGuiderTheme {
                AppRoot()
            }
        }
    }
}
