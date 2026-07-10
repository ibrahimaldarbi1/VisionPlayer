package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppContainer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {

    @Test
    fun testAppContainerSingletons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        // 1. Repeated access to database returns the same instance.
        val db1 = container.database
        val db2 = container.database
        assertSame("Repeated access to database must return the same instance", db1, db2)

        // 2. Repeated access to dao uses the same database/instance
        val dao1 = container.dao
        val dao2 = container.dao
        assertSame("Repeated access to dao must return the same instance", dao1, dao2)

        // 3. Repeated access to repository returns the same instance.
        val repo1 = container.repository
        val repo2 = container.repository
        assertSame("Repeated access to repository must return the same instance", repo1, repo2)

        // 4. Repeated access to sharedHttpClient returns the same instance.
        val client1 = container.sharedHttpClient
        val client2 = container.sharedHttpClient
        assertSame("Repeated access to sharedHttpClient must return the same instance", client1, client2)
    }

    @Test
    fun testMainActivityDoesNotConstructRepository() {
        // Find MainActivity.kt relative to the test environment
        val pathsToTry = listOf(
            "src/main/java/com/example/MainActivity.kt",
            "../app/src/main/java/com/example/MainActivity.kt",
            "app/src/main/java/com/example/MainActivity.kt"
        )
        var mainActivityFile: File? = null
        for (path in pathsToTry) {
            val file = File(path)
            if (file.exists()) {
                mainActivityFile = file
                break
            }
        }

        assertNotNull("MainActivity.kt file was not found under expected paths", mainActivityFile)
        val content = mainActivityFile!!.readText()

        // 5. MainActivity production code no longer constructs its own repository/database
        val containsDirectDbConstruction = content.contains("IptvDatabase.getDatabase(this)")
        val containsDirectRepoConstruction = content.contains("IptvRepository(database.iptvDao(), this)")

        assertFalse("MainActivity should not directly call IptvDatabase.getDatabase(this)", containsDirectDbConstruction)
        assertFalse("MainActivity should not directly construct IptvRepository", containsDirectRepoConstruction)
    }
}
