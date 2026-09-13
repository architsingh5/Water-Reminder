package com.jaimatadi.waterreminder.reminder

object ReminderActions {
    const val Show = "com.jaimatadi.waterreminder.action.SHOW_REMINDER"
    const val Drank = "com.jaimatadi.waterreminder.action.DRANK"
    const val Snooze = "com.jaimatadi.waterreminder.action.SNOOZE"
    const val Skip = "com.jaimatadi.waterreminder.action.SKIP"

    /**
     * Sent (package-internal) after Drank/Snooze/Skip has been processed so an
     * alarm card that is still on screen can close itself and stop ringing.
     */
    const val Handled = "com.jaimatadi.waterreminder.action.REMINDER_HANDLED"

    /** Fired at local midnight so placed widgets roll over to the new day. */
    const val RefreshWidgets = "com.jaimatadi.waterreminder.action.REFRESH_WIDGETS"
}
