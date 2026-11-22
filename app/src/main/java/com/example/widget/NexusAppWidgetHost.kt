package com.example.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

data class InstalledWidgetProvider(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val packageName: String,
    val className: String
)

/**
 * Manages real Android AppWidgetHost lifecycle, binding, configuration, sizing, and cleanup.
 */
class NexusAppWidgetHost(private val context: Context) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    private var isListening = false

    fun startListening() {
        if (!isListening) {
            try {
                appWidgetHost.startListening()
                isListening = true
            } catch (_: Exception) {
                // Ignore OEM host edge cases cleanly
            }
        }
    }

    fun stopListening() {
        if (isListening) {
            try {
                appWidgetHost.stopListening()
            } catch (_: Exception) {
                // Ignore
            } finally {
                isListening = false
            }
        }
    }

    fun getInstalledWidgetProviders(): List<InstalledWidgetProvider> {
        val pm = context.packageManager
        return try {
            appWidgetManager.installedProviders.map { info ->
                val pkg = info.provider.packageName
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }
                val widgetLabel = info.loadLabel(pm).ifBlank { appLabel }
                InstalledWidgetProvider(
                    info = info,
                    label = widgetLabel,
                    appLabel = appLabel,
                    packageName = pkg,
                    className = info.provider.className
                )
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun deleteWidgetId(appWidgetId: Int) {
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun bindAppWidgetIfAllowed(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
        } catch (_: Exception) {
            false
        }
    }

    fun createBindIntent(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
        }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return try {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        } catch (_: Exception) {
            null
        }
    }

    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        val info = getAppWidgetInfo(appWidgetId) ?: return null
        return try {
            appWidgetHost.createView(context, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun updateWidgetSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(
                    Bundle(),
                    listOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        } catch (_: Exception) {
            // Ignore resize exceptions on older OEM widgets
        }
    }

    companion object {
        const val HOST_ID = 1024
    }
}
