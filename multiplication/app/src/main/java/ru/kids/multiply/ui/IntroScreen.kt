package ru.kids.multiply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Шпаргалка перед проверкой: таблица на одно число и подсказка, как её запомнить. */
@Composable
fun IntroScreen(table: Int, onStart: () -> Unit, onBack: () -> Unit) {
    val color = Palette.table(table)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← Назад", fontSize = 18.sp) }
        }
        Text(
            text = "Таблица на $table",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 1..10) {
                Row {
                    Text("$table × $i =", fontSize = 30.sp, textAlign = TextAlign.End, modifier = Modifier.width(170.dp))
                    Text(
                        text = " ${table * i}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.width(80.dp),
                    )
                }
            }
            hint(table)?.let {
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = color.copy(alpha = 0.12f),
                ) {
                    Text("💡 $it", modifier = Modifier.padding(16.dp), fontSize = 17.sp)
                }
            }
        }
        BigButton("Проверить себя!", onStart, color = color)
    }
}

private fun hint(table: Int): String? = when (table) {
    1 -> "Любое число, умноженное на 1, остаётся тем же числом."
    2 -> "Умножить на 2 — значит сложить число само с собой: 7 × 2 = 7 + 7."
    3 -> "Умножить на 3 — это удвоить и прибавить ещё раз: 6 × 3 = 12 + 6."
    4 -> "Умножить на 4 — дважды удвоить: 6 × 4 → 12 → 24."
    5 -> "Ответы на 5 всегда заканчиваются на 0 или 5. 5 × 8 — половина от 8 × 10."
    6 -> "6 × чётное число заканчивается той же цифрой: 6 × 4 = 24, 6 × 8 = 48."
    7 -> "Самые трудные: 7 × 7 = 49, 7 × 8 = 56 (5, 6, 7, 8 — по порядку!)."
    8 -> "Умножить на 8 — трижды удвоить: 7 × 8 → 14 → 28 → 56."
    9 -> "Сумма цифр ответа равна 9: 9 × 7 = 63, 6 + 3 = 9. Или 9 × 7 = 70 − 7."
    10 -> "Умножить на 10 — приписать к числу ноль: 7 × 10 = 70."
    else -> null
}
