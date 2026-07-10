package com.example.core.network

import android.util.Log
import com.example.core.redaction.SensitiveDataRedactor
import okhttp3.Interceptor
import okhttp3.Response

object SafeNetworkLogger : Interceptor {
    private const val TAG = "SafeNetworkLogger"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url.toString()
        val redactedUrl = SensitiveDataRedactor.redactUrl(originalUrl)
        val method = request.method
        
        Log.d(TAG, "--> SEND $method $redactedUrl")
        val startTime = System.nanoTime()
        
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(e.message ?: "Unknown Connection Error")
            Log.e(TAG, "<-- FAIL $method $redactedUrl: $redactedMsg")
            throw e
        }
        
        val durationMs = (System.nanoTime() - startTime) / 1e6
        val code = response.code
        Log.d(TAG, "<-- RECV $code in ${durationMs.toInt()}ms for $redactedUrl")
        
        return response
    }
}
