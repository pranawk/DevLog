package com.matrix.devlog.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.matrix.devlog.R
import com.matrix.devlog.data.ContributionDatabase
import com.matrix.devlog.data.PlatformAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseContributionWidgetProvider(private val platformId: String) : AppWidgetProvider() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("WidgetProvider", "onUpdate called for $platformId with ${appWidgetIds.size} widgets")
        updateWidgetViews(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, this::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                updateWidgetViews(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    private fun updateWidgetViews(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scope.launch {
            try {
                val db = ContributionDatabase.getDatabase(context)
                val dao = db.contributionDao()
                val account = dao.getAccountById(platformId) ?: PlatformAccount(
                    id = platformId,
                    username = "Not Configured",
                    colorTheme = "GREEN",
                    cachedDataJson = "{}",
                    totalContributions = 0,
                    streak = 0
                )

                val isDarkMode = (context.resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

                val bitmap = WidgetDrawingHelper.drawWidgetBitmap(context, account, isDarkMode)

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_contribution)
                    views.setImageViewBitmap(R.id.widget_image_view, bitmap)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                Log.e("WidgetProvider", "Error updating widget for $platformId", e)
            }
        }
    }
}
