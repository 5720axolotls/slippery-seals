package com.calebnoble.slipperyseals.model

import androidx.compose.ui.graphics.Color

enum class SealColor(val composeColor: Color) {
    RED(Color(0xFFE53935)),
    BLUE(Color(0xFF1E88E5)),
    GREEN(Color(0xFF43A047)),
    YELLOW(Color(0xFFFDD835)),
    PURPLE(Color(0xFF8E24AA)),
    ORANGE(Color(0xFFFB8C00));
}
