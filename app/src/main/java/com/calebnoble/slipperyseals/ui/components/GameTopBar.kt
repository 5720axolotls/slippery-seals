package com.calebnoble.slipperyseals.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameTopBar(
    moveCount: Int,
    optimalMoves: Int,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onBack) {
                Text("<")
            }
            TextButton(onClick = onUndo, enabled = canUndo) {
                Text("Undo")
            }
            TextButton(onClick = onReset, enabled = moveCount > 0) {
                Text("Reset")
            }
        }

        Text(
            text = "Moves: $moveCount / $optimalMoves",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        TextButton(onClick = onMenu) {
            Text("Menu")
        }
    }
}
