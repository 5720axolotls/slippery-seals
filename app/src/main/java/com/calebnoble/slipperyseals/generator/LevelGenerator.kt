package com.calebnoble.slipperyseals.generator

import com.calebnoble.slipperyseals.model.*
import com.calebnoble.slipperyseals.solver.AStarSolver
import com.calebnoble.slipperyseals.solver.Heuristics
import kotlin.random.Random

data class GeneratorConfig(
    val rows: Int,
    val cols: Int,
    val nSeals: Int,
    val nWalls: Int,
    val minMoves: Int,
    val maxMoves: Int,
    val nDestinations: Int = 1,
    val maxAttempts: Int = 200,
    val maxSolverNodes: Int = 150_000,
)

/**
 * On-device level generation. Mirrors the offline Python generator: place seals,
 * a goal, and internal walls at random, then keep the board only if the in-app A*
 * solver finds an optimal solution within the configured move range.
 */
object LevelGenerator {

    private val COLORS = listOf(
        SealColor.RED, SealColor.BLUE, SealColor.GREEN,
        SealColor.YELLOW, SealColor.PURPLE, SealColor.ORANGE,
    )
    private val SIDES = Direction.entries

    // Difficulty presets tuned to keep the pure-Kotlin solver fast on-device
    // (single goal, like the original game).
    val EASY = GeneratorConfig(rows = 5, cols = 5, nSeals = 2, nWalls = 3, minMoves = 2, maxMoves = 4)
    val MEDIUM = GeneratorConfig(rows = 7, cols = 7, nSeals = 3, nWalls = 5, minMoves = 4, maxMoves = 7)
    val HARD = GeneratorConfig(rows = 8, cols = 8, nSeals = 4, nWalls = 6, minMoves = 6, maxMoves = 9)

    /**
     * Returns a solvable level whose optimal solution length falls within the
     * config's move range, or null if none is found within maxAttempts.
     * This is CPU-bound — call it off the main thread.
     */
    fun generateVerified(config: GeneratorConfig, random: Random, id: String): LevelData? {
        val solver = AStarSolver(
            name = "gen",
            heuristic = Heuristics.combined(Heuristics.minAxisMoves(), Heuristics.unmatchedCount()),
            maxNodes = config.maxSolverNodes,
        )
        repeat(config.maxAttempts) {
            val candidate = buildCandidate(config, random) ?: return@repeat
            val moves = solver.solve(candidate)?.moves ?: return@repeat
            // An empty path means the solver hit its node cap before finding a goal.
            if (moves.isEmpty() || moves.size < config.minMoves || moves.size > config.maxMoves) {
                return@repeat
            }
            return candidate.copy(id = id, optimalMoves = moves.size, optimalSolution = moves)
        }
        return null
    }

    private fun buildCandidate(config: GeneratorConfig, random: Random): LevelData? {
        val rows = config.rows
        val cols = config.cols
        val allCells = buildList {
            for (r in 0 until rows) for (c in 0 until cols) add(Position(r, c))
        }

        val picked = allCells.shuffled(random).take(config.nSeals + config.nDestinations)
        val sealCells = picked.take(config.nSeals)
        val destCells = picked.drop(config.nSeals)

        val colors = COLORS.take(config.nSeals)
        val seals = sealCells.mapIndexed { i, pos -> Seal("s${i + 1}", colors[i], pos) }

        val destColors = colors.shuffled(random).take(config.nDestinations)
        val destinations = destColors.zip(destCells).map { (color, pos) -> Destination(color, pos) }

        // Reject boards where a goal is already satisfied at the start.
        val startPlacements = seals.map { it.color to it.position }.toSet()
        if (destinations.any { (it.color to it.position) in startPlacements }) return null

        val walls = mutableListOf<Wall>()
        val usedEdges = mutableSetOf<Triple<Int, Int, Direction>>()
        repeat(config.nWalls) {
            for (attempt in 0 until 20) {
                val r = random.nextInt(rows)
                val c = random.nextInt(cols)
                val side = SIDES[random.nextInt(SIDES.size)]
                val delta = side.delta()
                val nr = r + delta.row
                val nc = c + delta.col
                // Skip perimeter edges (neighbor off-board) so walls stay internal.
                if (nr !in 0 until rows || nc !in 0 until cols) continue
                if (Triple(r, c, side) in usedEdges) continue
                walls.add(Wall(Position(r, c), side))
                usedEdges.add(Triple(r, c, side))
                usedEdges.add(Triple(nr, nc, side.opposite()))
                break
            }
        }

        return LevelData(
            id = "candidate",
            rows = rows,
            cols = cols,
            walls = walls,
            seals = seals,
            destinations = destinations,
            optimalMoves = 0,
            optimalSolution = emptyList(),
        )
    }
}
