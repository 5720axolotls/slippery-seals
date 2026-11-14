package com.calebnoble.slipperyseals

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calebnoble.slipperyseals.data.LevelRepository
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.ui.navigation.NavRoute
import com.calebnoble.slipperyseals.ui.screens.GameScreen
import com.calebnoble.slipperyseals.ui.screens.HomeScreen
import com.calebnoble.slipperyseals.ui.screens.LevelSetScreen

@Composable
fun SlipperySealsApp(
    levelRepository: LevelRepository,
    progressManager: ProgressManager
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = NavRoute.Home.route) {
        composable(NavRoute.Home.route) {
            HomeScreen(
                onLevelSetSelected = { set ->
                    navController.navigate(NavRoute.LevelSetScreen.createRoute(set))
                },
                levelRepository = levelRepository,
                progressManager = progressManager
            )
        }

        composable(
            NavRoute.LevelSetScreen.route,
            arguments = listOf(navArgument("setName") { type = NavType.StringType })
        ) { backStackEntry ->
            val setName = backStackEntry.arguments?.getString("setName") ?: return@composable
            val levelSet = LevelSet.valueOf(setName)
            LevelSetScreen(
                levelSet = levelSet,
                onLevelSelected = { index ->
                    navController.navigate(NavRoute.Game.createRoute(levelSet, index))
                },
                onBack = { navController.popBackStack() },
                levelRepository = levelRepository,
                progressManager = progressManager
            )
        }

        composable(
            NavRoute.Game.route,
            arguments = listOf(
                navArgument("setName") { type = NavType.StringType },
                navArgument("levelIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val setName = backStackEntry.arguments?.getString("setName") ?: return@composable
            val levelIndex = backStackEntry.arguments?.getInt("levelIndex") ?: return@composable
            val levelSet = LevelSet.valueOf(setName)
            val levelCount = levelRepository.getLevels(levelSet).size
            val sets = LevelSet.entries
            val setIndex = sets.indexOf(levelSet)
            val nextSet = sets.getOrNull(setIndex + 1)

            val onNextLevel: () -> Unit = when {
                levelIndex + 1 < levelCount -> {
                    {
                        navController.popBackStack()
                        navController.navigate(NavRoute.Game.createRoute(levelSet, levelIndex + 1))
                    }
                }
                nextSet != null -> {
                    {
                        navController.popBackStack()
                        navController.navigate(NavRoute.Game.createRoute(nextSet, 0))
                    }
                }
                else -> {
                    { navController.popBackStack() }
                }
            }

            GameScreen(
                levelSet = levelSet,
                levelIndex = levelIndex,
                onBack = { navController.popBackStack() },
                onNextLevel = onNextLevel,
                onGoToLevels = {
                    navController.popBackStack(NavRoute.LevelSetScreen.route, false)
                },
                onGoHome = {
                    navController.popBackStack(NavRoute.Home.route, false)
                },
                levelRepository = levelRepository,
                progressManager = progressManager
            )
        }
    }
}
