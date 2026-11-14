package com.calebnoble.slipperyseals.solver

import com.calebnoble.slipperyseals.model.Destination
import com.calebnoble.slipperyseals.model.Position
import com.calebnoble.slipperyseals.model.SealColor
import kotlin.math.abs

object Heuristics {

    fun zero(): Heuristic = Heuristic { _, _ -> 0 }

    fun unmatchedCount(): Heuristic = Heuristic { state, ctx ->
        countUnmatched(state, ctx)
    }

    fun minAxisMoves(): Heuristic = Heuristic { state, ctx ->
        ctx.destinations.sumOf { dest ->
            bestSealCost(state, ctx, dest) { pos, destPos ->
                when {
                    pos == destPos -> 0
                    pos.row == destPos.row || pos.col == destPos.col -> 1
                    else -> 2
                }
            }
        }
    }

    fun manhattanScaled(): Heuristic = Heuristic { state, ctx ->
        val maxDim = maxOf(ctx.rows, ctx.cols)
        ctx.destinations.sumOf { dest ->
            bestSealCost(state, ctx, dest) { pos, destPos ->
                val dist = abs(pos.row - destPos.row) + abs(pos.col - destPos.col)
                when {
                    pos == destPos -> 0
                    dist <= maxDim -> 1
                    else -> 2
                }
            }
        }
    }

    fun weighted(base: Heuristic, weight: Double): Heuristic = Heuristic { state, ctx ->
        (base.estimate(state, ctx) * weight).toInt()
    }

    fun combined(vararg heuristics: Heuristic): Heuristic = Heuristic { state, ctx ->
        heuristics.maxOf { it.estimate(state, ctx) }
    }

    private fun countUnmatched(state: SolverState, ctx: SolverContext): Int {
        return ctx.destinations.count { dest ->
            state.positions.none { (id, pos) ->
                ctx.sealColors[id] == dest.color && pos == dest.position
            }
        }
    }

    private inline fun bestSealCost(
        state: SolverState,
        ctx: SolverContext,
        dest: Destination,
        cost: (Position, Position) -> Int
    ): Int {
        var best = Int.MAX_VALUE
        for ((id, pos) in state.positions) {
            if (ctx.sealColors[id] != dest.color) continue
            val c = cost(pos, dest.position)
            if (c < best) best = c
        }
        return if (best == Int.MAX_VALUE) 2 else best
    }
}
