package com.jos.firewall

import android.app.Application
import android.content.Intent
import android.os.Process
import com.jos.firewall.data.AppDatabase
import com.jos.firewall.firewall.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.PrintWriter
import java.io.StringWriter

class JosFirewallApp : Application() {

    val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val ruleRepository by lazy { RuleRepository(database, applicationScope) }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("crash_trace", sw.toString())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                // If even the crash screen fails, fall back to default behavior
            }
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }
}
