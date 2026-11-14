package com.calebnoble.slipperyseals.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calebnoble.slipperyseals.data.LevelRepository
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.ui.components.LevelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSetScreen(
    levelSet: LevelSet,
    onLevelSelected: (Int) -> Unit,
    onBack: () -> Unit,
    levelRepository: LevelRepository,
    progressManager: ProgressManager
) {
    val levels = remember(levelSet) { levelRepository.getLevels(levelSet) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(levelSet.displayName) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("<  Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(levels) { index, level ->
                val completion = remember(level.id) { progressManager.getCompletion(level.id) }
                val bestMoves = remember(level.id) { progressManager.getBestMoveCount(level.id) }
                LevelCard(
                    levelNumber = index + 1,
                    completion = completion,
                    bestMoves = bestMoves,
                    optimalMoves = level.optimalMoves,
                    onClick = { onLevelSelected(index) }
                )
            }
        }
    }
}
