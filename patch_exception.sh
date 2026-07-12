sed -i 's/} catch (e: Exception) {/} catch (e: Exception) { e.printStackTrace();/g' app/src/main/java/com/example/ui/screens/HomeViewModel.kt
