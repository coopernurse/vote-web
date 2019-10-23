package voteweb

import com.fasterxml.jackson.annotation.JsonIgnore
import io.azam.ulidj.ULID

data class Ballot (val id: String = ULID.random().toString(),
                   val updatedAt: Long = System.currentTimeMillis(),
                   val name: String = "",
                   val questions: List<Question> = listOf(),
                   val rangeQuestions: List<RangeQuestion> = listOf())

data class Question (val id: String, val question: String)

data class RangeQuestion (val id: String, val question: String, val options: List<String>, val maxRating: Int = 5) {
    @JsonIgnore
    fun getOptionsStr() = options.joinToString(separator = "\n")
}

data class Vote (val id: String, val ballotId: String, val answers: Map<String,String>)

data class RangeVote (val id: String, val ballotId: String, val questionId: String, val ratings: Map<String,Int>)

data class RangeWinner (val option: String, val mean: Double)

data class RangeQuestionResult (val question: RangeQuestion,
                                val winners: List<RangeWinner>)

data class QuestionResult (val question: String, val answers: List<String>)

data class BallotResult (val ballot: Ballot,
                         val questions: List<QuestionResult>,
                         val rangeQuestions: List<RangeQuestionResult>)

fun toBallotResult(ballot: Ballot, votes: List<Vote>): BallotResult {
    val questions = mutableListOf<QuestionResult>()
    val rangeQuestions = mutableListOf<RangeQuestionResult>()
    val rangeVotesByQuestionId = toRangeVotesByQuestionId(votes)

    for (question in ballot.questions) {
        val answers = mutableListOf<String>()
        val key = "question_${question.id}"
        for (vote in votes) {
            val v = vote.answers.get(key)
            if (v != null) {
                answers.add(v)
            }
        }
        questions.add(QuestionResult(question = question.question, answers = answers))
    }

    for (question in ballot.rangeQuestions) {
        val rangeVotes = rangeVotesByQuestionId.get(question.id)
        if (rangeVotes != null) {
            rangeQuestions.add(RangeQuestionResult(question = question, winners = toRangeWinners(question, rangeVotes)))
        }
    }

    return BallotResult(ballot = ballot, questions = questions, rangeQuestions = rangeQuestions)
}

fun toRangeVotesByQuestionId(votes: List<Vote>): Map<String,List<RangeVote>> {
    val byQuestionId = mutableMapOf<String,MutableList<RangeVote>>()
    for (vote in votes) {
        val ratingsByQuestionId = mutableMapOf<String, MutableMap<String,Int>>()
        for ((k, v) in vote.answers) {
            if (k.startsWith("range_")) {
                val pos = k.indexOf("_", 6)
                if (pos > -1) {
                    val questionId = k.substring(6, pos)
                    val option = k.substring(pos+1)
                    val rating = Integer.parseInt(v)
                    val ratings = ratingsByQuestionId.computeIfAbsent(questionId) { mutableMapOf() }
                    ratings.put(option, rating)
                }
            }
        }
        for ((questionId, ratings) in ratingsByQuestionId) {
            val rangeVotes = byQuestionId.computeIfAbsent(questionId) { mutableListOf() }
            rangeVotes.add(RangeVote(vote.id, vote.ballotId, questionId, ratings))
        }
    }
    return byQuestionId
}

fun toRangeWinners(question: RangeQuestion, votes: List<RangeVote>): List<RangeWinner> {
    val winners = mutableListOf<RangeWinner>()
    val winningOptionsSoFar = mutableSetOf<String>()

    while (winningOptionsSoFar.size < question.options.size) {
        val winner = nextRangeWinner(question.maxRating, winningOptionsSoFar, votes)
        if (winner == null) {
            break
        } else {
            winners.add(winner)
            winningOptionsSoFar.add(winner.option)
        }
    }

    return winners
}

fun nextRangeWinner(maxRating: Int, winningOptionsSoFar: Set<String>, votes: List<RangeVote>): RangeWinner? {
    val weightsByVoteId = toBallotWeights(maxRating, winningOptionsSoFar, votes)
    val weightedRatingByOption = mutableMapOf<String,MutableList<Double>>()
    for (vote in votes) {
        val weight = weightsByVoteId.get(vote.id) ?: 1.0
        for ((option, rating) in vote.ratings) {
            if (!winningOptionsSoFar.contains(option)) {
                val ratings = weightedRatingByOption.computeIfAbsent(option) { mutableListOf() }
                ratings.add(rating.toDouble() * weight)
            }
        }
    }
    if (weightedRatingByOption.isEmpty()) {
        return null
    } else {
        var winningOption = ""
        var winningMeanScore = 0.0
        for ((option, ratings) in weightedRatingByOption) {
            val mean = ratings.sum() / ratings.size.toDouble()
            if (mean >= winningMeanScore) {
                winningOption = option
                winningMeanScore = mean
            }
        }
        return RangeWinner(option = winningOption, mean = winningMeanScore)
    }
}

fun toBallotWeights(maxRating: Int, winningOptionsSoFar: Set<String>, votes: List<RangeVote>): Map<String,Double> {
    val maxRatingDouble = maxRating.toDouble()
    val weightsByVoteId = mutableMapOf<String, Double>()
    for (vote in votes) {
        var sumWinnerRatings = 0.0
        for ((option, rating) in vote.ratings) {
            if (winningOptionsSoFar.contains(option)) {
                sumWinnerRatings += rating
            }
        }
        val weight = maxRatingDouble / (maxRatingDouble + sumWinnerRatings)
        weightsByVoteId.put(vote.id, weight)
    }
    return weightsByVoteId
}