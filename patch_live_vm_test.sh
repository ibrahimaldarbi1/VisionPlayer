#!/bin/bash
cat << 'TESTEOF' >> app/src/test/java/com/example/LiveViewModelTest.kt

    // 17. Add Live/EPG session continuity test
    @Test
    fun testLiveEpgSessionContinuity() = runTest {
        val channel = LiveChannel("id1", "epg1", "CH1", "http", "", "cat1", "Adult Cat", isAdult = true, channelNumber = "1")
        val category = Category("cat1", "Adult Cat")
        
        val fakeLive = FakeLiveDataSource()
        fakeLive.emitData(listOf(category), listOf(channel))
        
        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)
        fakeParental.configurePin("1234")
        
        val profile = createProfile(parentalEnabled = true)
        vm.onProfileChanged(profile)
        vm.onLiveVisibilityChanged(true) // 1. Make Live visible
        runCurrent()
        
        vm.onChannelSelected(channel)
        runCurrent()
        assertTrue(vm.uiState.value.pinDialogVisible)
        
        vm.submitParentalPin("1234") // 2. Unlock
        runCurrent()
        assertFalse(vm.uiState.value.pinDialogVisible)
        assertTrue(vm.uiState.value.parentalSessionUnlocked)
        
        // 3. Navigate from Live to EPG (Live Content remains visible)
        vm.onLiveVisibilityChanged(true)
        runCurrent()
        assertTrue(vm.uiState.value.parentalSessionUnlocked)
        
        // 4. Select another protected channel
        vm.onChannelSelected(channel)
        runCurrent()
        
        // 5. Verify no second PIN dialog
        assertFalse(vm.uiState.value.pinDialogVisible)
        
        // 6. Report visibility false (leaving both)
        vm.onLiveVisibilityChanged(false)
        runCurrent()
        assertFalse(vm.uiState.value.parentalSessionUnlocked)
        
        // 7. Return to a Live-content surface
        vm.onLiveVisibilityChanged(true)
        runCurrent()
        
        // 8. Verify protected channel requires PIN again
        vm.onChannelSelected(channel)
        runCurrent()
        assertTrue(vm.uiState.value.pinDialogVisible)
    }
TESTEOF
sed -i -e '/^}$/d' app/src/test/java/com/example/LiveViewModelTest.kt
echo "}" >> app/src/test/java/com/example/LiveViewModelTest.kt
chmod +x patch_live_vm_test.sh
./patch_live_vm_test.sh
rm patch_live_vm_test.sh