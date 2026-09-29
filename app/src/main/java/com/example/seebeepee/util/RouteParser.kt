package com.example.seebeepee.util

import com.example.seebeepee.model.Waypoint
import java.io.BufferedReader
import java.io.StringReader

object RouteParser {
    /**
     * Parses CSV route content into a list of Waypoints.
     * Supports columns: grid_ref/grid/location, latitude/lat, longitude/lon, altitude/alt/elevation/ele, name/title.
     */
    fun parseCsv(csvContent: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        val reader = BufferedReader(StringReader(csvContent))
        val lines = reader.readLines()
        if (lines.isEmpty()) return waypoints

        // Parse header
        val headerLine = lines[0].trim()
        val headers = parseCsvLine(headerLine).map { it.lowercase().trim() }

        val nameIdx = headers.indexOfFirst { it in listOf("name", "title") }
        val gridIdx = headers.indexOfFirst { it in listOf("grid_ref", "grid", "location") }
        val latIdx = headers.indexOfFirst { it in listOf("latitude", "lat") }
        val lonIdx = headers.indexOfFirst { it in listOf("longitude", "lon") }
        val altIdx = headers.indexOfFirst { it in listOf("altitude", "alt", "elevation", "ele") }

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val tokens = parseCsvLine(line)
            if (tokens.isEmpty()) continue

            val name = if (nameIdx >= 0 && nameIdx < tokens.size) {
                tokens[nameIdx].trim().removeSurrounding("\"", "\"")
            } else {
                "Waypoint $i"
            }

            val alt = if (altIdx >= 0 && altIdx < tokens.size) {
                tokens[altIdx].trim().removeSurrounding("\"", "\"").toDoubleOrNull() ?: 0.0
            } else {
                0.0
            }

            val waypoint = if (gridIdx >= 0 && gridIdx < tokens.size && tokens[gridIdx].isNotBlank()) {
                val gridRef = tokens[gridIdx].trim().removeSurrounding("\"", "\"")
                Waypoint.fromGridReference(name, gridRef, alt)
            } else if (latIdx >= 0 && lonIdx >= 0 && latIdx < tokens.size && lonIdx < tokens.size) {
                val lat = tokens[latIdx].trim().removeSurrounding("\"", "\"").toDoubleOrNull() ?: 0.0
                val lon = tokens[lonIdx].trim().removeSurrounding("\"", "\"").toDoubleOrNull() ?: 0.0
                Waypoint.fromLatLon(name, lat, lon, alt)
            } else {
                continue
            }

            waypoints.add(waypoint)
        }

        return waypoints
    }

    /**
     * Parses basic GPX track content into a list of Waypoints.
     */
    fun parseGpx(gpxContent: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        val ptRegex = Regex("<(wpt|trkpt)\\s+lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\"[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        val altRegex = Regex("<ele>([^<]+)</ele>")
        val nameRegex = Regex("<name>([^<]+)</name>")

        val matches = ptRegex.findAll(gpxContent)
        var index = 1
        for (match in matches) {
            val lat = match.groupValues[2].toDoubleOrNull() ?: continue
            val lon = match.groupValues[3].toDoubleOrNull() ?: continue
            val innerContent = match.groupValues[4]

            val alt = altRegex.find(innerContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val name = nameRegex.find(innerContent)?.groupValues?.get(1) ?: "Waypoint $index"

            waypoints.add(Waypoint.fromLatLon(name, lat, lon, alt))
            index++
        }

        if (waypoints.isEmpty()) {
            val simplePtRegex = Regex("<(wpt|trkpt)\\s+lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\"[^>]*/?>", RegexOption.IGNORE_CASE)
            val simpleMatches = simplePtRegex.findAll(gpxContent)
            for (match in simpleMatches) {
                val lat = match.groupValues[2].toDoubleOrNull() ?: continue
                val lon = match.groupValues[3].toDoubleOrNull() ?: continue
                waypoints.add(Waypoint.fromLatLon("Waypoint $index", lat, lon, 0.0))
                index++
            }
        }

        return waypoints
    }

    fun generateSampleIrishGridCsv(): String {
        return """
            name,grid_ref,altitude
            Start,V860870,120.0
            Colt,V875882,450.0
            Summit,V890895,850.0
            Ridge,V905910,650.0
            Finish,V920925,100.0
        """.trimIndent()
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString())
        return tokens
    }
}
