sed -i '532,538c\
                        TvGuideView(\
                            uiState = epgState,\
                            onSelectChannel = epgViewModel::selectChannel,\
                            onRequestPlay = liveViewModel::onChannelSelected,\
                            onRetryPrograms = epgViewModel::retryPrograms,\
                            onDismissProgramsError = epgViewModel::dismissProgramsError,\
                            parentalControlsEnabled = liveState.parentalControlsEnabled,\
                            parentalLoading = liveState.parentalLoading,\
                            parentalLoadError = liveState.parentalLoadError,\
                            pinDialogVisible = liveState.pinDialogVisible,\
                            pinVerificationLoading = liveState.pinVerificationLoading,\
                            pinVerificationError = liveState.pinVerificationError,\
                            lockedLiveCategoryIds = liveState.lockedLiveCategoryIds,\
                            onSubmitParentalPin = liveViewModel::submitParentalPin,\
                            onCancelParentalDialog = liveViewModel::cancelParentalDialog,\
                            onRetryParentalStatus = liveViewModel::retryParentalStatus,\
                            onDismissParentalLoadError = liveViewModel::dismissParentalLoadError,\
                            isTv = isTv,\
                            profile = profile\
                        )' app/src/main/java/com/example/ui/screens/HomeScreen.kt
