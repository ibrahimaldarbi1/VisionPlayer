cat << 'INNER' > patch.kt
    private data class EpgRefreshIdentity(
        val profileId: String,
        val providerId: String,
        val liveEnabled: Boolean,
        val epgEnabled: Boolean,
        val generation: Int
    )

    private var epgRefreshGeneration = 0
    private var activeEpgIdentity: EpgRefreshIdentity? = null
    
    private val categoryOpJobs = mutableListOf<Job>()
INNER
