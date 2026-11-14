package com.calebnoble.slipperyseals.solver

fun interface Heuristic {
    fun estimate(state: SolverState, context: SolverContext): Int
}
