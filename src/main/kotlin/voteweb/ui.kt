package voteweb

import com.fasterxml.jackson.module.kotlin.readValue
import io.azam.ulidj.ULID
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJackson
import org.mapdb.DB
import org.mapdb.Serializer

class UI(val db: DB) {

    fun homePage(ctx: Context) {
        ctx.render("/home.mustache")
    }

    fun createBallot(ctx: Context) {
        renderBallot(ctx, Ballot())
    }

    fun editBallot(ctx: Context) {
        renderBallot(ctx, dbGetBallot(ctx.pathParam("id")))
    }

    fun saveBallot(ctx: Context) {
        val ballot = toBallot(ctx)
        dbPutJSON("ballots", ballot.id, ballot)
        ctx.redirect("ballot/${ballot.id}")
    }

    fun renderBallot(ctx: Context, ballot: Ballot) {
        ctx.render("/createBallot.mustache", mapOf(
                "ballot" to ballot))
    }

    fun voteForm(ctx: Context) {
        val ballotId = ctx.pathParam("id")
        cookieIdOrRandom("vote_$ballotId", ctx)
        val ballot = dbGetBallot(ballotId)
        if (ballot.name.isEmpty() || ballot.rangeQuestions.isEmpty()) {
            ctx.render("/ballotNotFound.mustache")
        } else {
            ctx.render("/vote.mustache", mapOf(
                    "ballot" to ballot))
        }
    }

    fun saveVote(ctx: Context) {
        val ballotId = ctx.pathParam("id")
        val voteId = cookieIdOrRandom("vote_$ballotId", ctx)
        val ballot = dbGetBallot(ballotId)
        val vote = toVote(ctx, voteId)
        dbPutJSON("votes", vote.id, vote)
        dbLinkBallotVote(ballotId, voteId)
        ctx.render("/voteSaved.mustache", mapOf(
                "ballot" to ballot))
    }

    fun viewResults(ctx: Context) {
        val ballotId = ctx.pathParam("id")
        val ballot = dbGetBallot(ballotId)
        val votes = dbGetVotesForBallot(ballotId)
        if (ballot.name.isEmpty() || ballot.rangeQuestions.isEmpty()) {
            ctx.render("/ballotNotFound.mustache")
        } else {
            for (v in votes) {
                println(v)
            }
            val result = toBallotResult(ballot, votes)
            ctx.render("/results.mustache", mapOf("result" to result,
                    "ballot" to ballot))
        }
    }

    ///////////////////////////////////////////

    private fun toBallot(ctx: Context): Ballot {
        val formMap = ctx.formParamMap()
        val questions = mutableListOf<Question>()
        val rangeQuestions = mutableListOf<RangeQuestion>()
        for ((k, v) in formMap) {
            if (v.isNotEmpty() && v[0].isNotEmpty()) {
                if (k.startsWith("question_")) {
                    val id = idOrRandom(k.substring(9))
                    questions.add(Question(id = id, question = v[0]))
                } else if (k.startsWith("range_question_")) {
                    val idOrig = k.substring(15)
                    val id = idOrRandom(idOrig)
                    val options = formMap.get("range_options_$idOrig")
                    rangeQuestions.add(RangeQuestion(id = id, question = v[0], options = options!![0].split("\n").map(String::trim)))
                }
            }
        }
        return Ballot(id = ctx.formParam("ballotId", ULID.random().toString())!!,
                name = ctx.formParam("name", "unknown ballot")!!,
                questions = questions,
                rangeQuestions = rangeQuestions)
    }

    private fun toVote(ctx: Context, voteId: String): Vote {
        val formMap = ctx.formParamMap()
        val answers = mutableMapOf<String,String>()
        for ((k, v) in formMap) {
            if (v.isNotEmpty() && v[0].isNotEmpty()) {
                if (k.startsWith("question_") || k.startsWith("range_")) {
                    answers.put(k.trim(), v[0].trim())
                }
            }
        }
        return Vote(id = voteId, ballotId = ctx.formParam("ballotId")!!, answers = answers)
    }

    private fun dbPutJSON(type: String, id: String, data: Any) {
        val dataJson = JavalinJackson.getObjectMapper().writeValueAsString(data)
        val map = db.hashMap(type, Serializer.STRING, Serializer.STRING).createOrOpen()
        map.put(id, dataJson)
        db.commit()
    }

    private fun dbGetBallot(id: String): Ballot {
        val map = db.hashMap("ballots", Serializer.STRING, Serializer.STRING).createOrOpen()
        val dataJson = map.get(id)
        return if (dataJson == null) Ballot(id) else JavalinJackson.getObjectMapper().readValue(dataJson)
    }

    private fun dbGetVotesForBallot(ballotId: String): List<Vote> {
        val votes = mutableListOf<Vote>()
        val voteMap = db.hashMap("votes", Serializer.STRING, Serializer.STRING).createOrOpen()
        val voteIdSet = db.hashSet("ballot_votes_${ballotId}", Serializer.STRING).createOrOpen()
        for (voteId in voteIdSet) {
            val dataJson = voteMap.get(voteId)
            if (dataJson != null) {
                votes.add(JavalinJackson.getObjectMapper().readValue(dataJson))
            }
        }
        return votes
    }

    private fun dbLinkBallotVote(ballotId: String, voteId: String) {
        val voteIdSet = db.hashSet("ballot_votes_${ballotId}", Serializer.STRING).createOrOpen()
        voteIdSet.add(voteId)
        db.commit()
    }

    private fun idOrRandom(id: String) = if (id.isEmpty()) ULID.random().toString() else id

    private fun cookieIdOrRandom(key: String, ctx: Context): String {
        var value = ctx.cookie(key)
        if (value.isNullOrEmpty()) {
            value = ULID.random().toString()
            ctx.cookie(key, value, -1)
        }
        return value
    }

}