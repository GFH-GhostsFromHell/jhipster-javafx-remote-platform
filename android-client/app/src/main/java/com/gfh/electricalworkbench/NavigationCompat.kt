package com.gfh.electricalworkbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact five-item navigation control used by the expanded mobile shell.
 * This intentionally avoids depending on Material3's RowScope-only NavigationBarItem
 * from inside a reusable top-level helper.
 */
@Composable
internal fun NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    colors: Any? = null
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredColors = colors
    Column(
        modifier = Modifier
            .width(72.dp)
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        label()
        if (selected) {
            Text("•", color = Color(0xFF22D3EE), fontSize = 10.sp)
        }
    }
}
