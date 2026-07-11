sed -i 's/LiveParentalStatus(true, false, setOf/LiveParentalStatus(true, setOf/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, true, setOf/LiveParentalStatus(true, setOf/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, false, emptySet()/LiveParentalStatus(true, emptySet(), false)/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/LiveParentalStatus(true, true, emptySet()/LiveParentalStatus(true, emptySet(), true)/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/createParentalProfile(/createParentalProfile("prov_1", /g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/createParentalProfile("prov_1", "prov_1", /createParentalProfile("prov_1", /g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/createParentalProfile("prov_1", "p1"/createParentalProfile("p1"/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/logoUrl = null/logoUrl = ""/g' app/src/test/java/com/example/ui/feature/live/LiveParentalPresentationPolicyTest.kt
sed -i 's/logoUrl = ""/logoUrl = "", epgId = "", channelNumber = ""/g' app/src/test/java/com/example/ui/feature/live/LiveParentalPresentationPolicyTest.kt
sed -i 's/logoUrl = null/logoUrl = "", epgId = "", channelNumber = ""/g' app/src/test/java/com/example/LiveViewModelTest.kt
