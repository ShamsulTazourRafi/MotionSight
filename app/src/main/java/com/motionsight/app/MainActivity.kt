package com.motionsight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motionsight.app.ui.MotionSightApp
import com.motionsight.app.ui.theme.MotionSightTheme
import com.motionsight.app.viewmodel.MotionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MotionSightTheme {
                val motionViewModel: MotionViewModel = viewModel()
                MotionSightApp(viewModel = motionViewModel)
            }
        }
    }
}
