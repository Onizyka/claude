package ru.kids.multiply.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    @Test
    fun optionsContainAnswerAndThreeDistinctPositiveWrongOnes() {
        val generator = QuestionGenerator(Random(1))
        for (a in 1..20) for (b in 1..20) {
            repeat(5) {
                assertGoodOptions(generator.options(Fact(a, b)), a * b)
            }
        }
    }

    @Test
    fun tableModeCoversWholeTable() {
        val questions = QuestionGenerator(Random(2)).session(Mode.TABLE, 7, 10)
        assertEquals(10, questions.size)
        val factors = questions.map { val f = it.fact!!; if (f.a == 7) f.b else f.a }.toSet()
        assertEquals((1..10).toSet(), factors)
    }

    @Test
    fun noSameExampleTwiceInARow() {
        val generator = QuestionGenerator(Random(3))
        for (mode in listOf(Mode.TABLE, Mode.MIXED, Mode.HARD)) {
            val keys = generator.session(mode, 3, 30).map { it.key }
            keys.zipWithNext().forEach { (x, y) -> assertNotEquals("$mode", x, y) }
        }
    }

    @Test
    fun hardModeMixesSquaresProductsAndBrackets() {
        val questions = QuestionGenerator(Random(4)).session(Mode.HARD, 0, 300)
        val squares = questions.count { it.fact?.let { f -> f.a == f.b } == true }
        val brackets = questions.count { it.fact == null }
        assertTrue("квадратов: $squares", squares in 90..150)
        assertTrue("со скобками: $brackets", brackets in 60..120)
        assertTrue(questions.any { it.fact?.let { f -> f.a >= 13 && f.a == f.b } == true })
        questions.forEach { assertGoodOptions(it.options, it.answer) }
    }

    @Test
    fun bracketExamplesAreCorrectAndUseDifferentNumbers() {
        val generator = ExpressionGenerator(Random(6))
        val seen = mutableSetOf<String>()
        repeat(3000) {
            val e = generator.next()
            seen += e.text.replace(Regex("\\d+"), "n")
            assertTrue(e.text, '(' in e.text)
            assertEquals(e.text, evaluate(e.text), e.answer)
            assertTrue(e.text, e.answer in 1..120)
            val numbers = Regex("\\d+").findAll(e.text).map { it.value.toInt() }.toList()
            assertEquals("числа должны быть разными: ${e.text}", numbers.size, numbers.toSet().size)
            e.withoutBrackets?.let {
                val plain = e.text.replace("(", "").replace(")", "")
                assertEquals(plain, evaluate(plain), it)
            }
            assertGoodOptions(QuestionGenerator(Random(it)).options(e), e.answer)
        }
        // Есть все четыре действия и все виды примеров.
        assertTrue(seen.joinToString(), seen.size == 10)
        listOf("+", "−", "×", ":").forEach { op -> assertTrue(op, seen.any { op in it }) }
    }

    @Test
    fun mistakesModeUsesOnlyMistakes() {
        val mistakes = mapOf("7x8" to 2, "6x9" to 4)
        val questions = QuestionGenerator(Random(5), mistakes).session(Mode.MISTAKES, 0, 6)
        assertEquals(6, questions.size)
        assertTrue(questions.all { it.key in mistakes.keys })
    }

    @Test
    fun factKeyIgnoresOrder() {
        assertEquals(Fact(8, 7).key, Fact(7, 8).key)
        assertEquals(Fact(7, 8), Fact.fromKey("7x8"))
    }

    private fun assertGoodOptions(options: List<Int>, answer: Int) {
        assertEquals(4, options.size)
        assertEquals(4, options.toSet().size)
        assertTrue(answer in options)
        assertTrue(options.all { it > 0 })
    }

    /** Считает пример по правилам порядка действий; деление должно быть без остатка. */
    private fun evaluate(text: String): Int {
        val tokens = Regex("\\d+|[-+−×:()]").findAll(text).map { it.value }.toList()
        var pos = 0

        fun expr(): Int {
            fun term(): Int {
                fun factor(): Int {
                    val t = tokens[pos++]
                    if (t == "(") return expr().also { assertEquals(")", tokens[pos++]) }
                    return t.toInt()
                }
                var v = factor()
                while (pos < tokens.size && tokens[pos] in listOf("×", ":")) {
                    val op = tokens[pos++]
                    val r = factor()
                    v = if (op == "×") v * r else {
                        assertEquals("деление с остатком: $text", 0, v % r)
                        v / r
                    }
                }
                return v
            }
            var v = term()
            while (pos < tokens.size && tokens[pos] in listOf("+", "−")) {
                v = if (tokens[pos++] == "+") v + term() else v - term()
            }
            return v
        }
        return expr().also { assertEquals(text, tokens.size, pos) }
    }
}
