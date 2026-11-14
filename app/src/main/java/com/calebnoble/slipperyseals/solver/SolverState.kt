package com.calebnoble.slipperyseals.solver

import com.calebnoble.slipperyseals.model.*

data class SolverState(val positions: Map<String, Position>)

data class SolverContext(
    val destinations: List<Destination>,
    val walls: Set<Wall>,
    val rows: Int,
    val cols: Int,
    val sealColors: Map<String, SealColor>,
    val sealIds: List<String>
)

data class SolverResult(
    val moves: List<Move>,
    val nodesExpanded: Int,
    val timeMs: Long
)
