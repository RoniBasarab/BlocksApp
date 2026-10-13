package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.templatepicker.stripFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The template list for the 3.1.0 Templates tab. Read-only in Base slice 2 (one card per stored
 * template; tapping reports the id). Track B turns [TemplateSection] into the editor entry point.
 */
class TemplateListViewModel : ViewModel() {
    private val _templates = MutableStateFlow(templates())
    val templates: StateFlow<List<StoredTemplate>> = _templates.asStateFlow()

    private fun templates(): List<StoredTemplate> {
        TemplateStore.instance?.let { return it.all() }
        return TemplateId.builtIns.map { id ->
            val seed = builtInTemplateSeeds.getValue(id)
            StoredTemplate(id, seed.title, seed.subtitle, seed.short, seed.iconKey, seed.accentArgb, seed.weekdays, emptyList())
        }
    }
}

/** One card per stored template: icon in its accent, title, weekdays and the picker's strip. */
@Composable
fun TemplateSection(
    onOpenTemplate: (TemplateId) -> Unit,
    onNewTemplate: () -> Unit,
    viewModel: TemplateListViewModel = viewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    SectionHeader("Templates")
    templates.forEach { template ->
        val display = templateDisplay(template.id)
        AppCard(
            onClick = { onOpenTemplate(template.id) },
            modifier = Modifier.fillMaxWidth().testTag("template_${template.id.key}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(display.icon, null, tint = display.accent)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(display.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                        if (template.id in TemplateId.builtIns) {
                            Spacer(Modifier.width(Spacing.s))
                            TagLabel("Built-in")
                        }
                    }
                    Text(display.short, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
                BuildingBlockStrip(template.blocks)
            }
        }
        Spacer(Modifier.height(Spacing.s))
    }
}

/** The picker's strip: one coloured segment per distinct block kind, in the template's accent. */
@Composable
private fun BuildingBlockStrip(blocks: List<com.roni.blocks.core.model.BlockSpec>) {
    val strip = stripFor(blocks)
    if (strip.isEmpty()) return
    Row(Modifier.width(56.dp)) {
        strip.forEach { (color, _) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TagLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .border(1.dp, AppTheme.colors.outline, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}