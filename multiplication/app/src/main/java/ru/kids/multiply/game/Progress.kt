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

    /** Ошибки по примерам: ключ [Fact.key] → вес. */
    fun mistakes(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(MISTAKE) }
        .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(MISTAKE) to it } }
        .toMap()

    /** Ошибка добавляет 2, верный ответ убирает 1: пример уходит из «трудных» после двух верных ответов. */
    fun record(fact: Fact, correct: Boolean) {
        val key = MISTAKE + fact.key
        val old = prefs.getInt(key, 0)
        val new = if (correct) old - 1 else minOf(old + 2, 6)
        prefs.edit {
            if (new > 0) putInt(key, new) else remove(key)
        }
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
        private const val STARS = "stars_"

        fun starsKey(mode: Mode, table: Int) = "${mode.name}_$table"
    }
}
