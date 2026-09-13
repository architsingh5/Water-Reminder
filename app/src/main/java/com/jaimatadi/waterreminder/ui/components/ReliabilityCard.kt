package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaimatadi.waterreminder.R

@Composable
fun ReliabilityCard(
    hasNotificationPermission: Boolean,
    notificationPermissionPermanentlyDenied: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
) {
    val allGood = hasNotificationPermission && canScheduleExactAlarms && canUseFullScreenIntent
    val scheme = MaterialTheme.colorScheme

    if (allGood) {
        // Nothing to do: a quiet green one-liner instead of a full card.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.tertiaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(R.drawable.ic_check_stroke, tint = scheme.onTertiaryContainer, size = 16.dp)
            Text(
                "Reminders are set up to be reliable",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onTertiaryContainer
            )
        }
        return
    }

    SectionCard(title = "Reliability", modifier = Modifier.animateContentSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow("Notifications", hasNotificationPermission)
            StatusRow("Exact alarms", canScheduleExactAlarms)
            StatusRow("Full-screen alerts", canUseFullScreenIntent)

            if (!hasNotificationPermission) {
                Text(
                    "Without notification permission reminders may not be visible at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(if (notificationPermissionPermanentlyDenied) "Open notification settings" else "Allow notifications")
                }
            }
            if (!canScheduleExactAlarms) {
                TextButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open alarm permission settings")
                }
            }
            if (!canUseFullScreenIntent) {
                TextButton(onClick = onOpenFullScreenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open full-screen alert settings")
                }
            }
            Text(
                "On Samsung phones, keep this app unrestricted in battery settings if reminders arrive late.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (ok) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (ok) "ready" else "needs attention",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
