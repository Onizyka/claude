package ru.kids.multiply.game

import android.content.Context
import androidx.core.content.edit

/** Настройки, звёзды за тренировки и статистика ошибок. Хранится на телефоне. */
class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    var secondsPerQuestion: Int
        get() = prefs.getInt(SECONDS, 10)
        set(value) = prefs.edit { putInt(SECONDS, value) }

    var questionCount: Int
        get() = prefs.getInt(COUNT, 20)
        set(value) = prefs.edit { putInt(COUNT, value) }

    /** Ошибки в примерах из таблицы: ключ [Fact.key] → вес. */
    fun mistakes(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(MISTAKE) }
        .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(MISTAKE) to it } }
        .toMap()

    /** Ошибки в примерах со скобками: пример → вес. */
    fun expressionMistakes(): Map<Expression, Int> = prefs.all
        .filterKeys { it.startsWith(EXPRESSION) }
        .mapNotNull { (key, value) ->
            val weight = value as? Int ?: return@mapNotNull null
            val text = key.removePrefix(EXPRESSION)
            val info = prefs.getString(EXPRESSION_INFO + text, null)?.split(";") ?: return@mapNotNull null
            val answer = info[0].toIntOrNull() ?: return@mapNotNull null
            Expression(text, answer, info.getOrNull(1)?.toIntOrNull()) to weight
        }
        .toMap()

    /** Сколько примеров с ошибками на каждом уровне: MIXED, HARD, BRACKETS. */
    fun mistakeCounts(): Map<Mode, Int> {
        val facts = mistakes().keys.mapNotNull(Fact::fromKey)
        return mapOf(
            Mode.MIXED to facts.count { it.level == Mode.MIXED },
            Mode.HARD to facts.count { it.level == Mode.HARD },
            Mode.BRACKETS to expressionMistakes().size,
        )
    }

    /** Ошибка добавляет 2, верный ответ убирает 1: пример уходит из «трудных» после двух верных ответов. */
    fun record(question: Question, correct: Boolean) {
        val fact = question.fact
        val expression = question.expression
        when {
            fact != null -> updateWeight(MISTAKE + fact.key, correct)
            expression != null -> {
                val kept = updateWeight(EXPRESSION + expression.text, correct)
                prefs.edit {
                    val infoKey = EXPRESSION_INFO + expression.text
                    if (kept) putString(infoKey, "${expression.answer};${expression.withoutBrackets ?: ""}") else remove(infoKey)
                }
            }
        }
    }

    /** Возвращает true, если пример остался среди трудных. */
    private fun updateWeight(key: String, correct: Boolean): Boolean {
        val old = prefs.getInt(key, 0)
        val new = if (correct) old - 1 else minOf(old + 2, 6)
        prefs.edit {
            if (new > 0) putInt(key, new) else remove(key)
        }
        return new > 0
    }

    /** Лучший результат (0–3 звезды) по ключу [starsKey]. */
    fun allStars(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(STARS) }
        .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(STARS) to it } }
        .toMap()

    fun saveStars(mode: Mode, table: Int, stars: Int) {
        val key = STARS + starsKey(mode, table)
        if (stars > prefs.getInt(key, 0)) prefs.edit { putInt(key, stars) }
    }

    companion object {
        private const val SECONDS = "seconds"
        private const val COUNT = "count"
        private const val MISTAKE = "m_"
        private const val EXPRESSION = "e_"
        private const val EXPRESSION_INFO = "ei_"
        private const val STARS = "stars_"

        fun starsKey(mode: Mode, table: Int) = "${mode.name}_$table"
    }
}
