package com.example.data

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
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

    suspend fun fetchAndParseXtreamXmltv(session: SessionEntity, channels: List<LiveChannel>): List<EpgProgramEntity> {
        val url = buildXtreamXmltvUrl(session)
        val request = Request.Builder()
            .url(url)
            .build()
        val call = client.newCall(request)

        try {
            val response = try {
                kotlinx.coroutines.suspendCancellableCoroutine<okhttp3.Response> { cont ->
                    cont.invokeOnCancellation {
                        call.cancel()
                    }
                    call.enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            cont.resumeWith(Result.failure(e))
                        }

                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            cont.resumeWith(Result.success(response))
                        }
                    })
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw com.example.core.network.mapThrowableToNetworkError(e)
            }

            response.use { resp ->
                val error = com.example.core.network.mapResponseCodeToNetworkError(resp.code)
                if (error != null) {
                    throw error
                }
                val body = resp.body ?: throw com.example.core.network.NetworkError.InvalidResponse
                body.charStream().use { reader ->
                    try {
                        return parseXmltvStrict(reader)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        throw com.example.core.network.NetworkError.InvalidResponse
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val error = if (e is com.example.core.network.NetworkError) e else com.example.core.network.mapThrowableToNetworkError(e)
            val category = when (error) {
                is com.example.core.network.NetworkError.NoConnection -> "NoConnection"
                is com.example.core.network.NetworkError.Timeout -> "Timeout"
                is com.example.core.network.NetworkError.Unauthorized -> "Unauthorized"
                is com.example.core.network.NetworkError.ServerError -> "ServerError"
                is com.example.core.network.NetworkError.InvalidResponse -> "InvalidResponse"
                is com.example.core.network.NetworkError.Unsupported -> "Unsupported"
                else -> "Unknown"
            }
            val redactedHost = com.example.core.redaction.SensitiveDataRedactor.redactUrl(url)
            android.util.Log.e("XmltvEpgParser", "EPG fetch/parse failed for $redactedHost with category: $category")
            throw error
        }
    }

    fun parseXmltv(xmlData: String): List<EpgProgramEntity> {
        return parseXmltv(StringReader(xmlData))
    }

    fun parseXmltv(reader: java.io.Reader): List<EpgProgramEntity> {
        val programs = mutableListOf<EpgProgramEntity>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(reader)
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
            android.util.Log.e("XmltvEpgParser", "Error parsing XMLTV data")
        }
        return programs
    }

    suspend fun parseXmltvStrict(reader: java.io.Reader): List<EpgProgramEntity> {
        val programs = mutableListOf<EpgProgramEntity>()
        val parser = Xml.newPullParser()
        parser.setInput(reader)
        var eventType = parser.eventType
        
        var hasTvTag = false
        var currentChannelId: String? = null
        var currentStart: String? = null
        var currentStop: String? = null
        var currentTitle: String? = null
        var currentDesc: String? = null
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "tv") {
                        hasTvTag = true
                    }
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
        if (!hasTvTag) {
            throw org.xmlpull.v1.XmlPullParserException("Missing <tv> root element")
        }
        return programs
    }
}
