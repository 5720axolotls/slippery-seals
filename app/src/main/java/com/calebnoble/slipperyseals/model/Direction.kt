package com.calebnoble.slipperyseals.model

enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }

    fun delta(): Position = when (this) {
        UP -> Position(-1, 0)
        DOWN -> Position(1, 0)
        LEFT -> Position(0, -1)
        RIGHT -> Position(0, 1)
    }
}
