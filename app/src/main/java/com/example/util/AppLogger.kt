package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String,
    val level: String, // "INFO", "WARN", "ERROR"
    val tag: String,
    val message: String
)

object AppLogger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 250
    private var prefs: SharedPreferences? = null

    fun initCrashHandler(context: Context) {
        prefs = context.getSharedPreferences("catmirror_crash_logs", Context.MODE_PRIVATE)

        // Load previous crash if any
        val lastCrash = prefs?.getString("last_crash", null)
        if (!lastCrash.isNullOrEmpty()) {
            addEntry("ERROR", "CRASH_PREVIO", "Se detectó un cierre inesperado anterior:\n$lastCrash")
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            prefs?.edit()?.putString("last_crash", "Hilo: ${thread.name} [${timeFormat.format(Date())}]\n$stackTrace")?.commit()
            addEntry("ERROR", "FATAL_CRASH", stackTrace)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    private fun addEntry(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > MAX_LOGS) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addEntry("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        Log.e(tag, fullMsg)
        addEntry("ERROR", tag, fullMsg)
    }

    fun clear() {
        prefs?.edit()?.remove("last_crash")?.apply()
        _logs.value = emptyList()
    }

    fun getAllLogsFormatted(): String {
        return _logs.value.reversed().joinToString("\n") {
            "[${it.timestamp}] [${it.level}] [${it.tag}]: ${it.message}"
        }
    }
}
