package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.ProviderProfile
import com.example.config.FeatureConfig
import com.example.config.BrandingConfig
import com.example.config.SupportConfig
import com.example.data.*
import com.example.ui.screens.HomeViewModel
import com.example.ui.feature.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureEnforcementTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: IptvDatabase
    private lateinit var repo: IptvRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = IptvRepository(database.iptvDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createProfile(id: String, providerId: String, features: FeatureConfig): ProviderProfile {
        return ProviderProfile(
            id = id,
            providerId = providerId,
            name = "Test Profile",
            appName = "Test App",
            features = features,
            branding = BrandingConfig(),
            support = SupportConfig()
        )
    }

    @Test
    fun `test Search invalidation on profile change`() = runTest {
        val viewModel = SearchViewModel(repo)
        
        val p1 = createProfile("p1", "prov1", FeatureConfig(searchEnabled = true))
        viewModel.onProfileChanged(p1)
        viewModel.onQueryChanged("test")
        advanceUntilIdle()
        
        assertEquals("test", viewModel.uiState.value.query)
        
        val p2 = createProfile("p2", "prov2", FeatureConfig(searchEnabled = true))
        viewModel.onProfileChanged(p2)
        
        // Ensure query is reset
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun `test Favorites mutation disabled`() = runTest {
        val viewModel = HomeViewModel(repo)
        
        val p1 = createProfile("p1", "prov1", FeatureConfig(moviesEnabled = true, favoritesEnabled = false))
        viewModel.onProfileChanged(p1)
        advanceUntilIdle()
        
        val fav = FavoriteEntity(contentId = "1", contentType = "MOVIE", title = "M", posterOrLogo = "", streamUrl = "http", addedAt = 0L)
        
        viewModel.toggleFavorite(fav)
        advanceUntilIdle()
        
        // Should not add favorite because the identity has changed
        val currentFavorites = repo.favorites.first()
        assertTrue(currentFavorites.isEmpty())
    }
}
