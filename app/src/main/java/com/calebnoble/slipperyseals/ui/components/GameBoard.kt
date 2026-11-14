package com.calebnoble.slipperyseals.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.calebnoble.slipperyseals.model.*
import com.calebnoble.slipperyseals.ui.theme.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun GameBoard(
    gameState: GameState,
    wallSet: Set<Wall>,
    onCellTapped: (Position) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = gameState.levelData.rows
    val cols = gameState.levelData.cols
    var pressedDirection by remember { mutableStateOf<Direction?>(null) }

    val arrowScales = Direction.entries.associateWith { dir ->
        animateFloatAsState(
            targetValue = if (pressedDirection == dir && dir in gameState.validMoves) 1.35f else 1.0f,
            animationSpec = tween(100),
            label = "arrow_scale_$dir"
        ).value
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(cols.toFloat() / rows.toFloat())
            .pointerInput(rows, cols, gameState.selectedSealId, gameState.validMoves.keys.toSet()) {
                detectTapGestures(
                    onPress = { offset ->
                        if (gameState.selectedSealId != null) {
                            val cellW = size.width.toFloat() / cols
                            val cellH = size.height.toFloat() / rows
                            val col = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
                            val row = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                            val seal = gameState.seals.firstOrNull { it.id == gameState.selectedSealId }
                            if (seal != null) {
                                pressedDirection = hitTestArrowDirection(
                                    seal.position, gameState.validMoves, Position(row, col)
                                )
                            }
                        }
                        tryAwaitRelease()
                        pressedDirection = null
                    },
                    onTap = { offset ->
                        val cellW = size.width.toFloat() / cols
                        val cellH = size.height.toFloat() / rows
                        val col = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
                        val row = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                        onCellTapped(Position(row, col))
                    }
                )
            }
    ) {
        val cellW = size.width / cols
        val cellH = size.height / rows

        drawBackground(rows, cols, cellW, cellH)
        drawInitialPositions(gameState.levelData.seals, cellW, cellH)
        drawDestinations(gameState.levelData.destinations, cellW, cellH)
        drawGridLines(rows, cols, cellW, cellH)
        drawWalls(wallSet, rows, cols, cellW, cellH)

        if (gameState.selectedSealId != null) {
            val seal = gameState.seals.first { it.id == gameState.selectedSealId }
            drawSelectedHighlight(seal.position, cellW, cellH)
            drawArrows(seal.position, gameState.validMoves, cellW, cellH, arrowScales)
        }

        drawSeals(gameState.seals, cellW, cellH)
    }
}

private fun hitTestArrowDirection(
    sealPos: Position,
    validMoves: Map<Direction, Position>,
    tapped: Position
): Direction? {
    for ((dir, _) in validMoves) {
        val arrowCell = when (dir) {
            Direction.UP -> Position(sealPos.row - 1, sealPos.col)
            Direction.DOWN -> Position(sealPos.row + 1, sealPos.col)
            Direction.LEFT -> Position(sealPos.row, sealPos.col - 1)
            Direction.RIGHT -> Position(sealPos.row, sealPos.col + 1)
        }
        if (tapped == arrowCell) return dir
    }
    return null
}

private fun DrawScope.drawBackground(rows: Int, cols: Int, cellW: Float, cellH: Float) {
    drawRect(BoardBackground, size = size)
}

private fun DrawScope.drawGridLines(rows: Int, cols: Int, cellW: Float, cellH: Float) {
    for (i in 0..cols) {
        drawLine(GridLine, Offset(i * cellW, 0f), Offset(i * cellW, rows * cellH), strokeWidth = 1f)
    }
    for (i in 0..rows) {
        drawLine(GridLine, Offset(0f, i * cellH), Offset(cols * cellW, i * cellH), strokeWidth = 1f)
    }
}

private fun DrawScope.drawInitialPositions(seals: List<Seal>, cellW: Float, cellH: Float) {
    for (seal in seals) {
        val x = seal.position.col * cellW
        val y = seal.position.row * cellH
        drawRect(
            color = seal.color.composeColor.copy(alpha = 0.15f),
            topLeft = Offset(x + 2, y + 2),
            size = Size(cellW - 4, cellH - 4)
        )
    }
}

private fun DrawScope.drawDestinations(destinations: List<Destination>, cellW: Float, cellH: Float) {
    for (dest in destinations) {
        val cx = dest.position.col * cellW + cellW / 2
        val cy = dest.position.row * cellH + cellH / 2
        val starSize = min(cellW, cellH) * 0.35f
        drawStar(cx, cy, starSize, dest.color.composeColor.copy(alpha = 0.5f))
    }
}

