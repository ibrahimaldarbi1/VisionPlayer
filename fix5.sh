sed -i 's/"url", "", false, true/"url", isLocked = false, isAdult = true/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/"url", "", false, false/"url", isLocked = false, isAdult = false/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/isAuthoritative =/authoritative =/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/uiEvent.value/uiEvent/g' app/src/test/java/com/example/LiveViewModelTest.kt
