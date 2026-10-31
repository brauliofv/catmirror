package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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
    private const val MAX_PERSISTED = 80
    private const val PREFS_NAME = "catmirror_crash_logs"
    private const val KEY_SAVED_LOGS = "persisted_logs_json"
    private const val KEY_LAST_CRASH = "last_crash"

    private var prefs: SharedPreferences? = null

    fun initCrashHandler(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val initialList = mutableListOf<LogEntry>()

        // 1. Load previously persisted operational logs from previous session
        val savedJson = sp.getString(KEY_SAVED_LOGS, null)
        if (!savedJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    initialList.add(
                        LogEntry(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            timestamp = obj.optString("time", "--:--:--"),
                            level = obj.optString("lvl", "INFO"),
                            tag = obj.optString("tag", "PREV"),
                            message = obj.optString("msg", "")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. Load previous crash if any
        val lastCrash = sp.getString(KEY_LAST_CRASH, null)
        if (!lastCrash.isNullOrEmpty()) {
            initialList.add(
                0,
                LogEntry(
                    timestamp = timeFormat.format(Date()),
                    level = "ERROR",
                    tag = "CRASH_PREVIO",
                    message = "Se detectó un cierre inesperado anterior:\n$lastCrash"
                )
            )
        }

        // Add startup entry
        initialList.add(
            0,
            LogEntry(
                timestamp = timeFormat.format(Date()),
                level = "INFO",
                tag = "CatMirrorApp",
                message = "--- App iniciada en ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}) ---"
            )
        )

        _logs.value = initialList
        persistLogs()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            prefs?.edit()?.putString(KEY_LAST_CRASH, "Hilo: ${thread.name} [${timeFormat.format(Date())}]\n$stackTrace")?.commit()
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
        persistLogs()
    }

    private fun persistLogs() {
        val sp = prefs ?: return
        try {
            val list = _logs.value.take(MAX_PERSISTED)
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("time", item.timestamp)
                obj.put("lvl", item.level)
                obj.put("tag", item.tag)
                obj.put("msg", item.message)
                array.put(obj)
            }
            sp.edit().putString(KEY_SAVED_LOGS, array.toString()).apply()
        } catch (_: Exception) {}
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
        prefs?.edit()?.remove(KEY_SAVED_LOGS)?.remove(KEY_LAST_CRASH)?.apply()
        _logs.value = emptyList()
    }

    fun getAllLogsFormatted(): String {
        return _logs.value.reversed().joinToString("\n") {
            "[${it.timestamp}] [${it.level}] [${it.tag}]: ${it.message}"
        }
    }
}
