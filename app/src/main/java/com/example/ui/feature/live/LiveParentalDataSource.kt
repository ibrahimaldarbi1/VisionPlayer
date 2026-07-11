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


