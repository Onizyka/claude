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
                val options = generator.options(Fact(a, b))
                assertEquals(4, options.size)
                assertEquals(4, options.toSet().size)
                assertTrue(a * b in options)
                assertTrue(options.all { it > 0 })
            }
        }
    }

    @Test
    fun tableModeCoversWholeTable() {
        val questions = QuestionGenerator(Random(2)).session(Mode.TABLE, 7, 10)
        assertEquals(10, questions.size)
        val factors = questions.map { if (it.fact.a == 7) it.fact.b else it.fact.a }.toSet()
        assertEquals((1..10).toSet(), factors)
    }

    @Test
    fun noSameExampleTwiceInARow() {
        val generator = QuestionGenerator(Random(3))
        for (mode in listOf(Mode.TABLE, Mode.MIXED, Mode.HARD)) {
            val keys = generator.session(mode, 3, 30).map { it.fact.key }
            keys.zipWithNext().forEach { (x, y) -> assertNotEquals("$mode", x, y) }
        }
    }

    @Test
    fun hardModeHasManySquares() {
        val questions = QuestionGenerator(Random(4)).session(Mode.HARD, 0, 200)
        val squares = questions.count { it.fact.a == it.fact.b }
        assertTrue("квадратов: $squares", squares in 70..130)
        assertTrue(questions.any { it.fact.a >= 13 && it.fact.a == it.fact.b })
    }

    @Test
    fun mistakesModeUsesOnlyMistakes() {
        val mistakes = mapOf("7x8" to 2, "6x9" to 4)
        val questions = QuestionGenerator(Random(5), mistakes).session(Mode.MISTAKES, 0, 6)
        assertEquals(6, questions.size)
        assertTrue(questions.all { it.fact.key in mistakes.keys })
    }

    @Test
    fun factKeyIgnoresOrder() {
        assertEquals(Fact(8, 7).key, Fact(7, 8).key)
        assertEquals(Fact(7, 8), Fact.fromKey("7x8"))
    }
}
