package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1 MB
    
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    
    fun init(context: Context) {
        // Use external files dir so logs are accessible via USB / File Manager
        logDir = context.getExternalFilesDir("logs") ?: context.filesDir
        
        i(TAG, "Logger initialized. OS: ${Build.VERSION.RELEASE}, Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        
        // Setup Uncaught Exception Handler
        setupCrashLogger(context.applicationContext)
    }
    
    private fun setupCrashLogger(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            } finally {
                // Pass control to default system handler so app closes normally
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    @Synchronized
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logEntry = StringBuilder().apply {
            append("$timestamp [$level] $tag: $message\n")
            throwable?.let {
                val sw = StringWriter()
                it.printStackTrace(PrintWriter(sw))
                append(sw.toString())
                append("\n")
            }
        }.toString()
        
        // Print to logcat
        when (level) {
            "INFO" -> Log.i(tag, message, throwable)
            "WARN" -> Log.w(tag, message, throwable)
            "ERROR" -> Log.e(tag, message, throwable)
            else -> Log.d(tag, message, throwable)
        }
        
        // Write to log file
        logDir?.let { dir ->
            try {
                val logFile = File(dir, LOG_FILE_NAME)
                
                // Roll over log file if it exceeds max size
                if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                    val backupFile = File(dir, "app_logs_old.txt")
                    if (backupFile.exists()) backupFile.delete()
                    logFile.renameTo(backupFile)
                }
                
                FileWriter(logFile, true).use { writer ->
                    writer.write(logEntry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }
    
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)
    
    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = logDir ?: context.getExternalFilesDir("logs") ?: context.filesDir
        val timestamp = fileDateFormat.format(Date())
        val crashFile = File(dir, "crash_report_$timestamp.txt")
        
        try {
            FileWriter(crashFile).use { writer ->
                writer.write("==================================================\n")
                writer.write("               APPLICATION CRASH REPORT           \n")
                writer.write("==================================================\n\n")
                writer.write("Time: ${dateFormat.format(Date())}\n")
                writer.write("Thread: ${thread.name} (ID: ${thread.id})\n\n")
                
                writer.write("--- DEVICE INFORMATION ---\n")
                writer.write("Manufacturer: ${Build.MANUFACTURER}\n")
                writer.write("Model: ${Build.MODEL}\n")
                writer.write("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                writer.write("CPU ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n\n")
                
                writer.write("--- APP INFORMATION ---\n")
                writer.write("Package: ${context.packageName}\n")
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    writer.write("Version Name: ${pInfo.versionName}\n")
                    writer.write("Version Code: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode}\n")
                } catch (e: Exception) {
                    writer.write("Version: Unknown\n")
                }
                writer.write("\n")
                
                writer.write("--- EXCEPTION STACK TRACE ---\n")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                writer.write(sw.toString())
                writer.write("\n\n")
                
                // Append the last 100 lines of app log if available
                writer.write("--- RECENT LOG HISTORY ---\n")
                val logFile = File(dir, LOG_FILE_NAME)
                if (logFile.exists()) {
                    val lines = logFile.readLines()
                    val lastLines = lines.takeLast(100)
                    lastLines.forEach { line ->
                        writer.write(line)
                        writer.write("\n")
                    }
                }
            }
            Log.e(TAG, "Crash report saved: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing crash report file", e)
        }
    }
    
    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".txt") || it.name.endsWith(".log")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun shareLogs(context: Context) {
        val files = getLogFiles()
        if (files.isEmpty()) {
            Log.w(TAG, "No logs available to share.")
            return
        }
        
        val uris = ArrayList<Uri>()
        for (file in files) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                uris.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get URI for file ${file.name}", e)
            }
        }
        
        if (uris.isEmpty()) return
        
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "Share ECG App Logs & Crash Reports")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
