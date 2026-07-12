package com.example.core

import android.content.Context
import androidx.work.WorkManager
import com.example.core.network.NetworkClientFactory
import com.example.core.network.XtreamApiClient
import com.example.core.sync.SyncCoordinator
import com.example.core.sync.NoOpSyncCoordinator
import com.example.data.IptvDatabase
import com.example.data.IptvRepository

class AppContainer(
    applicationContext: Context
) {
    private val context =
        applicationContext
            .applicationContext

    val database: IptvDatabase by lazy {
        IptvDatabase.getDatabase(context)
    }

    val dao by lazy {
        database.iptvDao()
    }

    val sharedHttpClient by lazy {
        NetworkClientFactory.sharedClient
    }

    val footballHttpClient by lazy {
        NetworkClientFactory.footballClient
    }

    val xmltvHttpClient by lazy {
        NetworkClientFactory.xmltvClient
    }

    val xtreamApiClient by lazy {
        XtreamApiClient(
            client = sharedHttpClient
        )
    }

    val repository: IptvRepository by lazy {
        IptvRepository(
            dao = dao,
            context = context,
            xtreamApiClient = xtreamApiClient
        )
    }

    val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    val syncCoordinator: SyncCoordinator by lazy {
        NoOpSyncCoordinator()
    }
}
