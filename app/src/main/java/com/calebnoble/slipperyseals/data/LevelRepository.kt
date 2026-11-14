package com.calebnoble.slipperyseals.data

import android.content.Context
import com.calebnoble.slipperyseals.model.LevelData
import com.calebnoble.slipperyseals.model.LevelSet
import com.calebnoble.slipperyseals.util.JsonParser

class LevelRepository(private val context: Context) {

    private val cache = mutableMapOf<LevelSet, List<LevelData>>()

    fun getLevels(levelSet: LevelSet): List<LevelData> {
        return cache.getOrPut(levelSet) {
            val json = context.assets.open("levels/${levelSet.fileName}")
                .bufferedReader().use { it.readText() }
            JsonParser.parseLevelSet(json)
        }
    }

    fun getLevel(levelSet: LevelSet, levelIndex: Int): LevelData =
        getLevels(levelSet)[levelIndex]
}
