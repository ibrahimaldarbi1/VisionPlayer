package com.example.core.support

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.config.ProviderProfile
import com.example.core.redaction.AccountHostHelper
import com.example.core.redaction.SensitiveDataRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportBuilder {

    fun buildReport(
        context: Context,
        profile: ProviderProfile,
        activeServerUrl: String?,
        subject: String,
        description: String,
        isTv: Boolean,
        cacheAgeMs: Long? = null,
        isNetworkConnected: Boolean? = null,
        errorCategory: String? = null
    ): String {
        val appName = profile.appName
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        val providerId = profile.providerId
        val profileId = profile.id
        
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val classification = if (isTv) "TV" else "Phone"
        
        val redactedHost = AccountHostHelper.formatRedactedHost(activeServerUrl)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        // Sanitize inputs
        val sanitizedSubject = SensitiveDataRedactor.redactExceptionMessage(subject)
        val sanitizedDescription = SensitiveDataRedactor.redactExceptionMessage(description)
        
        val sb = java.lang.StringBuilder()
        sb.append("=== SUPPORT DIAGNOSTIC REPORT ===\n")
        sb.append("Date/Time: $timestamp\n")
        sb.append("App Name: $appName\n")
        sb.append("App Version: $versionName ($versionCode)\n")
        sb.append("Provider ID: $providerId\n")
        sb.append("Profile ID: $profileId\n")
        sb.append("Device: $manufacturer $model\n")
        sb.append("Android OS: $androidVersion\n")
        sb.append("Classification: $classification\n")
        sb.append("Active Account Host: $redactedHost\n")
        if (errorCategory != null) {
            sb.append("Error Category: $errorCategory\n")
        }
        sb.append("---------------------------------\n")
        sb.append("Subject: $sanitizedSubject\n")
        sb.append("Description: $sanitizedDescription\n")
        sb.append("---------------------------------\n")
        sb.append("Diagnostic Metrics:\n")
        if (cacheAgeMs != null) {
            val ageMin = cacheAgeMs / (60 * 1000)
            sb.append("- Cache Age: ${ageMin} minutes\n")
        } else {
            sb.append("- Cache Age: N/A\n")
        }
        if (isNetworkConnected != null) {
            sb.append("- Network Connected: $isNetworkConnected\n")
        }
        sb.append("=================================")
        
        return sb.toString()
    }
}
