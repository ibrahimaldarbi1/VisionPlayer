sed -i 's/imageUrl = item.posterUrl ?: item.backdropUrl,/imageUrl = item.posterUrl ?: item.backdropUrl ?: "",/g' app/src/main/java/com/example/ui/feature/home/HomeDashboardView.kt
