package com.calebnoble.slipperyseals.model

data class Move(val sealId: String, val direction: Direction)

data class LevelData(
    val id: String,
    val rows: Int,
    val cols: Int,
    val walls: List<Wall>,
    val seals: List<Seal>,
    val destinations: List<Destination>,
    val optimalMoves: Int,
    val optimalSolution: List<Move>,
    val instruction: String? = null
)
