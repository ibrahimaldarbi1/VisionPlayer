sed -i 's/val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)/val fakeLive = FakeLiveDataSource()\n        val vm = LiveViewModel(fakeLive, fakeFavorites, fakeParental)/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/createProfile/createParentalProfile/g' app/src/test/java/com/example/LiveViewModelTest.kt
sed -i 's/vm.enableLiveTv("prov_1")/vm.onLiveVisibilityChanged(true)/g' app/src/test/java/com/example/LiveViewModelTest.kt
