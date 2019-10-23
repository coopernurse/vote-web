package voteweb

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DataTest {

    @Test
    fun `range winners`() {
        val question = RangeQuestion(id = "q1", question="zz", maxRating = 5, options = listOf("a", "b", "c"))
        val votes = listOf(
                RangeVote("v1", "b1", "q1", mapOf("a" to 4, "b" to 1)),
                RangeVote("v2", "b1", "q1", mapOf("a" to 1, "b" to 2))
        )
        val expected = listOf(
                RangeWinner("a", 2.5),
                RangeWinner("b", 1.1111111111111112)
        )
        assertEquals(expected, toRangeWinners(question, votes))
    }

    @Test
    fun `ballot weights`() {
        val maxRating = 5
        val votes = listOf(
                RangeVote("v1", "b1", "q1", mapOf("a" to 4, "b" to 1)),
                RangeVote("v2", "b1", "q1", mapOf("a" to 1, "b" to 2))
        )
        assertEquals(mapOf("v1" to 1.0, "v2" to 1.0), toBallotWeights(maxRating, setOf(), votes))
        assertEquals(mapOf("v1" to 0.5555555555555556, "v2" to 0.8333333333333334),
                toBallotWeights(maxRating, setOf("a"), votes))
    }

}