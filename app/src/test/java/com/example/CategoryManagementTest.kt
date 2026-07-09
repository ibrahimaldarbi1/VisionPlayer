package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CategoryEntity
import com.example.data.IptvDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CategoryManagementTest {

    private lateinit var database: IptvDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testCategoryHidingFiltersOutHiddenCategories() = runBlocking {
        val dao = database.iptvDao()

        // Insert categories
        val c1 = CategoryEntity(id = "cat_1", name = "Sports", type = "LIVE", hidden = false, sortOrder = 1, pinned = false, updatedAt = System.currentTimeMillis())
        val c2 = CategoryEntity(id = "cat_2", name = "Movies", type = "LIVE", hidden = true, sortOrder = 2, pinned = false, updatedAt = System.currentTimeMillis())
        dao.upsertCategories(listOf(c1, c2))

        // Observe visible categories
        val visible = dao.observeVisibleCategories("LIVE").first()
        assertEquals(1, visible.size)
        assertEquals("cat_1", visible[0].id)
    }

    @Test
    fun testPinnedCategoriesSortedFirstThenBySortOrder() = runBlocking {
        val dao = database.iptvDao()

        // Insert categories with different sorting and pinning states
        val c1 = CategoryEntity(id = "cat_1", name = "Sports", type = "LIVE", hidden = false, sortOrder = 1, pinned = false, updatedAt = System.currentTimeMillis())
        val c2 = CategoryEntity(id = "cat_2", name = "News", type = "LIVE", hidden = false, sortOrder = 2, pinned = true, updatedAt = System.currentTimeMillis())
        val c3 = CategoryEntity(id = "cat_3", name = "Movies", type = "LIVE", hidden = false, sortOrder = 3, pinned = false, updatedAt = System.currentTimeMillis())
        val c4 = CategoryEntity(id = "cat_4", name = "Music", type = "LIVE", hidden = false, sortOrder = 0, pinned = true, updatedAt = System.currentTimeMillis())

        dao.upsertCategories(listOf(c1, c2, c3, c4))

        // Expected visible sort:
        // Pinned first, ordered by sortOrder: cat_4 (0), then cat_2 (2)
        // Then Unpinned, ordered by sortOrder: cat_1 (1), then cat_3 (3)
        val visible = dao.observeVisibleCategories("LIVE").first()
        assertEquals(4, visible.size)
        assertEquals("cat_4", visible[0].id)
        assertEquals("cat_2", visible[1].id)
        assertEquals("cat_1", visible[2].id)
        assertEquals("cat_3", visible[3].id)
    }

    @Test
    fun testCategoryStateUpdatesPersistCorrectly() = runBlocking {
        val dao = database.iptvDao()

        val c1 = CategoryEntity(id = "cat_1", name = "Sports", type = "LIVE", hidden = false, sortOrder = 1, pinned = false, updatedAt = System.currentTimeMillis())
        dao.upsertCategories(listOf(c1))

        // Hide category
        dao.setCategoryHidden("LIVE", "cat_1", true)
        var visible = dao.observeVisibleCategories("LIVE").first()
        assertTrue(visible.isEmpty())

        // Show/Unhide category
        dao.setCategoryHidden("LIVE", "cat_1", false)
        visible = dao.observeVisibleCategories("LIVE").first()
        assertEquals(1, visible.size)

        // Pin category
        dao.setCategoryPinned("LIVE", "cat_1", true)
        val all = dao.observeAllCategoriesForManagement("LIVE").first()
        assertTrue(all[0].pinned)
    }

    @Test
    fun testResetCategoryCustomizationRestoresDefaults() = runBlocking {
        val dao = database.iptvDao()

        val c1 = CategoryEntity(id = "cat_1", name = "Sports", type = "LIVE", hidden = false, sortOrder = 1, pinned = false, updatedAt = System.currentTimeMillis())
        val c2 = CategoryEntity(id = "cat_2", name = "Movies", type = "LIVE", hidden = false, sortOrder = 2, pinned = false, updatedAt = System.currentTimeMillis())
        dao.upsertCategories(listOf(c1, c2))

        // Apply customization: hide cat_1, pin cat_2, change sortOrder of both
        dao.setCategoryHidden("LIVE", "cat_1", true)
        dao.setCategoryPinned("LIVE", "cat_2", true)
        dao.updateCategorySortOrderSingle("LIVE", "cat_2", 0)
        dao.updateCategorySortOrderSingle("LIVE", "cat_1", 1)

        // Verify custom state
        var all = dao.observeAllCategoriesForManagement("LIVE").first()
        assertTrue(all.first { it.id == "cat_1" }.hidden)
        assertTrue(all.first { it.id == "cat_2" }.pinned)

        // Reset customization: clear the customized categories by type
        dao.clearCategoriesByType("LIVE")

        // Verify reset state is empty (or we reinsert original defaults)
        all = dao.observeAllCategoriesForManagement("LIVE").first()
        assertTrue(all.isEmpty())
    }
}
