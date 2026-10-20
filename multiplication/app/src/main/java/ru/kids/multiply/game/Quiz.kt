package ru.kids.multiply.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Режим тренировки. */
enum class Mode {
    /** Таблица на одно число: 7 × 1 … 7 × 10. */
    TABLE,

    /** Вся таблица от 2 до 10 вперемешку. */
    MIXED,

    /** Квадраты чисел до 20 × 20, трудные примеры до 12 × 12 и примеры со скобками. */
    HARD,

    /** Только примеры, в которых ребёнок ошибался. */
    MISTAKES,

    /** Только примеры со скобками на четыре действия. */
    BRACKETS,
}

/** Пример «a × b». */
data class Fact(val a: Int, val b: Int) {
    val answer: Int get() = a * b

    /** 7 × 8 и 8 × 7 — один и тот же пример для статистики ошибок. */
    val key: String get() = "${min(a, b)}x${max(a, b)}"

    companion object {
        fun fromKey(key: String): Fact? {
            val parts = key.split("x")
            if (parts.size != 2) return null
            val a = parts[0].toIntOrNull() ?: return null
            val b = parts[1].toIntOrNull() ?: return null
            return Fact(a, b)
        }
    }
}

/**
 * Вопрос с вариантами ответа. [fact] есть у примеров из таблицы — по нему считается статистика ошибок.
 * [isRepeat] — повтор примера, в котором была ошибка.
 */
data class Question(
    val text: String,
    val answer: Int,
    val options: List<Int>,
    val fact: Fact? = null,
    val isRepeat: Boolean = false,
) {
    /** Ключ, по которому одинаковые примеры считаются одним. */
    val key: String get() = fact?.key ?: text
}

/**
 * Составляет тренировку. [mistakes] — сколько раз ошибались в примере (по [Fact.key]):
 * такие примеры попадаются чаще.
 */
