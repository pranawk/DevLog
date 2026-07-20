package com.matrix.devlog.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.matrix.devlog.worker.DataRefreshWorker

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AppUpdateReceiver", "Received broadcast: $action")
        
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_BOOT_COMPLETED) {
            // Trigger an immediate refresh of all widgets after app update or device reboot
            DataRefreshWorker.enqueueOneTimeWork(context)
            
            // Also ensure periodic work is still active
            DataRefreshWorker.enqueuePeriodicWork(context)
        }
    }
}
