package com.roni.blocks.feature.templates

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.schedule.TemplateEdit
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives [TemplateEditorViewModel.onAction] against the real store and [TemplateActions] from
 * [com.roni.blocks.testing.testActions]: every save lands in storage, every refusal is shown and
 * Save is disabled.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplateEditorViewModelTest {
    private val context = RuntimeEnvironment.getApplication()

    private lateinit var actions: TemplateActions
    private lateinit var store: TemplateStore
    private lateinit var viewModel: TemplateEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
        val rig = testActions(context)
        actions = rig.templates
        store = rig.store
        viewModel = TemplateEditorViewModel(
            actions = actions,
            store = store,
            timeFormatter = TimeFormatter(
                PrefsStorage(context, UnconfinedTestDispatcher()),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            logger = AppLogger(context),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun spec(type: BlockType, start: String, end: String, id: String) = BlockSpec(
        id = id,
        type = type,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
    )

    private suspend fun ownTemplate(vararg blocks: BlockSpec): StoredTemplate {
        val created = actions.create("Mine")
        actions.save(created.copy(blocks = blocks.toList()))
        return store.find(created.id)!!
    }

    @Test
    fun addBlock_toABlankTemplate_savesIt() = runBlocking {
        val created = actions.create("Office day")
        viewModel.load(created.id)

        viewModel.onAction(TemplateEditorAction.AddBlock)
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.Type(BlockType.Coding)))
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.LengthBy(450)))
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.Save))

        val saved = store.find(created.id)!!
        assertEquals(listOf(BlockType.Coding, BlockType.KidTime), saved.blocks.map { it.type })
        assertEquals(
            listOf("09:00" to "17:00", "17:00" to "22:30"),
            saved.blocks.map { it.start.toString() to it.end.toString() },
        )
    }

    @Test
    fun aStartInsideTheBlockBefore_showsTheReason() = runBlocking {
        val t = ownTemplate(
            spec(BlockType.Coding, "09:00", "11:30", "coding"),
            spec(BlockType.Gym, "12:00", "14:00", "gym"),
            spec(BlockType.KidTime, "17:00", "22:30", "kid"),
        )
        viewModel.load(t.id)
        val gymId = viewModel.state.value.rows.first { it.name == "Gym" }.id

        viewModel.onAction(TemplateEditorAction.OpenBlock(gymId))
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.StartAt(11 * 60)))

        val sheet = viewModel.state.value.sheet!!
        assertEquals("Coding runs until 11:30. Start at 11:30 or later.", sheet.line)
        assertTrue(sheet.result is TemplateEdit.Refused)

        // Save is disabled: the refused edit changes nothing in storage.
        val before = store.find(t.id)!!.blocks.size
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.Save))
        assertEquals(before, store.find(t.id)!!.blocks.size)
    }

    @Test
    fun longer_showsWhatMoves() = runBlocking {
        val t = ownTemplate(
            spec(BlockType.Coding, "09:00", "11:30", "coding"),
            spec(BlockType.Gym, "11:30", "13:30", "gym"),
            spec(BlockType.KidTime, "17:00", "22:30", "kid"),
        )
        viewModel.load(t.id)
        val codingId = viewModel.state.value.rows.first { it.name == "Coding" }.id

        viewModel.onAction(TemplateEditorAction.OpenBlock(codingId))
        viewModel.onAction(TemplateEditorAction.Sheet(BlockSheetAction.LengthBy(60)))

        assertEquals("Moves later: Gym", viewModel.state.value.sheet!!.line)
    }

    @Test
    fun builtIn_isReadOnly() {
        viewModel.load(TemplateId.MON_THU)

        assertTrue(viewModel.state.value.readOnly)
        val firstRow = viewModel.state.value.rows.first()
        viewModel.onAction(TemplateEditorAction.OpenBlock(firstRow.id))
        assertNull(viewModel.state.value.sheet)
        viewModel.onAction(TemplateEditorAction.AddBlock)
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun weekday_movesFromABuiltIn() = runBlocking {
        val created = actions.create("Mine")
        viewModel.load(created.id)

        viewModel.onAction(TemplateEditorAction.ToggleDay(1))

        assertEquals("Monday moves here from Monday / Thursday", viewModel.state.value.dayMoved)
        assertEquals(setOf(4), store.find(TemplateId.MON_THU)!!.weekdays)
    }

    @Test
    fun deleteTemplate_leavesThePicker() = runBlocking {
        val created = actions.create("Mine")
        viewModel.load(created.id)

        viewModel.onAction(TemplateEditorAction.DeleteTemplate)
        assertTrue(viewModel.state.value.confirmDelete)
        viewModel.onAction(TemplateEditorAction.ConfirmDelete)

        assertTrue(store.all().none { it.id == created.id })
    }
}