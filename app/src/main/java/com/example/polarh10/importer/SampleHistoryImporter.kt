package com.example.polarh10.importer

import android.content.Context
import com.example.polarh10.db.DatabaseHelper
import com.example.polarh10.model.AccSample

data class ImportResult(
    val sessionId: Long,
    val hrCount: Int,
    val accCount: Int
)

class SampleHistoryImporter(
    private val context: Context,
    private val database: DatabaseHelper
) {
    fun importFromAssets(): ImportResult {
        val sessionRow = readCsv("PolarH10_session/session.csv").drop(1).first()
        val deviceId = sessionRow[1]
        val startTime = sessionRow[2].toLong()
        val endTime = sessionRow[3].takeIf { it.isNotBlank() }?.toLong()
        val note = sessionRow.getOrNull(4)?.takeIf { it.isNotBlank() }
        val newSessionId = database.createImportedSession(
            deviceId = deviceId,
            startTime = startTime,
            endTime = endTime,
            note = note
        )

        var hrCount = 0
        readCsv("PolarH10_session/hr.csv").drop(1).forEach { row ->
            database.insertHr(
                sessionId = newSessionId,
                timestamp = row[1].toLong(),
                hr = row[2].toInt(),
                rr = row.getOrNull(3)?.takeIf { it.isNotBlank() }
            )
            hrCount += 1
        }

        val accRows = readCsv("PolarH10_session/acc.csv").drop(1)
        database.insertAccBatch(
            accRows.map { row ->
                AccSample(
                    id = 0,
                    sessionId = newSessionId,
                    timestamp = row[1].toLong(),
                    x = row[2].toInt(),
                    y = row[3].toInt(),
                    z = row[4].toInt()
                )
            }
        )

        return ImportResult(
            sessionId = newSessionId,
            hrCount = hrCount,
            accCount = accRows.size
        )
    }

    private fun readCsv(assetPath: String): List<List<String>> =
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { parseCsvLine(it.removePrefix("\uFEFF")) }
                .toList()
        }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }

        result.add(current.toString())
        return result
    }
}
