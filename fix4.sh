sed -i 's/LiveParentalStatus(true, emptySet(), true)))/LiveParentalStatus(true, emptySet(), true))/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, emptySet(), false)))/LiveParentalStatus(true, emptySet(), false))/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, emptySet(), true))/LiveParentalStatus(pinConfigured = true, hideAdultContent = true, lockedCategoryIds = emptySet())/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, emptySet(), false))/LiveParentalStatus(pinConfigured = true, hideAdultContent = false, lockedCategoryIds = emptySet())/g' app/src/test/java/com/example/LiveViewModelTest.kt
