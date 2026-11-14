package com.calebnoble.slipperyseals.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calebnoble.slipperyseals.data.LevelRepository
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.engine.GameEngine
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.ui.components.GameBoard
import com.calebnoble.slipperyseals.ui.components.GameTopBar
import com.calebnoble.slipperyseals.ui.theme.StarGold
import com.calebnoble.slipperyseals.ui.viewmodel.GameViewModel
import com.calebnoble.slipperyseals.ui.viewmodel.GameViewModelFactory

@Composable
fun GameScreen(
    levelSet: LevelSet,
    levelIndex: Int,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGoToLevels: () -> Unit,
    onGoHome: () -> Unit,
    levelRepository: LevelRepository,
    progressManager: ProgressManager
) {
    val levelData = remember(levelSet, levelIndex) {
        levelRepository.getLevel(levelSet, levelIndex)
    }

    val viewModel: GameViewModel = viewModel(
        key = levelData.id,
        factory = GameViewModelFactory(levelData, progressManager)
    )

    val gameState = viewModel.gameState
    val wallSet = remember(levelData) { GameEngine.buildWallSet(levelData) }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        GameTopBar(
            moveCount = gameState.moveCount,
            optimalMoves = levelData.optimalMoves,
            canUndo = gameState.history.isNotEmpty(),
            canHint = !gameState.isSolved,
            isHintLoading = viewModel.isHintLoading,
            onUndo = viewModel::onUndo,
            onReset = viewModel::onReset,
            onHint = viewModel::onHint,
            onBack = onBack,
            onMenu = { showMenu = true }
        )

        viewModel.hintMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(onClick = viewModel::dismissHint) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (levelData.instruction != null) {
            Text(
                text = levelData.instruction,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            GameBoard(
                gameState = gameState,
                wallSet = wallSet,
                onCellTapped = viewModel::onCellTapped
            )

            if (gameState.isSolved) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isOptimal = gameState.moveCount <= levelData.optimalMoves
                        Text(
                            if (isOptimal) "Perfect!" else "Level Complete!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOptimal) StarGold else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Solved in ${gameState.moveCount} moves (optimal: ${levelData.optimalMoves})",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = viewModel::onReset) {
                                Text("Try Again")
                            }
                            Button(onClick = onNextLevel) {
                                Text("Next Level")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Menu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showMenu = false; onGoToLevels() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Levels")
                    }
                    TextButton(
                        onClick = { showMenu = false; onGoHome() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Home")
                    }
                    TextButton(
                        onClick = { showMenu = false; viewModel.onHint() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !gameState.isSolved
                    ) {
                        Text("Show Hint")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}
