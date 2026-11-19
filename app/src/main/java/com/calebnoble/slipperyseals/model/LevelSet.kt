package com.calebnoble.slipperyseals.model

enum class LevelSet(val displayName: String, val fileName: String) {
    TUTORIAL("Tutorial", "tutorial.json"),
    EASY("Easy", "easy.json"),
    MEDIUM("Medium", "medium.json"),
    HARD("Hard", "hard.json"),
    EXPERT("Expert", "expert.json"),
    CLASSIC("Classic", "classic.json"),
    CLASSIC_HARD("Classic Hard", "classic_hard.json"),
    RETRO("Retro", "retro.json"),

    // Backed by an in-memory, ephemeral level rather than a bundled asset.
    RANDOM("Random", "")
}
