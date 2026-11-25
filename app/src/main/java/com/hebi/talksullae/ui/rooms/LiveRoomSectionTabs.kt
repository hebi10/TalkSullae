package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LiveRoomSectionTabs(
    current: String,
    onSelect: (String) -> Unit,
    includeMembers: Boolean = true
) {
    if (!includeMembers) {
        val tabs = listOf("chat" to "채팅", "games" to "게임", "records" to "기록")
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("live-room-tabs")) {
            val scrollable = maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
            val shape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, shape)
                    .padding(4.dp).then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { (key, label) ->
                    val selected = current == key
                    Box(
                        modifier = (if (scrollable) Modifier.widthIn(min = 80.dp) else Modifier.weight(1f))
                            .heightIn(min = 48.dp).clip(shape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .selectable(selected = selected, onClick = { onSelect(key) }, role = Role.Tab)
                            .testTag("live-room-tab-$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, modifier=Modifier.padding(horizontal=8.dp,vertical=6.dp),
                            color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style=MaterialTheme.typography.labelLarge, textAlign=TextAlign.Center)
                    }
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .testTag("live-room-tabs"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "chat" to "채팅",
            "games" to "게임",
            "records" to "기록",
            "members" to "멤버 관리"
        ).forEach { (key, label) ->
            FilterChip(
                selected = current == key,
                onClick = { onSelect(key) },
                label = { Text(label, modifier = Modifier.padding(horizontal = 4.dp)) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.heightIn(min = 48.dp).testTag("live-room-tab-$key")
            )
        }
    }
}
