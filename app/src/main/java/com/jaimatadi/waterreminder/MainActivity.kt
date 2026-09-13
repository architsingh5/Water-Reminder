package com.jaimatadi.waterreminder

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import com.jaimatadi.waterreminder.reminder.WaterNotification
import com.jaimatadi.waterreminder.ui.HomeScreen
import com.jaimatadi.waterreminder.ui.ReminderViewModel
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
import com.jaimatadi.waterreminder.ui.theme.isDarkTheme
import com.jaimatadi.waterreminder.widget.WaterWidgetProvider

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ReminderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WaterNotification(this).createChannel()
        val appContext = applicationContext
        val repository = ReminderRepository(appContext)
        val scheduler = ReminderScheduler(appContext)
        viewModel = ViewModelProvider(
            this,
            ReminderViewModel.Factory(repository, scheduler, appContext) {
                WaterWidgetProvider.requestUpdate(appContext)
            }
        )[ReminderViewModel::class.java]

        setContent {
            val state by viewModel.state.collectAsState()
            val dark = isDarkTheme(state.themeMode)

            // The user can force light/dark regardless of the system setting,
            // so status/navigation bar icon colors must follow the app theme,
            // not the system one that enableEdgeToEdge() picks by default.
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }

            WaterTheme(themeMode = state.themeMode, dynamicColor = state.dynamicColor) {
                HomeScreen(viewModel)
            }
        }
    }
}
