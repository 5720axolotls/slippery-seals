package com.calebnoble.slipperyseals.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.calebnoble.slipperyseals.data.ProgressManager
import com.calebnoble.slipperyseals.model.LevelData

class GameViewModelFactory(
    private val levelData: LevelData,
    private val progressManager: ProgressManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GameViewModel(levelData, progressManager) as T
    }
}
