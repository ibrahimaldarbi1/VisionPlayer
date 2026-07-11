package com.example.ui.feature.live

import com.example.data.IptvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RepositoryLiveParentalDataSource(
    private val repository: IptvRepository
) : LiveParentalDataSource {

    override fun observeStatus(
        providerId: String
    ): Flow<LiveParentalStatus> {
        return repository
            .parentalSettings
            .map { settings ->
                LiveParentalPolicyParser.parse(
                    storedPin = settings?.pin,
                    storedCategoryPolicy = settings?.lockedCategories
                )
            }
            .distinctUntilChanged()
    }

    override suspend fun verifyPin(
        providerId: String,
        candidatePin: String
    ): Boolean {
        val settings =
            repository
                .getParentalSettingsDirect()

        // Temporary plaintext comparison retained only until the secure PIN hashing migration.
        return settings != null &&
            settings.pin ==
                candidatePin
    }
}