private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = Path()
    val innerRadius = radius * 0.4f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = Math.toRadians((i * 36.0 - 90.0))
        val x = cx + r * cos(angle).toFloat()
        val y = cy + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
    drawPath(path, color.copy(alpha = 0.8f), style = Stroke(width = 2f))
}

private fun DrawScope.drawWalls(wallSet: Set<Wall>, rows: Int, cols: Int, cellW: Float, cellH: Float) {
    val wallWidth = 4f
    val drawn = mutableSetOf<Pair<Offset, Offset>>()

    for (wall in wallSet) {
        val r = wall.position.row
        val c = wall.position.col
        if (r < 0 || r >= rows || c < 0 || c >= cols) continue

        val (start, end) = when (wall.side) {
            Direction.UP -> Offset(c * cellW, r * cellH) to Offset((c + 1) * cellW, r * cellH)
            Direction.DOWN -> Offset(c * cellW, (r + 1) * cellH) to Offset((c + 1) * cellW, (r + 1) * cellH)
            Direction.LEFT -> Offset(c * cellW, r * cellH) to Offset(c * cellW, (r + 1) * cellH)
            Direction.RIGHT -> Offset((c + 1) * cellW, r * cellH) to Offset((c + 1) * cellW, (r + 1) * cellH)
        }

        val key = if (start.x < end.x || (start.x == end.x && start.y < end.y)) start to end else end to start
        if (key in drawn) continue
        drawn.add(key)

        drawLine(WallColor, start, end, strokeWidth = wallWidth)
    }
}

private fun DrawScope.drawSelectedHighlight(position: Position, cellW: Float, cellH: Float) {
    val x = position.col * cellW
    val y = position.row * cellH
    drawRect(SelectedHighlight, Offset(x, y), Size(cellW, cellH))
}

private fun DrawScope.drawSeals(seals: List<Seal>, cellW: Float, cellH: Float) {
    for (seal in seals) {
        val cx = seal.position.col * cellW + cellW / 2
        val cy = seal.position.row * cellH + cellH / 2
        val radius = min(cellW, cellH) * 0.35f
        drawCircle(Color.White, radius + 3f, Offset(cx, cy))
        drawCircle(seal.color.composeColor, radius, Offset(cx, cy))
    }
}

private fun DrawScope.drawArrows(
    sealPos: Position,
    validMoves: Map<Direction, Position>,
    cellW: Float,
    cellH: Float,
    scales: Map<Direction, Float>
) {
    for ((dir, _) in validMoves) {
        val scale = scales[dir] ?: 1.0f
        val arrowSize = min(cellW, cellH) * 0.22f * scale

        val ax = when (dir) {
            Direction.LEFT -> (sealPos.col - 1) * cellW + cellW / 2
            Direction.RIGHT -> (sealPos.col + 1) * cellW + cellW / 2
            else -> sealPos.col * cellW + cellW / 2
        }
        val ay = when (dir) {
            Direction.UP -> (sealPos.row - 1) * cellH + cellH / 2
            Direction.DOWN -> (sealPos.row + 1) * cellH + cellH / 2
            else -> sealPos.row * cellH + cellH / 2
        }

        val path = Path()
        when (dir) {
            Direction.UP -> {
                path.moveTo(ax, ay - arrowSize)
                path.lineTo(ax - arrowSize, ay + arrowSize * 0.5f)
                path.lineTo(ax + arrowSize, ay + arrowSize * 0.5f)
            }
            Direction.DOWN -> {
                path.moveTo(ax, ay + arrowSize)
                path.lineTo(ax - arrowSize, ay - arrowSize * 0.5f)
                path.lineTo(ax + arrowSize, ay - arrowSize * 0.5f)
            }
            Direction.LEFT -> {
                path.moveTo(ax - arrowSize, ay)
                path.lineTo(ax + arrowSize * 0.5f, ay - arrowSize)
                path.lineTo(ax + arrowSize * 0.5f, ay + arrowSize)
            }
            Direction.RIGHT -> {
                path.moveTo(ax + arrowSize, ay)
                path.lineTo(ax - arrowSize * 0.5f, ay - arrowSize)
                path.lineTo(ax - arrowSize * 0.5f, ay + arrowSize)
            }
        }
        path.close()
        drawPath(path, ArrowColor, style = Fill)
    }
}
