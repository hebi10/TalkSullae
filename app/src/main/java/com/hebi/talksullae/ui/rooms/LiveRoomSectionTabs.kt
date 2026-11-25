package com.hebi.talksullae.ui.rooms

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
    onSelect: (String) -> Unit
) {
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
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("live-room-tab-$key")
            )
        }
    }
}
