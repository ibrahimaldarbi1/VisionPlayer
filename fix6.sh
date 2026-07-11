sed -i 's/assertNull(vm.uiEvent)/assertTrue(vm.uiState.value.pendingParentalChannel == null)/g' app/src/test/java/com/example/LiveViewModelTest.kt
