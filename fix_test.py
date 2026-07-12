with open("app/src/test/java/com/example/ui/feature/shell/FeatureAvailabilityPolicyTest.kt", "r") as f:
    content = f.read()

content = content.replace("shouldShowSearch", "isDestinationEnabled")
content = content.replace("FeatureAvailabilityPolicy.isDestinationEnabled(allEnabled)", "FeatureAvailabilityPolicy.isDestinationEnabled(AppDestination.SEARCH, allEnabled)")
content = content.replace("FeatureAvailabilityPolicy.isDestinationEnabled(allDisabled)", "FeatureAvailabilityPolicy.isDestinationEnabled(AppDestination.SEARCH, allDisabled)")

content = content.replace("shouldShowSupportPage", "shouldShowSupport")
content = content.replace("shouldShowParentalControl", "shouldShowParentalControls")
content = content.replace("shouldShowFootballSchedule", "shouldShowFootball")
content = content.replace("shouldShowRecentlyWatched", "shouldCollectRecentlyWatched")
content = content.replace("shouldShowContinueWatching", "shouldCollectContinueWatching")

with open("app/src/test/java/com/example/ui/feature/shell/FeatureAvailabilityPolicyTest.kt", "w") as f:
    f.write(content)

