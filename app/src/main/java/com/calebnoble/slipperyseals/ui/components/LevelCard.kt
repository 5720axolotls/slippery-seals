package com.calebnoble.slipperyseals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebnoble.slipperyseals.model.LevelCompletion
import com.calebnoble.slipperyseals.ui.theme.CompletedGreen
import com.calebnoble.slipperyseals.ui.theme.StarGold
import com.calebnoble.slipperyseals.ui.theme.StarGoldText
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LevelCard(
    levelNumber: Int,
    completion: LevelCompletion,
    bestMoves: Int?,
    optimalMoves: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (completion) {
        LevelCompletion.INCOMPLETE -> MaterialTheme.colorScheme.outlineVariant
        LevelCompletion.COMPLETED -> CompletedGreen
        LevelCompletion.STARRED -> StarGold
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().let {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$levelNumber",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (completion == LevelCompletion.STARRED) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val radius = size.width / 2
                        val innerRadius = radius * 0.4f
                        val path = Path()
                        for (i in 0 until 10) {
                            val r = if (i % 2 == 0) radius else innerRadius
                            val angle = Math.toRadians((i * 36.0 - 90.0))
                            val x = cx + r * cos(angle).toFloat()
                            val y = cy + r * sin(angle).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, StarGold, style = Fill)
                    }
                } else if (completion == LevelCompletion.COMPLETED) {
                    Text("Done", fontSize = 10.sp, color = CompletedGreen)
                }
                if (bestMoves != null) {
                    Text(
                        "$bestMoves / $optimalMoves",
                        fontSize = 10.sp,
                        color = if (bestMoves <= optimalMoves) StarGoldText else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
