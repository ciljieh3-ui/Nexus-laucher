package com.example.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional Device Admin receiver used exclusively when the user enables
 * Double-Tap to Lock Screen in Gesture Settings.
 */
class NexusDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
