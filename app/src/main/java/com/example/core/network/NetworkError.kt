package com.example.core.network

sealed class NetworkError : Exception() {
    object NoConnection : NetworkError() {
        override val message: String = "No internet connection detected. Please check your network."
    }
    object Timeout : NetworkError() {
        override val message: String = "The request timed out. Please try again later."
    }
    object Unauthorized : NetworkError() {
        override val message: String = "Authentication failed. Invalid username or password."
    }
    object ServerError : NetworkError() {
        override val message: String = "The server encountered an error. Please contact support."
    }
    object InvalidResponse : NetworkError() {
        override val message: String = "Received an invalid response from the server."
    }
    object Unsupported : NetworkError() {
        override val message: String = "The requested action is not supported by your provider."
    }
    data class Unknown(val error: Throwable) : NetworkError() {
        override val message: String = error.localizedMessage ?: "An unexpected network error occurred."
    }
}
