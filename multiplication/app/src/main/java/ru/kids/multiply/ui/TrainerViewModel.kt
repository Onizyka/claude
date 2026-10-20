package ru.kids.multiply.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.kids.multiply.game.Fact
import ru.kids.multiply.game.Mode
import ru.kids.multiply.game.Progress
import ru.kids.multiply.game.Question
import ru.kids.multiply.game.QuestionGenerator

sealed interface Screen {
    data object Menu : Screen
    data class Intro(val table: Int) : Screen
    data object Game : Screen
    data object Result : Screen
}

/** Текущая тренировка. */
data class Round(
    val mode: Mode,
    val table: Int,
    val questions: List<Question>,
    val totalMs: Long,
    val timeLeftMs: Long = totalMs,
    val index: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val mistakes: List<Fact> = emptyList(),
    /** Выбранный ответ; null — ещё не ответили или время вышло. */
    val chosen: Int? = null,
    val timedOut: Boolean = false,
) {
    val question: Question get() = questions[index]
    val isAnswered: Boolean get() = chosen != null || timedOut
    val isCorrect: Boolean get() = chosen == question.fact.answer

    val stars: Int
        get() {
            val percent = if (answered == 0) 0 else correct * 100 / answered
            return when {
                percent >= 95 -> 3
                percent >= 80 -> 2
                percent >= 60 -> 1
                else -> 0
            }
        }
}

class TrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val progress = Progress(app)

    var screen by mutableStateOf<Screen>(Screen.Menu)
        private set
    var round by mutableStateOf<Round?>(null)
        private set
    var seconds by mutableIntStateOf(progress.secondsPerQuestion)
        private set
    var questionCount by mutableIntStateOf(progress.questionCount)
        private set
    var mistakesCount by mutableIntStateOf(progress.mistakes().size)
        private set
    private var stars by mutableStateOf(progress.allStars())

    private var job: Job? = null
    private var paused = false

    fun stars(mode: Mode, table: Int = 0): Int = stars[Progress.starsKey(mode, table)] ?: 0

    fun changeSeconds(value: Int) {
        seconds = value
        progress.secondsPerQuestion = value
    }

    fun changeQuestionCount(value: Int) {
        questionCount = value
        progress.questionCount = value
    }

    fun openTable(table: Int) {
        screen = Screen.Intro(table)
    }

    fun start(mode: Mode, table: Int = 0) {
        val mistakes = progress.mistakes()
        // Если трудных примеров мало, тренировка короче, чтобы один пример не шёл по кругу.
        val count = if (mode == Mode.MISTAKES) minOf(questionCount, mistakes.size * 3) else questionCount
        val questions = QuestionGenerator(mistakes = mistakes).session(mode, table, count)
        if (questions.isEmpty()) return
        round = Round(mode, table, questions, totalMs = seconds * 1000L)
        screen = Screen.Game
        startTimer()
    }

    fun restart() {
        val r = round ?: return
        start(r.mode, r.table)
    }

    fun answer(option: Int) {
        val r = round ?: return
        if (r.isAnswered || paused) return
        job?.cancel()
        resolve(option)
    }

    fun toMenu() {
        job?.cancel()
        paused = false
        round = null
        mistakesCount = progress.mistakes().size
        screen = Screen.Menu
    }

    /** Приложение свёрнуто — таймер останавливается. */
    fun pause() {
        if (screen != Screen.Game) return
        paused = true
        job?.cancel()
    }

    fun resume() {
        if (!paused) return
        paused = false
        val r = round ?: return
        if (r.isAnswered) next() else startTimer()
    }

    private fun startTimer() {
        job?.cancel()
        val r = round ?: return
        val endsAt = SystemClock.elapsedRealtime() + r.timeLeftMs
        job = viewModelScope.launch {
            while (true) {
                val left = endsAt - SystemClock.elapsedRealtime()
                if (left <= 0) break
                round = round?.copy(timeLeftMs = left)
                delay(50)
            }
            round = round?.copy(timeLeftMs = 0)
            resolve(null)
        }
    }

    private fun resolve(choice: Int?) {
        val r = round ?: return
        val fact = r.question.fact
        val ok = choice == fact.answer
        progress.record(fact, ok)

        // Ошибку повторяем через пару вопросов, но не больше половины тренировки.
        var questions = r.questions
        val repeats = questions.count { it.isRepeat }
        if (!ok && repeats < (questions.size - repeats) / 2) {
            val at = minOf(r.index + 3, questions.size)
            val repeat = QuestionGenerator().question(fact, isRepeat = true)
            questions = questions.toMutableList().apply { add(at, repeat) }
        }

        val streak = if (ok) r.streak + 1 else 0
        round = r.copy(
            questions = questions,
            chosen = choice,
            timedOut = choice == null,
            correct = r.correct + if (ok) 1 else 0,
            answered = r.answered + 1,
            streak = streak,
            bestStreak = maxOf(r.bestStreak, streak),
            mistakes = if (ok || r.mistakes.any { it.key == fact.key }) r.mistakes else r.mistakes + fact,
        )
        job = viewModelScope.launch {
            delay(if (ok) 700 else 2200)
            next()
        }
    }

    private fun next() {
        val r = round ?: return
        if (r.index + 1 < r.questions.size) {
            round = r.copy(index = r.index + 1, chosen = null, timedOut = false, timeLeftMs = r.totalMs)
            startTimer()
        } else {
            progress.saveStars(r.mode, r.table, r.stars)
            stars = progress.allStars()
            mistakesCount = progress.mistakes().size
            screen = Screen.Result
        }
    }

}
