package ru.kids.multiply.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Primary = Color(0xFF5B4BDB)
    val Orange = Color(0xFFFF9F1C)
    val Background = Color(0xFFFFF8EC)
    val Good = Color(0xFF2BB673)
    val Bad = Color(0xFFE5484D)
    val Gold = Color(0xFFFFB800)

    /** Свой цвет у каждой таблицы — так проще запомнить. */
    private val tables = listOf(
        Color(0xFF8E8E9A), Color(0xFFE5484D), Color(0xFFFF7A1C), Color(0xFFE8A500), Color(0xFF2BB673),
        Color(0xFF12A594), Color(0xFF0090FF), Color(0xFF3E63DD), Color(0xFF8E4EC6), Color(0xFFD6409F),
    )

    fun table(n: Int): Color = tables[(n - 1).mod(tables.size)]
}

@Composable
fun MultiplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary,
            onPrimary = Color.White,
            secondary = Palette.Orange,
            onSecondary = Color.White,
            background = Palette.Background,
            onBackground = Color(0xFF1F1D2B),
            surface = Color.White,
            onSurface = Color(0xFF1F1D2B),
        ),
        content = content,
    )
}
