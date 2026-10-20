package ru.kids.multiply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultScreen(round: Round, onAgain: () -> Unit, onMenu: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(modeTitle(round.mode, round.table), style = MaterialTheme.typography.titleMedium)
        Text(
            text = when (round.stars) {
                3 -> "🏆"
                2 -> "🎉"
                1 -> "👍"
                else -> "💪"
            },
            fontSize = 80.sp,
        )
        Text(
            text = when (round.stars) {
                3 -> "Превосходно!"
                2 -> "Очень хорошо!"
                1 -> "Неплохо!"
                else -> "Надо ещё потренироваться"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text("Правильно ${round.correct} из ${round.answered}", fontSize = 24.sp)
        Stars(round.stars, size = 44.sp)
        if (round.bestStreak >= 3) {
            Text("Лучшая серия: ${round.bestStreak} подряд 🔥", fontSize = 18.sp)
        }

        if (round.mistakes.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Palette.Bad.copy(alpha = 0.08f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Запомни:", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Bad)
                    round.mistakes.forEach {
                        Text("${it.a} × ${it.b} = ${it.answer}", fontSize = 26.sp)
                    }
                }
            }
        }

        BigButton("Ещё раз", onAgain)
        BigButton("В меню", onMenu, outlined = true)
    }
}
