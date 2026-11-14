package com.calebnoble.slipperyseals.solver

import com.calebnoble.slipperyseals.engine.GameEngine
import com.calebnoble.slipperyseals.model.*
import java.util.PriorityQueue

class AStarSolver(
    val name: String,
    private val heuristic: Heuristic,
    private val maxNodes: Int = 1_000_000
) {
    private data class Node(val fScore: Int, val gScore: Int, val state: SolverState)

    fun solve(level: LevelData): SolverResult? {
        val startTime = System.nanoTime()
        val walls = GameEngine.buildWallSet(level)
        val sealColors = level.seals.associate { it.id to it.color }
        val sealIds = level.seals.map { it.id }
        val context = SolverContext(level.destinations, walls, level.rows, level.cols, sealColors, sealIds)

        val initial = SolverState(level.seals.associate { it.id to it.position })
        val h0 = heuristic.estimate(initial, context)

        val openSet = PriorityQueue<Node>(compareBy { it.fScore })
        openSet.add(Node(h0, 0, initial))

        val bestG = mutableMapOf(initial to 0)
        val parent = mutableMapOf<SolverState, Pair<SolverState, Move>>()
        val closed = mutableSetOf<SolverState>()
        var nodesExpanded = 0

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            if (current.state in closed) continue
            closed.add(current.state)
            nodesExpanded++

            if (nodesExpanded > maxNodes) {
                val elapsed = (System.nanoTime() - startTime) / 1_000_000
                return SolverResult(emptyList(), nodesExpanded, elapsed)
            }

            if (isGoal(current.state, context)) {
                val elapsed = (System.nanoTime() - startTime) / 1_000_000
                return SolverResult(reconstructPath(parent, current.state), nodesExpanded, elapsed)
            }

            val sealsForEngine = current.state.positions.map { (id, pos) ->
                Seal(id, sealColors[id]!!, pos)
            }

            for (sealId in sealIds) {
                for (dir in Direction.entries) {
                    val dest = GameEngine.computeSlideDestination(sealId, dir, sealsForEngine, walls)
                        ?: continue

                    val newPositions = current.state.positions.toMutableMap()
                    newPositions[sealId] = dest
                    val neighbor = SolverState(newPositions)

                    if (neighbor in closed) continue

                    val tentativeG = current.gScore + 1
                    if (tentativeG < (bestG[neighbor] ?: Int.MAX_VALUE)) {
                        bestG[neighbor] = tentativeG
                        parent[neighbor] = current.state to Move(sealId, dir)
                        val f = tentativeG + heuristic.estimate(neighbor, context)
                        openSet.add(Node(f, tentativeG, neighbor))
                    }
                }
            }
        }

        val elapsed = (System.nanoTime() - startTime) / 1_000_000
        return null
    }

    private fun isGoal(state: SolverState, ctx: SolverContext): Boolean {
        return ctx.destinations.all { dest ->
            state.positions.any { (id, pos) ->
                ctx.sealColors[id] == dest.color && pos == dest.position
            }
        }
    }

    private fun reconstructPath(
        parent: Map<SolverState, Pair<SolverState, Move>>,
        goal: SolverState
    ): List<Move> {
        val moves = mutableListOf<Move>()
        var current = goal
        while (current in parent) {
            val (prev, move) = parent[current]!!
            moves.add(move)
            current = prev
        }
        return moves.reversed()
    }
}
