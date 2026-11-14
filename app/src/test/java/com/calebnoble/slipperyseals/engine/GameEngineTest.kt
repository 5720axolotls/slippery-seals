package com.calebnoble.slipperyseals.engine

import com.calebnoble.slipperyseals.model.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {

    private fun makeLevel(
        rows: Int = 5,
        cols: Int = 5,
        walls: List<Wall> = emptyList(),
        seals: List<Seal> = emptyList(),
        destinations: List<Destination> = emptyList()
    ): LevelData = LevelData(
        id = "test",
        rows = rows,
        cols = cols,
        walls = walls,
        seals = seals,
        destinations = destinations,
        optimalMoves = 1,
        optimalSolution = emptyList()
    )

    @Test
    fun `seal slides to edge when no obstacles`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(2, 2)))
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.RIGHT, seals, wallSet)
        assertEquals(Position(2, 4), dest)
    }

    @Test
    fun `seal slides to edge going left`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(2, 2)))
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.LEFT, seals, wallSet)
        assertEquals(Position(2, 0), dest)
    }

    @Test
    fun `seal slides to edge going up`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(2, 2)))
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.UP, seals, wallSet)
        assertEquals(Position(0, 2), dest)
    }

    @Test
    fun `seal slides to edge going down`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(2, 2)))
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.DOWN, seals, wallSet)
        assertEquals(Position(4, 2), dest)
    }

    @Test
    fun `seal stops at interior wall`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(2, 0)))
        val walls = listOf(Wall(Position(2, 2), Direction.LEFT))
        val level = makeLevel(walls = walls, seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.RIGHT, seals, wallSet)
        assertEquals(Position(2, 1), dest)
    }

    @Test
    fun `seal stops before another seal`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 0)),
            Seal("s2", SealColor.BLUE, Position(2, 3))
        )
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.RIGHT, seals, wallSet)
        assertEquals(Position(2, 2), dest)
    }

    @Test
    fun `seal blocked immediately returns null`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(0, 0))
        )
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.UP, seals, wallSet)
        assertNull(dest)
    }

    @Test
    fun `seal blocked by adjacent seal returns null`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 2)),
            Seal("s2", SealColor.BLUE, Position(2, 3))
        )
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.RIGHT, seals, wallSet)
        assertNull(dest)
    }

    @Test
    fun `isSolved returns true when all destinations matched`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 2)),
            Seal("s2", SealColor.BLUE, Position(0, 0))
        )
        val destinations = listOf(Destination(SealColor.RED, Position(2, 2)))

        assertTrue(GameEngine.isSolved(seals, destinations))
    }

    @Test
    fun `isSolved returns false when destination unmatched`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 2)),
            Seal("s2", SealColor.BLUE, Position(0, 0))
        )
        val destinations = listOf(Destination(SealColor.RED, Position(3, 3)))

        assertFalse(GameEngine.isSolved(seals, destinations))
    }

    @Test
    fun `isSolved requires color match`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 2)),
            Seal("s2", SealColor.BLUE, Position(3, 3))
        )
        val destinations = listOf(Destination(SealColor.BLUE, Position(2, 2)))

        assertFalse(GameEngine.isSolved(seals, destinations))
    }

    @Test
    fun `executeMove moves seal to correct position`() {
        val seals = listOf(
            Seal("s1", SealColor.RED, Position(2, 0)),
            Seal("s2", SealColor.BLUE, Position(2, 3))
        )
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val newSeals = GameEngine.executeMove("s1", Direction.RIGHT, seals, wallSet)
        assertEquals(Position(2, 2), newSeals.first { it.id == "s1" }.position)
        assertEquals(Position(2, 3), newSeals.first { it.id == "s2" }.position)
    }

    @Test
    fun `validMovesForSeal returns only unblocked directions`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(0, 0)))
        val level = makeLevel(seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val moves = GameEngine.validMovesForSeal("s1", seals, wallSet)
        assertEquals(2, moves.size)
        assertTrue(Direction.DOWN in moves)
        assertTrue(Direction.RIGHT in moves)
    }

    @Test
    fun `wall on south side blocks downward movement`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(1, 2)))
        val walls = listOf(Wall(Position(1, 2), Direction.DOWN))
        val level = makeLevel(walls = walls, seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.DOWN, seals, wallSet)
        assertNull(dest)
    }

    @Test
    fun `wall normalization works - south wall blocks from both sides`() {
        val seals = listOf(Seal("s1", SealColor.RED, Position(3, 2)))
        val walls = listOf(Wall(Position(2, 2), Direction.DOWN))
        val level = makeLevel(walls = walls, seals = seals)
        val wallSet = GameEngine.buildWallSet(level)

        val dest = GameEngine.computeSlideDestination("s1", Direction.UP, seals, wallSet)
        assertNull(dest)
    }
}
