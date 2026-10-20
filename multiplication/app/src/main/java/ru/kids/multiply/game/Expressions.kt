package ru.kids.multiply.game

import kotlin.random.Random

/**
 * Пример со скобками, например «(12 − 4) : 2».
 * [withoutBrackets] — что получится, если забыть про скобки и считать по порядку действий.
 * Это хороший неверный вариант ответа; null, если так не получается целого положительного числа.
 */
data class Expression(val text: String, val answer: Int, val withoutBrackets: Int?)

/**
 * Примеры со скобками на сложение, вычитание, умножение и деление.
 * Все числа в примере разные, деление всегда без остатка, ответ — натуральное число не больше 120.
 */
class ExpressionGenerator(private val random: Random = Random.Default) {
    private var last: String? = null

    fun next(): Expression {
        while (true) {
            val expression = templates[random.nextInt(templates.size)]() ?: continue
            if (expression.text == last) continue
            last = expression.text
            return expression
        }
    }

    private fun r(range: IntRange) = range.random(random)

    private fun distinct(vararg numbers: Int) = numbers.toSet().size == numbers.size

    private fun Int.positiveOrNull() = takeIf { it > 0 }

    private val templates: List<() -> Expression?> = listOf(
        // (a + b) × c
        {
            val a = r(1..9); val b = r(1..9); val c = r(2..9)
            if (distinct(a, b, c) && a + b <= 12) Expression("($a + $b) × $c", (a + b) * c, a + b * c) else null
        },
        // (a − b) × c
        {
            val a = r(4..19); val b = r(1..a - 2); val c = r(2..9)
            if (distinct(a, b, c) && a - b <= 10) {
                Expression("($a − $b) × $c", (a - b) * c, (a - b * c).positiveOrNull())
            } else null
        },
        // (a + b) : c
        {
            val c = r(2..9); val q = r(2..10); val a = r(1 until c * q); val b = c * q - a
            if (distinct(a, b, c)) {
                Expression("($a + $b) : $c", q, if (b % c == 0) a + b / c else null)
            } else null
        },
        // (a − b) : c
        {
            val c = r(2..9); val q = r(2..10); val b = r(1..20); val a = c * q + b
            if (distinct(a, b, c)) {
                Expression("($a − $b) : $c", q, if (b % c == 0) (a - b / c).positiveOrNull() else null)
            } else null
        },
        // a × (b + c)
        {
            val a = r(2..9); val b = r(1..9); val c = r(1..9)
            if (distinct(a, b, c) && b + c <= 12) Expression("$a × ($b + $c)", a * (b + c), a * b + c) else null
        },
        // a × (b − c)
        {
            val a = r(2..9); val b = r(4..19); val c = r(1..b - 2)
            if (distinct(a, b, c) && b - c <= 10) {
                Expression("$a × ($b − $c)", a * (b - c), (a * b - c).positiveOrNull())
            } else null
        },
        // a : (b + c)
        {
            val d = r(2..9); val q = r(2..10); val a = d * q; val b = r(1 until d); val c = d - b
            if (distinct(a, b, c)) {
                Expression("$a : ($b + $c)", q, if (a % b == 0) a / b + c else null)
            } else null
        },
        // a : (b − c)
        {
            val d = r(2..9); val q = r(2..10); val a = d * q; val c = r(1..9); val b = d + c
            if (distinct(a, b, c)) {
                Expression("$a : ($b − $c)", q, if (a % b == 0) (a / b - c).positiveOrNull() else null)
            } else null
        },
        // (a + b) × (c − d)
        {
            val a = r(1..8); val b = r(1..8); val c = r(3..15); val d = r(1..c - 2)
            if (distinct(a, b, c, d) && a + b <= 10 && c - d <= 9) {
                Expression("($a + $b) × ($c − $d)", (a + b) * (c - d), (a + b * c - d).positiveOrNull())
            } else null
        },
        // a − (b + c)
        {
            val a = r(12..50); val b = r(2..20); val c = r(2..20)
            if (distinct(a, b, c) && b + c < a) Expression("$a − ($b + $c)", a - (b + c), a - b + c) else null
        },
    )
}
