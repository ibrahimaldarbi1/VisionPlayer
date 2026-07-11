sed -i '/val footballState by footballViewModel.uiState.collectAsStateWithLifecycle()/a\
\
    // EPG ViewModel & States\
    val epgViewModelFactory = remember(repository) {\
        com.example.core.viewmodel.AppViewModelFactory {\
            com.example.ui.feature.epg.EpgViewModel(\
                dataSource = com.example.ui.feature.epg.RepositoryEpgDataSource(repository)\
            )\
        }\
    }\
    val epgViewModel: com.example.ui.feature.epg.EpgViewModel = viewModel(factory = epgViewModelFactory)\
    val epgState by epgViewModel.uiState.collectAsStateWithLifecycle()\
\
    LaunchedEffect(profile) {\
        epgViewModel.onProfileChanged(profile)\
    }\
\
    LaunchedEffect(profile.providerId, liveState.channels) {\
        epgViewModel.onChannelsChanged(\
            providerId = profile.providerId,\
            channels = liveState.channels\
        )\
    }\
\
    LaunchedEffect(activeTab) {\
        epgViewModel.onGuideVisibilityChanged(activeTab == "EPG")\
    }\
\
    DisposableEffect(epgViewModel) {\
        onDispose {\
            epgViewModel.onGuideVisibilityChanged(false)\
        }\
    }' app/src/main/java/com/example/ui/screens/HomeScreen.kt
