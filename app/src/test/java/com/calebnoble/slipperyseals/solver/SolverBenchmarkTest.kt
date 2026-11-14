package com.calebnoble.slipperyseals.solver

import com.calebnoble.slipperyseals.model.*
import org.junit.Assert.*
import org.junit.Test

class SolverBenchmarkTest {

    private val solvers = listOf(
        AStarSolver("BFS (h=0)", Heuristics.zero()),
        AStarSolver("Unmatched Count", Heuristics.unmatchedCount()),
        AStarSolver("Min Axis Moves", Heuristics.minAxisMoves()),
        AStarSolver("Manhattan Scaled", Heuristics.manhattanScaled()),
        AStarSolver("Combined (max)", Heuristics.combined(Heuristics.minAxisMoves(), Heuristics.unmatchedCount())),
        AStarSolver("Weighted MinAxis x2", Heuristics.weighted(Heuristics.minAxisMoves(), 2.0)),
    )

    private val levels = listOf(
        level(
            id = "tutorial_1move", rows = 3, cols = 3,
            seals = listOf(seal("s1", SealColor.RED, 1, 0)),
            destinations = listOf(dest(SealColor.RED, 2, 2)),
            expectedMoves = 2
        ),
        level(
            id = "tutorial_walls", rows = 3, cols = 3,
            walls = listOf(wall(0, 0, Direction.RIGHT), wall(1, 1, Direction.DOWN)),
            seals = listOf(seal("s1", SealColor.RED, 0, 0)),
            destinations = listOf(dest(SealColor.RED, 1, 1)),
            expectedMoves = 5
        ),
        level(
            id = "tutorial_blocker", rows = 3, cols = 3,
            walls = listOf(wall(1, 1, Direction.DOWN)),
            seals = listOf(seal("s1", SealColor.RED, 0, 0), seal("s2", SealColor.BLUE, 2, 2)),
            destinations = listOf(dest(SealColor.RED, 1, 1)),
            expectedMoves = 3
        ),
        level(
            id = "easy_5x5", rows = 5, cols = 5,
            walls = listOf(wall(2, 2, Direction.DOWN), wall(1, 4, Direction.LEFT)),
            seals = listOf(seal("s1", SealColor.RED, 0, 0), seal("s2", SealColor.BLUE, 4, 4)),
            destinations = listOf(dest(SealColor.RED, 2, 2)),
            expectedMoves = 2
        ),
        level(
            id = "medium_7x7", rows = 7, cols = 7,
            walls = listOf(
                wall(1, 3, Direction.DOWN), wall(3, 5, Direction.LEFT),
                wall(5, 2, Direction.RIGHT), wall(4, 0, Direction.DOWN)
            ),
            seals = listOf(
                seal("s1", SealColor.RED, 0, 0),
                seal("s2", SealColor.BLUE, 6, 6),
                seal("s3", SealColor.GREEN, 0, 6)
            ),
            destinations = listOf(dest(SealColor.RED, 1, 3), dest(SealColor.BLUE, 3, 5)),
            expectedMoves = 4
        ),
        level(
            id = "hard_8x8", rows = 8, cols = 8,
            walls = listOf(
                wall(1, 3, Direction.DOWN), wall(3, 6, Direction.LEFT),
                wall(5, 2, Direction.RIGHT), wall(6, 4, Direction.UP),
                wall(2, 1, Direction.RIGHT), wall(4, 5, Direction.DOWN)
            ),
            seals = listOf(
                seal("s1", SealColor.RED, 0, 0),
                seal("s2", SealColor.BLUE, 7, 7),
                seal("s3", SealColor.GREEN, 0, 7),
                seal("s4", SealColor.YELLOW, 7, 0)
            ),
            destinations = listOf(dest(SealColor.RED, 1, 3), dest(SealColor.BLUE, 6, 4)),
            expectedMoves = 5
        ),
        level(
            id = "multi_dest_6x6", rows = 6, cols = 6,
            walls = listOf(
                wall(2, 3, Direction.DOWN), wall(4, 1, Direction.RIGHT),
                wall(1, 5, Direction.DOWN)
            ),
            seals = listOf(
                seal("s1", SealColor.RED, 0, 0),
                seal("s2", SealColor.BLUE, 5, 5),
                seal("s3", SealColor.GREEN, 0, 5)
            ),
            destinations = listOf(dest(SealColor.RED, 2, 3), dest(SealColor.GREEN, 1, 5)),
            expectedMoves = 3
        ),
    )

    @Test
    fun benchmarkAllSolvers() {
        val colName = 22
        val colMoves = 7
        val colNodes = 10
        val colTime = 8

        println()
        for (level in levels) {
            println("=== ${level.id} (${level.rows}x${level.cols}, ${level.seals.size} seals, ${level.destinations.size} dests) ===")
            println(
                "  ${"Heuristic".padEnd(colName)} ${"Moves".padStart(colMoves)} ${"Nodes".padStart(colNodes)} ${"Time".padStart(colTime)}"
            )
            println("  ${"-".repeat(colName + colMoves + colNodes + colTime + 3)}")

            for (solver in solvers) {
                val result = solver.solve(level)
                val movesStr = if (result == null) "FAIL" else "${result.moves.size}"
                val nodesStr = if (result == null) "-" else "${result.nodesExpanded}"
                val timeStr = if (result == null) "-" else "${result.timeMs}ms"
                println(
                    "  ${solver.name.padEnd(colName)} ${movesStr.padStart(colMoves)} ${nodesStr.padStart(colNodes)} ${timeStr.padStart(colTime)}"
                )
            }
            println()
        }
    }

    @Test
    fun allSolversProduceOptimalOnAdmissibleHeuristics() {
        val admissibleSolvers = solvers.filter { !it.name.contains("Weighted") }

        for (level in levels) {
            for (solver in admissibleSolvers) {
                val result = solver.solve(level)
                assertNotNull("${solver.name} failed on ${level.id}", result)
                assertEquals(
                    "${solver.name} non-optimal on ${level.id}: got ${result!!.moves.size}, expected ${level.optimalMoves}",
                    level.optimalMoves,
                    result.moves.size
                )
            }
        }
    }

    companion object {
        fun level(
            id: String,
            rows: Int,
            cols: Int,
            walls: List<Wall> = emptyList(),
            seals: List<Seal>,
            destinations: List<Destination>,
            expectedMoves: Int
        ) = LevelData(
            id = id, rows = rows, cols = cols,
            walls = walls, seals = seals, destinations = destinations,
            optimalMoves = expectedMoves, optimalSolution = emptyList()
        )

        fun seal(id: String, color: SealColor, row: Int, col: Int) =
            Seal(id, color, Position(row, col))

        fun dest(color: SealColor, row: Int, col: Int) =
            Destination(color, Position(row, col))

        fun wall(row: Int, col: Int, side: Direction) =
            Wall(Position(row, col), side)
    }
}
