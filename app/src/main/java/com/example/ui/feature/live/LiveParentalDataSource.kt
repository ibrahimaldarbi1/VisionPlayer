package com.example.ui.feature.live

import kotlinx.coroutines.flow.Flow

interface LiveParentalDataSource {

    fun observeStatus(
        providerId: String
    ): Flow<LiveParentalStatus>

    suspend fun verifyPin(
        providerId: String,
        candidatePin: String
    ): Boolean
}

object DummyParentalDataSource : LiveParentalDataSource {
    override fun observeStatus(providerId: String): Flow<LiveParentalStatus> {
        return kotlinx.coroutines.flow.flowOf(LiveParentalStatus(pinConfigured = false))
    }

    override suspend fun verifyPin(providerId: String, candidatePin: String): Boolean {
        return false
    }
}
