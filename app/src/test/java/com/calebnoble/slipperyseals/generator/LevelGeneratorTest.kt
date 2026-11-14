package com.calebnoble.slipperyseals.generator

import com.calebnoble.slipperyseals.solver.AStarSolver
import com.calebnoble.slipperyseals.solver.Heuristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LevelGeneratorTest {

    private fun verifier() = AStarSolver(
        "verify",
        Heuristics.combined(Heuristics.minAxisMoves(), Heuristics.unmatchedCount())
    )

    @Test
    fun generatesSolvableLevelsWithinRangeForEachPreset() {
        val presets = mapOf(
            "easy" to LevelGenerator.EASY,
            "medium" to LevelGenerator.MEDIUM,
            "hard" to LevelGenerator.HARD,
        )
        for ((name, config) in presets) {
            val level = LevelGenerator.generateVerified(config, Random(1234), "test_$name")
            assertNotNull("generator returned null for $name", level)
            level!!

            assertEquals(config.nSeals, level.seals.size)
            assertEquals(config.nDestinations, level.destinations.size)
            assertEquals(config.rows, level.rows)
            assertEquals(config.cols, level.cols)
            assertTrue(
                "$name optimalMoves ${level.optimalMoves} outside ${config.minMoves}..${config.maxMoves}",
                level.optimalMoves in config.minMoves..config.maxMoves
            )

            // The goal must not already be satisfied at the start.
            val startSatisfied = level.destinations.any { dest ->
                level.seals.any { it.color == dest.color && it.position == dest.position }
            }
            assertTrue("$name starts already solved", !startSatisfied)

            // Independently confirm the baked-in optimal length is correct.
            val result = verifier().solve(level)
            assertNotNull("$name not solvable", result)
            assertEquals(level.optimalMoves, result!!.moves.size)
        }
    }
}
