package com.calebnoble.slipperyseals.data

import android.content.Context
import com.calebnoble.slipperyseals.model.LevelCompletion
import com.calebnoble.slipperyseals.model.LevelData

class ProgressManager(context: Context) {

    private val prefs = context.getSharedPreferences("slippery_seals_progress", Context.MODE_PRIVATE)

    fun getCompletion(levelId: String): LevelCompletion {
        val value = prefs.getString("level_$levelId", null) ?: return LevelCompletion.INCOMPLETE
        return LevelCompletion.valueOf(value)
    }

    fun getBestMoveCount(levelId: String): Int? {
        val value = prefs.getInt("moves_$levelId", -1)
        return if (value == -1) null else value
    }

    fun recordCompletion(levelId: String, moveCount: Int, optimalMoves: Int) {
        val isOptimal = moveCount <= optimalMoves
        val newCompletion = if (isOptimal) LevelCompletion.STARRED else LevelCompletion.COMPLETED
        val existing = getCompletion(levelId)
        val existingBest = getBestMoveCount(levelId) ?: Int.MAX_VALUE

        val shouldUpdate = newCompletion.ordinal > existing.ordinal || moveCount < existingBest
        if (shouldUpdate) {
            val bestCompletion = if (newCompletion.ordinal >= existing.ordinal) newCompletion else existing
            prefs.edit()
                .putString("level_$levelId", bestCompletion.name)
                .putInt("moves_$levelId", minOf(moveCount, existingBest))
                .apply()
        }
    }

    fun getCompletionCounts(levels: List<LevelData>): Triple<Int, Int, Int> {
        var completed = 0
        var starred = 0
        for (level in levels) {
            when (getCompletion(level.id)) {
                LevelCompletion.COMPLETED -> completed++
                LevelCompletion.STARRED -> starred++
                LevelCompletion.INCOMPLETE -> {}
            }
        }
        return Triple(levels.size, completed + starred, starred)
    }
}
