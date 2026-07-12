cat << 'INNER' > app/src/test/java/com/example/DebugHomeTest.kt
package com.example
import org.junit.Test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import com.example.data.*
import com.example.ui.screens.HomeViewModel
import com.example.config.*

class DebugHomeTest {
    @Test
    fun testOutput() = runTest {
        println("STARTING TEST")
    }
}
INNER
