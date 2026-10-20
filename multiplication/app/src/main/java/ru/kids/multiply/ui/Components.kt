package ru.kids.multiply.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kids.multiply.game.Mode

/** ★★☆ — лучший результат. */
@Composable
fun Stars(count: Int, size: TextUnit = 18.sp, color: Color = Palette.Gold) {
    Text(
        text = "★".repeat(count) + "☆".repeat(3 - count),
        fontSize = size,
        color = color,
    )
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    outlined: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (outlined) Color.Transparent else color,
        border = if (outlined) BorderStroke(2.dp, color) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (outlined) color else Color.White,
            )
        }
    }
}

/** Ряд вариантов настройки, выбранный подсвечен. */
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (active) 1f else 0.3f)),
            ) {
                Text(
                    text = label(option),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun modeTitle(mode: Mode, table: Int, focus: Mode? = null): String = when (mode) {
    Mode.TABLE -> "Таблица на $table"
    Mode.MIXED -> "Всё вперемешку"
    Mode.HARD -> "Сложный уровень"
    Mode.MISTAKES -> focus?.let { "${modeTitle(it, table)} (работа над ошибками)" } ?: "Работа над ошибками"
    Mode.BRACKETS -> "Примеры со скобками"
}
