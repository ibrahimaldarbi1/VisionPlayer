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
    object Unknown : NetworkError() {
        override val message: String = "An unexpected network error occurred."
    }
}

fun mapThrowableToNetworkError(throwable: Throwable): NetworkError {
    if (throwable is NetworkError) return throwable
    if (throwable is kotlinx.coroutines.CancellationException) {
        throw throwable
    }
    
    if (throwable is retrofit2.HttpException) {
        val error = mapResponseCodeToNetworkError(throwable.code())
        if (error != null) return error
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
    
    return NetworkError.Unknown
}

fun mapResponseCodeToNetworkError(code: Int): NetworkError? {
    return when (code) {
        in 200..299 -> null
        401, 403 -> NetworkError.Unauthorized
        408, 504 -> NetworkError.Timeout
        405, 501 -> NetworkError.Unsupported
        in 400..499 -> NetworkError.InvalidResponse
        in 500..599 -> NetworkError.ServerError
        else -> NetworkError.ServerError
    }
}

