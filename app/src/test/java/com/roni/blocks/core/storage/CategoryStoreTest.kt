package com.roni.blocks.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.CategorySet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Task-19 slice 1: the category writes (3.1.0) — add, rename/recolour, delete; built-ins are protected. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CategoryStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    @After
    fun restoreCategories() {
        // The store mutates the global Categories.current and the categories prefs; put both back
        // so tests that run after this one in the same JVM keep reading the defaults.
        Categories.current = CategorySet.DEFAULT
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    @Test
    fun add_update_delete_followTheRules() = runBlocking {
        val store = CategoryStore(PrefsStorage(context, UnconfinedTestDispatcher()))
        store.load()

        val added = store.add("Office", 0xFFE08A7A, work = true, pomodoro = false)
        val office = (added as CategoryResult.Saved).info!!
        assertEquals("cat_1", office.id)
        assertTrue("a new category is never built-in", !office.builtIn)

        assertEquals(CategoryResult.NameTaken, store.add("office", 0xFF6FB7D6, work = true, pomodoro = false))

        val renamed = store.update(CategoryInfo("cv", "CV sending", 0xFFA99BC9, work = false, pomodoro = true, builtIn = true))
        val cv = (renamed as CategoryResult.Saved).info!!
        assertEquals("CV sending", cv.name)
        assertEquals("a built-in always keeps its work flag", true, cv.work)

        assertEquals(CategoryResult.BuiltIn, store.delete("cv"))
        assertEquals(CategoryResult.EmptyName, store.add("", 0xFFE08A7A, work = true, pomodoro = false))
    }
}