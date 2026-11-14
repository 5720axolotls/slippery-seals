package com.calebnoble.slipperyseals.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebnoble.slipperyseals.data.LevelRepository
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.ui.components.LevelSetCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLevelSetSelected: (LevelSet) -> Unit,
    levelRepository: LevelRepository,
    progressManager: ProgressManager
) {
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (levelSet in LevelSet.entries) {
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
        }
    }
}
