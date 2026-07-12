package com.example.data

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

object XmltvEpgParser {

    private val client: okhttp3.OkHttpClient
        get() = com.example.core.network.NetworkClientFactory.xmltvClient

    fun buildXtreamXmltvUrl(session: SessionEntity): String {
        return "${session.serverUrl}/xmltv.php?username=${session.username}&password=${session.token}"
    }

    fun parseXmltvDate(value: String): Long? {
        val clean = value.trim()
        if (clean.isEmpty()) return null

        val formats = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                if (fmt == "yyyyMMddHHmmss") {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(clean)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        // Custom manual fallback parsing if standard parsing failed
        try {
            val parts = clean.split(Regex("\\s+"))
            if (parts.isNotEmpty()) {
                val datePart = parts[0].filter { it.isDigit() }
                val tzPart = if (parts.size > 1) parts[1] else "+0000"
                if (datePart.length >= 8) {
                    var normalizedDate = datePart
                    if (normalizedDate.length < 14) {
                        normalizedDate = normalizedDate.padEnd(14, '0')
                    } else if (normalizedDate.length > 14) {
                        normalizedDate = normalizedDate.substring(0, 14)
                    }
                    val formatted = "$normalizedDate $tzPart"
                    val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                    return sdf.parse(formatted)?.time
                }
            }
        } catch (e: Exception) {
            // Safe logging/fallback
        }
        return null
    }

    fun fetchAndParseXtreamXmltv(session: SessionEntity, channels: List<LiveChannel>): List<EpgProgramEntity> {
        val url = buildXtreamXmltvUrl(session)
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val redactedUrl = com.example.core.redaction.SensitiveDataRedactor.redactUrl(url)
                    android.util.Log.e("XmltvEpgParser", "EPG XMLTV request failed with code ${response.code} for $redactedUrl")
                    return emptyList()
                }
                val bodyString = response.body?.string() ?: return emptyList()
                return parseXmltv(bodyString)
            }
        } catch (e: Exception) {
            val redactedUrl = com.example.core.redaction.SensitiveDataRedactor.redactUrl(url)
            val redactedMsg = com.example.core.redaction.SensitiveDataRedactor.redactExceptionMessage(e.message)
            android.util.Log.e("XmltvEpgParser", "EPG fetch/parse failed for $redactedUrl: $redactedMsg")
            return emptyList()
        }
    }

    fun parseXmltv(xmlData: String): List<EpgProgramEntity> {
        val programs = mutableListOf<EpgProgramEntity>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlData))
            var eventType = parser.eventType
            
            var currentChannelId: String? = null
            var currentStart: String? = null
            var currentStop: String? = null
            var currentTitle: String? = null
            var currentDesc: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "programme") {
                            currentChannelId = parser.getAttributeValue(null, "channel")
                            currentStart = parser.getAttributeValue(null, "start")
                            currentStop = parser.getAttributeValue(null, "stop")
                            currentTitle = null
                            currentDesc = null
                        } else if (name == "title") {
                            currentTitle = parser.nextText()
                        } else if (name == "desc") {
                            currentDesc = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "programme") {
                            val chanId = currentChannelId
                            val startStr = currentStart
                            val stopStr = currentStop
                            val title = currentTitle ?: ""
                            
                            if (chanId != null && startStr != null && stopStr != null) {
                                val startTime = parseXmltvDate(startStr)
                                val endTime = parseXmltvDate(stopStr)
                                if (startTime != null && endTime != null) {
                                    programs.add(
                                        EpgProgramEntity(
                                            channelId = chanId,
                                            title = title,
                                            description = currentDesc ?: "",
                                            startTime = startTime,
                                            endTime = endTime,
                                            epgId = "${chanId}_$startTime"
                                        )
                                    )
                                }
                            }
                            currentChannelId = null
                            currentStart = null
                            currentStop = null
                            currentTitle = null
                            currentDesc = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("XmltvEpgParser", "Error parsing XMLTV data", e)
        }
        return programs
    }
}
