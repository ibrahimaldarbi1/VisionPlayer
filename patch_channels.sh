sed -i '67,138c\
    LiveParentalPinDialog(\
        visible = parentalControlsEnabled && pinDialogVisible,\
        verificationLoading = pinVerificationLoading,\
        verificationError = pinVerificationError,\
        profile = profile,\
        onSubmit = onSubmitParentalPin,\
        onCancel = onCancelParentalDialog\
    )' app/src/main/java/com/example/ui/feature/live/LiveChannelsView.kt
