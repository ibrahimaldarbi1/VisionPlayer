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

fun mapThrowableToNetworkError(throwable: Throwable): NetworkError {
    if (throwable is NetworkError) return throwable
    if (throwable is kotlinx.coroutines.CancellationException) {
        throw throwable
    }
    
    val msg = throwable.message ?: ""
    val lowercaseMsg = msg.lowercase()
    
    if (throwable is java.net.UnknownHostException || 
        throwable is java.net.ConnectException || 
        throwable is java.net.NoRouteToHostException ||
        lowercaseMsg.contains("unable to resolve host") ||
        lowercaseMsg.contains("connect failed") ||
        lowercaseMsg.contains("network unreachable") ||
        lowercaseMsg.contains("route")
    ) {
        return NetworkError.NoConnection
    }
    
    if (throwable is java.net.SocketTimeoutException || 
        throwable is java.io.InterruptedIOException ||
        lowercaseMsg.contains("timeout") ||
        lowercaseMsg.contains("timed out")
    ) {
        return NetworkError.Timeout
    }
    
    return NetworkError.Unknown(throwable)
}

fun mapResponseCodeToNetworkError(code: Int): NetworkError? {
    return when (code) {
        200 -> null
        401, 403 -> NetworkError.Unauthorized
        405, 501 -> NetworkError.Unsupported
        in 500..599 -> NetworkError.ServerError
        else -> NetworkError.ServerError
    }
}
