
    @Test
    fun categoryObserverFailure_withHideMode_doesNotExposeAdultChannel() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val adultChannel = LiveChannel("ch1", "Adult", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.channelsEmissions = listOf(listOf(adultChannel))
        
        // Hide mode on
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        advanceUntilIdle()

        // Category observer fails
        fakeLive.observeError = RuntimeException("Observer failed")
        
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    @Test
    fun categoryObserverFailure_withHideMode_doesNotExposeLockedCategoryChannel() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val lockedChannel = LiveChannel("ch1", "Locked", "cat_1", "Cat 1", "url", "", false, false)
        fakeLive.channelsEmissions = listOf(listOf(lockedChannel))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        advanceUntilIdle()

        fakeLive.observeError = RuntimeException("Observer failed")
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    @Test
    fun categoryObserverFailure_stillExposesUnrelatedSafeChannel() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val safeChannel = LiveChannel("ch1", "Safe", "cat_1", "Cat 1", "url", "", false, false)
        fakeLive.channelsEmissions = listOf(listOf(safeChannel))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_2")))
        advanceUntilIdle()

        fakeLive.observeError = RuntimeException("Observer failed")
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals("ch1", vm.uiState.value.channels[0].id)
    }

    @Test
    fun zeroTotalNonAuthoritativeSnapshot_stillAppliesAdultFiltering() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val adultChannel = LiveChannel("ch1", "Adult", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.channelsEmissions = listOf(listOf(adultChannel))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        advanceUntilIdle()

        fakeLive.snapshotEmissions = listOf(LiveCategoryVisibilitySnapshot(emptyList(), isAuthoritative = false))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    @Test
    fun liveDisable_clearsLockedCategoryIds() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_1")))
        advanceUntilIdle()
        assertEquals(setOf("cat_1"), vm.uiState.value.lockedLiveCategoryIds)

        val disabledProfile = createParentalProfile("prov_1", liveTvEnabled = false, parentalEnabled = true)
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.lockedLiveCategoryIds.isEmpty())
    }

    @Test
    fun liveDisable_clearsHideMode() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hideAdultContent)

        val disabledProfile = createParentalProfile("prov_1", liveTvEnabled = false, parentalEnabled = true)
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hideAdultContent)
    }

    @Test
    fun liveDisable_clearsPendingCategory() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        
        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(listOf(cat1))
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        vm.selectCategory("cat_1")
        advanceUntilIdle()
        assertEquals("cat_1", vm.uiState.value.pendingParentalCategoryId)

        val disabledProfile = createParentalProfile("prov_1", liveTvEnabled = false, parentalEnabled = true)
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingParentalCategoryId)
    }

    @Test
    fun parentalDisable_restoresCachedCategoriesWithoutReloading() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(listOf(cat1))
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.categories.isEmpty()) // Hidden

        fakeLive.categoriesCallCount = 0
        
        val disabledProfile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = false)
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.categories.size)
        assertEquals(0, fakeLive.categoriesCallCount) // No reload
    }

    @Test
    fun parentalDisable_restoresCachedChannelsWithoutReloading() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val ch1 = LiveChannel("ch1", "Ch 1", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.channelsEmissions = listOf(listOf(ch1))
        fakeLive.snapshotEmissions = listOf(LiveCategoryVisibilitySnapshot(listOf(Category("cat_1", "Cat 1", "LIVE")), true))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.channels.isEmpty()) // Hidden

        fakeLive.channelsCallCount = 0

        val disabledProfile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = false)
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals(0, fakeLive.channelsCallCount)
    }

    @Test
    fun providerHidden_staleCategorySelection_performsNoChannelRequest() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = false)
        vm.onProfileChanged(profile)
        
        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(emptyList()) // Hidden by provider
        
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        fakeLive.channelsCallCount = 0
        vm.selectCategory("cat_1")
        advanceUntilIdle()
        
        assertEquals(0, fakeLive.channelsCallCount)
    }

    @Test
    fun providerHidden_staleChannelClick_emitsNoPlayback() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = false)
        vm.onProfileChanged(profile)

        val ch1 = LiveChannel("ch1", "Ch 1", "cat_1", "Cat 1", "url", "", false, false)
        fakeLive.categoriesEmissions = listOf(listOf(Category("cat_1", "Cat 1", "LIVE")))
        fakeLive.observeEmissions = listOf(emptyList()) // Hidden
        fakeLive.channelsEmissions = listOf(listOf(ch1))
        
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        vm.onChannelSelected(ch1)
        advanceUntilIdle()
        assertNull(vm.uiEvent.value)
    }

    @Test
    fun parentalHidden_staleCategorySelection_performsNoRequest() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        
        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(listOf(cat1))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        fakeLive.channelsCallCount = 0
        vm.selectCategory("cat_1")
        advanceUntilIdle()
        
        assertEquals(0, fakeLive.channelsCallCount)
        assertNull(vm.uiState.value.pendingParentalCategoryId)
    }

    @Test
    fun parentalHidden_staleChannelClick_emitsNoPlayback() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val ch1 = LiveChannel("ch1", "Ch 1", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.observeEmissions = listOf(listOf(Category("cat_1", "Cat 1", "LIVE")))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        vm.onChannelSelected(ch1)
        advanceUntilIdle()
        assertNull(vm.uiEvent.value)
    }

    @Test
    fun turningHideModeOff_restoresCachedCategories() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(listOf(cat1))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.categories.isEmpty())

        fakeLive.categoriesCallCount = 0
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_1")))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.categories.size)
        assertEquals(0, fakeLive.categoriesCallCount)
    }

    @Test
    fun turningHideModeOff_restoresCachedChannels() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val ch1 = LiveChannel("ch1", "Ch 1", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.channelsEmissions = listOf(listOf(ch1))
        fakeLive.snapshotEmissions = listOf(LiveCategoryVisibilitySnapshot(listOf(Category("cat_1", "Cat 1", "LIVE")), true))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.channels.isEmpty())

        fakeLive.channelsCallCount = 0
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = emptySet()))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals(0, fakeLive.channelsCallCount)
    }

    @Test
    fun providerHidden_andParentalHidden_categoriesComposeCorrectly() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        
        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        val cat2 = Category("cat_2", "Cat 2", "LIVE")
        val cat3 = Category("cat_3", "Cat 3", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1, cat2, cat3))
        fakeLive.observeEmissions = listOf(listOf(cat2, cat3)) // Provider hides cat1
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_2"))) // Parental hides cat2
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        assertEquals(1, vm.uiState.value.categories.size)
        assertEquals("cat_3", vm.uiState.value.categories[0].id)
    }

    @Test
    fun observerFailure_preservesLastSuccessfulPolicy() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.hideAdultContent)
        assertEquals(setOf("cat_1"), vm.uiState.value.lockedLiveCategoryIds)
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_2")))
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.hideAdultContent)
        assertEquals(setOf("cat_2"), vm.uiState.value.lockedLiveCategoryIds)
    }

    @Test
    fun nowHiddenPendingCategoryVerification_isCancelled() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)
        
        val cat1 = Category("cat_1", "Cat 1", "LIVE")
        fakeLive.categoriesEmissions = listOf(listOf(cat1))
        fakeLive.observeEmissions = listOf(listOf(cat1))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        vm.selectCategory("cat_1")
        advanceUntilIdle()
        assertEquals("cat_1", vm.uiState.value.pendingParentalCategoryId)
        
        // Hide mode on
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_1")))
        advanceUntilIdle()
        
        assertNull(vm.uiState.value.pendingParentalCategoryId)
    }

    @Test
    fun nowHiddenPendingChannelVerification_cannotPlay() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true)
        vm.onProfileChanged(profile)

        val ch1 = LiveChannel("ch1", "Ch 1", "cat_1", "Cat 1", "url", "", false, true)
        fakeLive.observeEmissions = listOf(listOf(Category("cat_1", "Cat 1", "LIVE")))
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = emptySet()))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()

        vm.onChannelSelected(ch1)
        advanceUntilIdle()
        assertEquals("ch1", vm.uiState.value.pendingParentalChannel?.id)

        // Hide mode on
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet()))
        advanceUntilIdle()

        vm.submitParentalPin("1234") // Should not play
        advanceUntilIdle()
        
        assertNull(vm.uiEvent.value)
    }

    @Test
    fun policyChanges_doNotReloadFavorites() = runTest {
        val fakeLive = FakeLiveDataSource()
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        val profile = createParentalProfile("prov_1", liveTvEnabled = true, parentalEnabled = true, favoritesEnabled = true)
        vm.onProfileChanged(profile)
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = setOf("cat_1")))
        vm.onLiveVisibilityChanged(true)
        advanceUntilIdle()
        
        fakeFavorites.observeFavoritesCallCount = 0
        
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = setOf("cat_2")))
        advanceUntilIdle()
        
        assertEquals(0, fakeFavorites.observeFavoritesCallCount)
    }
