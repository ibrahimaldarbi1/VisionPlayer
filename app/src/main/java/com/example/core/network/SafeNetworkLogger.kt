package com.example.core.network

import android.util.Log
import com.example.core.redaction.SensitiveDataRedactor
import okhttp3.Interceptor
import okhttp3.Response

object SafeNetworkLogger : Interceptor {
    private const val TAG = "SafeNetworkLogger"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val originalUrl = request.url.toString()
        val redactedHost = SensitiveDataRedactor.redactHost(originalUrl)
        
        val isDebug = com.example.BuildConfig.DEBUG
        
        if (isDebug) {
            Log.d(TAG, "--> SEND $method to host: $redactedHost")
        }
        
        val startTime = System.nanoTime()
        
        val response = try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(e.message ?: "Unknown Connection Error")
            Log.e(TAG, "<-- FAIL $method to host $redactedHost: $redactedMsg")
            throw e
        } catch (e: Exception) {
            val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(e.message ?: "Unknown Error")
            Log.e(TAG, "<-- FAIL $method to host $redactedHost: $redactedMsg")
            throw e
        }
        
        val durationMs = (System.nanoTime() - startTime) / 1e6
        val code = response.code
        
        if (isDebug) {
            Log.d(TAG, "<-- RECV $code in ${durationMs.toInt()}ms for host $redactedHost")
        } else {
            Log.i(TAG, "$method $redactedHost returned $code in ${durationMs.toInt()}ms")
        }
        
        return response
    }
}
