package com.calebnoble.slipperyseals.model

data class GameState(
    val levelData: LevelData,
    val seals: List<Seal>,
    val moveCount: Int = 0,
    val selectedSealId: String? = null,
    val validMoves: Map<Direction, Position> = emptyMap(),
    val isSolved: Boolean = false,
    val history: List<List<Seal>> = emptyList()
) {
    fun withSnapshot(): GameState = copy(history = history + listOf(seals))

    fun undo(): GameState? {
        if (history.isEmpty()) return null
        return copy(
            seals = history.last(),
            history = history.dropLast(1),
            moveCount = moveCount - 1,
            selectedSealId = null,
            validMoves = emptyMap(),
            isSolved = false
        )
    }
}
