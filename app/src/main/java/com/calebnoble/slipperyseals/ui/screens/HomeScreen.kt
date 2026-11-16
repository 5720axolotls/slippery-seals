package com.calebnoble.slipperyseals.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebnoble.slipperyseals.data.LevelRepository
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.generator.GeneratorConfig
import com.calebnoble.slipperyseals.generator.LevelGenerator
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.ui.components.LevelSetCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLevelSetSelected: (LevelSet) -> Unit,
    onPlayRandom: () -> Unit,
    levelRepository: LevelRepository,
    progressManager: ProgressManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Slippery Seals", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (levelSet in LevelSet.entries) {
                if (levelSet == LevelSet.RANDOM) continue

                val levels = remember(levelSet) { levelRepository.getLevels(levelSet) }
                val (total, completed, starred) = remember(levelSet) {
                    progressManager.getCompletionCounts(levels)
                }

                LevelSetCard(
                    name = levelSet.displayName,
                    total = total,
                    completed = completed,
                    starred = starred,
                    onClick = { onLevelSetSelected(levelSet) }
                )
            }

            Button(
                onClick = { showDifficultyDialog = true },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Generating…", fontSize = 18.sp)
                } else {
                    Text("Random Puzzle", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDifficultyDialog) {
        val presets = listOf(
            "Easy" to LevelGenerator.EASY,
            "Medium" to LevelGenerator.MEDIUM,
            "Hard" to LevelGenerator.HARD,
            "Huge" to LevelGenerator.HUGE,
        )
        AlertDialog(
            onDismissRequest = { showDifficultyDialog = false },
            title = { Text("Random Puzzle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, config) in presets) {
                        Button(
                            onClick = {
                                showDifficultyDialog = false
                                generate(
                                    config = config,
                                    scope = scope,
                                    levelRepository = levelRepository,
                                    onStart = { isGenerating = true },
                                    onDone = { isGenerating = false },
                                    onSuccess = onPlayRandom,
                                    onFailure = {
                                        Toast.makeText(
                                            context,
                                            "Couldn't generate a puzzle — try again",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$label  (${config.rows}×${config.cols}, ${config.nSeals} seals)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDifficultyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun generate(
    config: GeneratorConfig,
    scope: kotlinx.coroutines.CoroutineScope,
    levelRepository: LevelRepository,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    onStart()
    scope.launch {
        val id = "random_${System.currentTimeMillis()}"
        val level = withContext(Dispatchers.Default) {
            LevelGenerator.generateVerified(config, Random(System.currentTimeMillis()), id)
        }
        onDone()
        if (level != null) {
            levelRepository.setRandomLevel(level)
            onSuccess()
        } else {
            onFailure()
        }
    }
}
