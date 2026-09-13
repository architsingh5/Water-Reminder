package com.jaimatadi.waterreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import com.jaimatadi.waterreminder.reminder.WaterNotification
import com.jaimatadi.waterreminder.ui.HomeScreen
import com.jaimatadi.waterreminder.ui.ReminderViewModel
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
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
            WaterTheme(dynamicColor = state.dynamicColor) {
                HomeScreen(viewModel)
            }
        }
    }
}
