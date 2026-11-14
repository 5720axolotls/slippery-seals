package com.calebnoble.slipperyseals.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.engine.GameEngine
import com.calebnoble.slipperyseals.model.*

class GameViewModel(
    private val levelData: LevelData,
    private val progressManager: ProgressManager
) : ViewModel() {

    private val wallSet: Set<Wall> = GameEngine.buildWallSet(levelData)

    var gameState by mutableStateOf(
        GameState(levelData = levelData, seals = levelData.seals)
    )
        private set

    fun onCellTapped(position: Position) {
        val state = gameState
        if (state.isSolved) return

        if (state.selectedSealId != null) {
            val seal = state.seals.first { it.id == state.selectedSealId }
            val matchedDir = matchArrowTap(seal.position, state.validMoves, position)
            if (matchedDir != null) {
                executeMove(state.selectedSealId, matchedDir)
                return
            }
        }

        val tappedSeal = state.seals.find { it.position == position }
        if (tappedSeal != null) {
            selectSeal(tappedSeal.id)
        } else {
            deselectSeal()
        }
    }

    private fun matchArrowTap(
        sealPos: Position,
        validMoves: Map<Direction, Position>,
        tapped: Position
    ): Direction? {
        for ((dir, _) in validMoves) {
            val arrowPos = when (dir) {
                Direction.UP -> Position(sealPos.row - 1, sealPos.col)
                Direction.DOWN -> Position(sealPos.row + 1, sealPos.col)
                Direction.LEFT -> Position(sealPos.row, sealPos.col - 1)
                Direction.RIGHT -> Position(sealPos.row, sealPos.col + 1)
            }
            if (tapped == arrowPos || tapped == sealPos) {
                if (tapped == sealPos) continue
                return dir
            }
        }
        return null
    }

    private fun selectSeal(sealId: String) {
        val moves = GameEngine.validMovesForSeal(sealId, gameState.seals, wallSet)
        gameState = gameState.copy(selectedSealId = sealId, validMoves = moves)
    }

    private fun deselectSeal() {
        gameState = gameState.copy(selectedSealId = null, validMoves = emptyMap())
    }

    private fun executeMove(sealId: String, direction: Direction) {
        val snapshot = gameState.withSnapshot()
        val newSeals = GameEngine.executeMove(sealId, direction, snapshot.seals, wallSet)
        val solved = GameEngine.isSolved(newSeals, snapshot.levelData.destinations)

        gameState = snapshot.copy(
            seals = newSeals,
            moveCount = snapshot.moveCount + 1,
            selectedSealId = null,
            validMoves = emptyMap(),
            isSolved = solved
        )

        if (solved) {
            progressManager.recordCompletion(
                levelData.id,
                gameState.moveCount,
                levelData.optimalMoves
            )
        }
    }

    fun onUndo() {
        gameState.undo()?.let { gameState = it }
    }

    fun onReset() {
        gameState = GameState(levelData = levelData, seals = levelData.seals)
    }
}