class QuestionGenerator(
    private val random: Random = Random.Default,
    private val mistakes: Map<String, Int> = emptyMap(),
) {

    fun session(mode: Mode, table: Int, count: Int): List<Question> = when (mode) {
        Mode.TABLE -> deck(tableFacts(table), count).map { question(it, swapChance = 0.25) }
        Mode.MIXED -> weighted(mixedFacts(), count).map { question(it) }
        Mode.HARD -> hard(count)
        Mode.MISTAKES -> weighted(mistakeFacts(), count).map { question(it) }
        Mode.BRACKETS -> ExpressionGenerator(random).let { generator -> List(count) { question(generator.next()) } }
    }

    /** Пример из таблицы; иногда множители переставлены, чтобы знать и 7 × 3, и 3 × 7. */
    private fun question(fact: Fact, swapChance: Double = 0.5): Question {
        val shown = if (random.nextDouble() < swapChance) Fact(fact.b, fact.a) else fact
        return Question("${shown.a} × ${shown.b}", shown.answer, options(shown), fact = shown)
    }

    private fun question(expression: Expression) =
        Question(expression.text, expression.answer, options(expression))

    /**
     * Три неверных варианта, похожих на правильный: соседние ответы из таблицы
     * (7 × 7 и 7 × 9 вместо 7 × 8), ответ ±1, ±10, переставленные цифры.
     */
    fun options(fact: Fact): List<Int> {
        val answer = fact.answer
        val (a, b) = fact
        val neighbours = listOf((a - 1) * b, (a + 1) * b, a * (b - 1), a * (b + 1))
        val others = listOf(answer + 1, answer - 1, answer + 10, answer - 10, a + b, swapDigits(answer))

        val wrong = LinkedHashSet<Int>()
        usable(neighbours, answer).take(2).forEach { wrong += it }
        usable(others, answer).forEach { if (wrong.size < 3) wrong += it }
        usable(neighbours, answer).forEach { if (wrong.size < 3) wrong += it }
        return withAnswer(wrong, answer)
    }

    /** Для примера со скобками: ответ без учёта скобок, ±1, ±2, ±10, переставленные цифры. */
    fun options(expression: Expression): List<Int> {
        val answer = expression.answer
        val wrong = LinkedHashSet<Int>()
        usable(listOfNotNull(expression.withoutBrackets), answer).forEach { wrong += it }
        val near = listOf(answer + 1, answer - 1, answer + 2, answer - 2, answer + 10, answer - 10, swapDigits(answer))
        usable(near, answer).forEach { if (wrong.size < 3) wrong += it }
        return withAnswer(wrong, answer)
    }

    private fun usable(list: List<Int>, answer: Int) =
        list.filter { it > 0 && it != answer }.distinct().shuffled(random)

    /** Добирает неверные варианты до трёх и перемешивает их с правильным ответом. */
    private fun withAnswer(wrong: MutableSet<Int>, answer: Int): List<Int> {
        while (wrong.size < 3) {
            val candidate = answer + random.nextInt(-12, 13)
            if (candidate > 0 && candidate != answer) wrong += candidate
        }
        return (wrong + answer).shuffled(random)
    }

    private fun swapDigits(n: Int): Int =
        if (n in 10..99 && n % 10 != 0) (n % 10) * 10 + n / 10 else -1

    /** Все примеры таблицы по кругу, каждый круг перемешан: ни один не пропускается. */
    private fun deck(pool: List<Fact>, count: Int): List<Fact> {
        val result = mutableListOf<Fact>()
        while (result.size < count) {
            var round = pool.shuffled(random)
            if (round.size > 1 && round.first() == result.lastOrNull()) {
                round = round.drop(1) + round.first()
            }
            result += round
        }
        return result.take(count)
    }

    /** Случайные примеры; где были ошибки — вероятность выше. Подряд один пример не повторяется. */
    private fun weighted(pool: List<Fact>, count: Int, weight: (Fact) -> Int = { 1 }): List<Fact> {
        if (pool.isEmpty()) return emptyList()
        val result = mutableListOf<Fact>()
        repeat(count) {
            val recent = result.takeLast(min(3, pool.size - 1)).map { it.key }.toSet()
            val candidates = pool.filter { it.key !in recent }.ifEmpty { pool }
            result += pick(candidates) { weight(it) * (1 + 3 * (mistakes[it.key] ?: 0)) }
        }
        return result
    }

    private fun pick(pool: List<Fact>, weight: (Fact) -> Int): Fact {
        val total = pool.sumOf(weight)
        var roll = random.nextInt(total)
        for (fact in pool) {
            roll -= weight(fact)
            if (roll < 0) return fact
        }
        return pool.last()
    }

    /**
     * 40 % — квадраты (большие чаще), 30 % — трудные примеры до 12 × 12,
     * 30 % — примеры со скобками.
     */
    private fun hard(count: Int): List<Question> {
        val squares = weighted(squareFacts(), count) { if (it.a >= 10) 3 else 1 }
        val products = weighted(hardProducts(), count)
        val expressions = ExpressionGenerator(random)
        var s = 0
        var p = 0
        // Квадраты, произведения и скобки не пересекаются, поэтому одинаковых примеров подряд не будет.
        return List(count) {
            val roll = random.nextInt(10)
            when {
                roll < 4 -> question(squares[s++])
                roll < 7 -> question(products[p++])
                else -> question(expressions.next())
            }
        }
    }

    private fun mistakeFacts(): List<Fact> =
        mistakes.filterValues { it > 0 }.keys.mapNotNull(Fact::fromKey)

    companion object {
        fun tableFacts(table: Int) = (1..10).map { Fact(table, it) }

        fun mixedFacts() = (2..9).flatMap { a -> (a..10).map { b -> Fact(a, b) } }

        fun squareFacts() = (2..20).map { Fact(it, it) }

        fun hardProducts() = (3..12).flatMap { a -> (a + 1..12).map { b -> Fact(a, b) } }
            .filter { it.b >= 6 }
    }
}
