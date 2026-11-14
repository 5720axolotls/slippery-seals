package com.calebnoble.slipperyseals.ui.navigation

import com.calebnoble.slipperyseals.model.LevelSet

sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")

    data object LevelSetScreen : NavRoute("level_set/{setName}") {
        fun createRoute(levelSet: LevelSet) = "level_set/${levelSet.name}"
    }

    data object Game : NavRoute("game/{setName}/{levelIndex}") {
        fun createRoute(levelSet: LevelSet, index: Int) = "game/${levelSet.name}/$index"
    }
}
