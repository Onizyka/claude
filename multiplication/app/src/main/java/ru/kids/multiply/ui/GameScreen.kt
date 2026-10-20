package ru.kids.multiply.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val praise = listOf("Верно!", "Молодец!", "Отлично!", "Так держать!", "Супер!")

@Composable
fun GameScreen(round: Round, onAnswer: (Int) -> Unit, onQuit: () -> Unit) {
    val fact = round.question.fact

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onQuit) { Text("✕ Выход", fontSize = 18.sp) }
            Spacer(Modifier.weight(1f))
            Text("${round.index + 1} / ${round.questions.size}", fontSize = 18.sp)
            Spacer(Modifier.width(16.dp))
            Text("✔ ${round.correct}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Good)
        }

        Spacer(Modifier.height(12.dp))
        TimerBar(round.timeLeftMs, round.totalMs)

        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                round.question.isRepeat -> "Попробуй ещё раз"
                round.streak >= 3 -> "🔥 ${round.streak} подряд!"
                else -> modeTitle(round.mode, round.table)
            },
            fontSize = 18.sp,
            color = if (round.question.isRepeat) Palette.Orange else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        val example = "${fact.a} × ${fact.b} = " + if (round.isAnswered) "${fact.answer}" else "?"
        Text(
            text = example,
            // «16 × 16 = 256» крупным шрифтом не помещается на узком экране.
            fontSize = if (example.length > 10) 44.sp else 56.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                !round.isAnswered -> ""
                round.isCorrect -> praise[round.answered % praise.size]
                round.timedOut -> "Время вышло! Запомни ответ."
                else -> "Неверно. Запомни ответ."
            },
            modifier = Modifier.height(36.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (round.isCorrect) Palette.Good else Palette.Bad,
        )
        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            round.question.options.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        AnswerButton(option, round, Modifier.weight(1f)) { onAnswer(option) }
                    }
                }
            }
        }
    }
}

/** Полоска времени: зелёная, потом оранжевая, в конце красная. */
@Composable
private fun TimerBar(leftMs: Long, totalMs: Long) {
    val fraction = (leftMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.5f -> Palette.Good
        fraction > 0.25f -> Palette.Orange
        else -> Palette.Bad
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color.copy(alpha = 0.2f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
        Text(
            text = "⏱ ${(leftMs + 999) / 1000}",
            modifier = Modifier.width(64.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = color,
        )
    }
}

@Composable
private fun AnswerButton(option: Int, round: Round, modifier: Modifier, onClick: () -> Unit) {
    val answer = round.question.fact.answer
    val color = when {
        !round.isAnswered -> MaterialTheme.colorScheme.primary
        option == answer -> Palette.Good
        option == round.chosen -> Palette.Bad
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    }
    Surface(
        onClick = onClick,
        enabled = !round.isAnswered,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(24.dp),
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("$option", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
