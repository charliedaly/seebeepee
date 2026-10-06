package com.example.seebeepee.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.RouteManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts stored grid breadcrumbs to Lat/Lon GPX format and saves them to either
 * the directory where the active route file was originally loaded from, or the public
 * Downloads directory, or the app documents directory as fallback.
 *
 * Displays toast feedback confirming the exact location (e.g. "Saved to Downloads: Carrauntoohil_20260324_143000.gpx").
 *
 * Filename format: <RouteName>_<YYYYMMDD_HHMMSS>.gpx
 */
fun saveBreadcrumbsToGpx(
    context: Context,
    breadcrumbs: List<Breadcrumb> = RouteManager.breadcrumbs,
    routeName: String = RouteManager.currentRouteName,
    filePath: String? = RouteManager.currentFilePath,
    fileUri: String? = RouteManager.currentFileUri
): File? {
    if (breadcrumbs.isEmpty()) return null

    // Route filename minus extension
    val cleanRouteName = if (routeName.isBlank() || routeName.equals("No Route Loaded", ignoreCase = true)) {
        "Hike"
    } else {
        routeName.substringBeforeLast(".").ifBlank { "Hike" }
    }

    // Filename: <RouteName>_<YYYYMMDD_HHMMSS>.gpx
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val timestampStr = dateFormat.format(Date())
    val filename = "${cleanRouteName}_$timestampStr.gpx"

    // Build valid GPX XML content
    val gpxBuilder = StringBuilder().apply {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"SeeBeePee\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n")
        append("    <name>$cleanRouteName</name>\n")
        append("    <trkseg>\n")

        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (bc in breadcrumbs) {
            // Convert grid coordinates (x, y) back to Latitude and Longitude via CoordinateUtils
            val (lat, lon) = CoordinateUtils.metricToLatLon(bc.x, bc.y)
            val timeIso = timeFormat.format(Date(bc.timestamp))

            append("      <trkpt lat=\"$lat\" lon=\"$lon\">\n")
            if (bc.altitude != 0.0) {
                append("        <ele>${bc.altitude}</ele>\n")
            }
            append("        <time>$timeIso</time>\n")
            append("      </trkpt>\n")
        }

        append("    </trkseg>\n")
        append("  </trk>\n")
        append("</gpx>")
    }

    val gpxBytes = gpxBuilder.toString().toByteArray(Charsets.UTF_8)
    var savedFile: File? = null
    var folderDisplayName = "Downloads"

    // 1. Attempt original directory using SAF DocumentFile if fileUri is present
    if (!fileUri.isNullOrEmpty()) {
        try {
            val parsedUri = Uri.parse(fileUri)
            val singleDoc = DocumentFile.fromSingleUri(context, parsedUri)
            val parentDoc = singleDoc?.parentFile
            if (parentDoc != null && parentDoc.canWrite()) {
                val newDoc = parentDoc.createFile("application/gpx+xml", filename)
                if (newDoc != null) {
                    context.contentResolver.openOutputStream(newDoc.uri)?.use { outputStream ->
                        outputStream.write(gpxBytes)
                    }
                    val dirName = parentDoc.name ?: "Downloads"
                    folderDisplayName = if (dirName.equals("Download", ignoreCase = true)) "Downloads" else dirName
                    RouteManager.logError("Saved ${breadcrumbs.size} breadcrumbs to original directory via SAF: ${newDoc.uri}")
                    val localFile = if (newDoc.uri.scheme == "file") File(newDoc.uri.path ?: "") else File(filename)
                    savedFile = localFile
                }
            }
        } catch (e: Exception) {
            RouteManager.logError("Failed saving to SAF parent directory: ${e.message}")
        }
    }

    // 1b. Attempt original directory using File if filePath is present and writable
    if (savedFile == null && !filePath.isNullOrEmpty()) {
        try {
            val routeFile = File(filePath)
            val parentDir = routeFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory && parentDir.canWrite()) {
                val gpxFile = File(parentDir, filename)
                FileOutputStream(gpxFile).use { outputStream ->
                    outputStream.write(gpxBytes)
                }
                folderDisplayName = if (parentDir.name.equals("Download", ignoreCase = true)) "Downloads" else parentDir.name
                RouteManager.logError("Saved ${breadcrumbs.size} breadcrumbs to original directory GPX file: ${gpxFile.absolutePath}")
                savedFile = gpxFile
            }
        } catch (e: Exception) {
            RouteManager.logError("Failed saving to original directory '$filePath': ${e.message}")
        }
    }

    // 2. Attempt public Downloads directory
    if (savedFile == null) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val gpxFile = File(downloadsDir, filename)
                FileOutputStream(gpxFile).use { outputStream ->
                    outputStream.write(gpxBytes)
                }
                folderDisplayName = "Downloads"
                RouteManager.logError("Saved ${breadcrumbs.size} breadcrumbs to public Downloads GPX file: ${gpxFile.absolutePath}")
                savedFile = gpxFile
            }
        } catch (e: Exception) {
            RouteManager.logError("Failed to save to public Downloads directory: ${e.message}")
        }
    }

    // 3. Fallback to app documents directory
    if (savedFile == null) {
        try {
            val fallbackDir = getAppDocumentsDirectory(context)
            val fallbackFile = File(fallbackDir, filename)
            FileOutputStream(fallbackFile).use { outputStream ->
                outputStream.write(gpxBytes)
            }
            folderDisplayName = if (fallbackDir.name.equals("Download", ignoreCase = true)) "Downloads" else fallbackDir.name
            RouteManager.logError("Saved ${breadcrumbs.size} breadcrumbs to fallback GPX file: ${fallbackFile.absolutePath}")
            savedFile = fallbackFile
        } catch (fallbackEx: Exception) {
            RouteManager.logError("Failed fallback GPX save: ${fallbackEx.message}")
            return null
        }
    }

    // Display user feedback Toast
    val finalFileName = savedFile?.name.orEmpty().ifEmpty { filename }
    val feedbackMsg = "Saved to $folderDisplayName: $finalFileName"
    try {
        val mainLooper = Looper.getMainLooper()
        if (mainLooper != null) {
            Handler(mainLooper).post {
                try {
                    Toast.makeText(context, feedbackMsg, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // Ignore Toast errors in non-UI or unit test environments
                }
            }
        }
    } catch (e: Exception) {
        // Ignore looper errors in unit test environments
    }

    return savedFile
}

private fun getAppDocumentsDirectory(context: Context): File {
    val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ?: File(context.filesDir, "documents")
    if (!docsDir.exists()) {
        docsDir.mkdirs()
    }
    return docsDir
}
