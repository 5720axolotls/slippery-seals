package com.calebnoble.slipperyseals.engine

import com.calebnoble.slipperyseals.model.*

object GameEngine {

    fun buildWallSet(levelData: LevelData): Set<Wall> {
        val walls = mutableSetOf<Wall>()
        for (wall in levelData.walls) {
            walls.add(wall)
            walls.add(mirrorWall(wall))
        }
        walls.addAll(perimeterWalls(levelData.rows, levelData.cols))
        return walls
    }

    private fun mirrorWall(wall: Wall): Wall {
        val delta = wall.side.delta()
        val neighborPos = Position(wall.position.row + delta.row, wall.position.col + delta.col)
        return Wall(neighborPos, wall.side.opposite())
    }

    fun perimeterWalls(rows: Int, cols: Int): Set<Wall> {
        val walls = mutableSetOf<Wall>()
        for (c in 0 until cols) {
            walls.add(Wall(Position(0, c), Direction.UP))
            walls.add(Wall(Position(rows - 1, c), Direction.DOWN))
        }
        for (r in 0 until rows) {
            walls.add(Wall(Position(r, 0), Direction.LEFT))
            walls.add(Wall(Position(r, cols - 1), Direction.RIGHT))
        }
        return walls
    }

    fun computeSlideDestination(
        sealId: String,
        direction: Direction,
        seals: List<Seal>,
        walls: Set<Wall>,
    ): Position? {
        val seal = seals.first { it.id == sealId }
        val otherPositions = seals.filter { it.id != sealId }.map { it.position }.toSet()
        val delta = direction.delta()

        var pos = seal.position
        while (true) {
            if (Wall(pos, direction) in walls) break
            val next = Position(pos.row + delta.row, pos.col + delta.col)
            if (next in otherPositions) break
            pos = next
        }

        return if (pos == seal.position) null else pos
    }

    fun validMovesForSeal(
        sealId: String,
        seals: List<Seal>,
        walls: Set<Wall>,
    ): Map<Direction, Position> {
        val moves = mutableMapOf<Direction, Position>()
        for (dir in Direction.entries) {
            val dest = computeSlideDestination(sealId, dir, seals, walls)
            if (dest != null) moves[dir] = dest
        }
        return moves
    }

    fun executeMove(
        sealId: String,
        direction: Direction,
        seals: List<Seal>,
        walls: Set<Wall>,
    ): List<Seal> {
        val dest = computeSlideDestination(sealId, direction, seals, walls) ?: return seals
        return seals.map { seal ->
            if (seal.id == sealId) seal.copy(position = dest) else seal
        }
    }

    fun isSolved(seals: List<Seal>, destinations: List<Destination>): Boolean {
        return destinations.all { dest ->
            seals.any { it.color == dest.color && it.position == dest.position }
        }
    }
}
