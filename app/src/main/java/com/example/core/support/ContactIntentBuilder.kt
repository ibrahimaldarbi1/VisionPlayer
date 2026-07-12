package com.example.core.support

import android.content.Context
import android.content.Intent
import android.net.Uri

object ContactIntentBuilder {

    /**
     * Validates if a string is a well-formatted email address.
     */
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        return emailRegex.matches(email)
    }

    /**
     * Builds a mailto Intent containing the recipient, subject, and body.
     */
    fun buildEmailIntent(recipient: String, subject: String, body: String): Intent {
        val uriString = "mailto:" + Uri.encode(recipient) +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body)
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(uriString)
        }
    }

    /**
     * Normalizes a Telegram handle from various input formats:
     * - @username
     * - username
     * - t.me/username
     * - https://t.me/username
     *
     * Returns the clean username segment or null if malformed/unsafe.
     */
    fun normalizeTelegramHandle(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var cleaned = input.trim()
        
        if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring("https://".length)
        } else if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring("http://".length)
        }
        
        if (cleaned.startsWith("t.me/")) {
            cleaned = cleaned.substring("t.me/".length)
        } else if (cleaned.startsWith("telegram.me/")) {
            cleaned = cleaned.substring("telegram.me/".length)
        } else if (cleaned.startsWith("telegram.dog/")) {
            cleaned = cleaned.substring("telegram.dog/".length)
        }
        
        if (cleaned.startsWith("@")) {
            cleaned = cleaned.substring(1)
        }
        
        // Remove trailing slashes or queries if present
        cleaned = cleaned.substringBefore("/")
        cleaned = cleaned.substringBefore("?")
        
        if (cleaned.length < 5 || cleaned.length > 32) return null
        
        val regex = Regex("^[a-zA-Z0-9_]{5,32}$")
        if (!regex.matches(cleaned)) return null
        
        return cleaned
    }

    /**
     * Builds a Telegram HTTPS web link.
     */
    fun buildTelegramUrl(handle: String): String {
        return "https://t.me/$handle"
    }

    /**
     * Retains digits only, rejecting empty or clearly unusable numbers.
     * International numbers can be between 7 to 15 digits.
     */
    fun normalizeWhatsAppNumber(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val digits = input.filter { it.isDigit() }
        if (digits.length < 7 || digits.length > 15) return null
        return digits
    }

    /**
     * Builds a WhatsApp wa.me HTTPS link.
     */
    fun buildWhatsAppUrl(number: String, text: String): String {
        return "https://wa.me/$number?text=${Uri.encode(text)}"
    }
}
