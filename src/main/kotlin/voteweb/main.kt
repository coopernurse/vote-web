package voteweb

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.javalin.plugin.rendering.FileRenderer
import io.javalin.plugin.rendering.JavalinRenderer
import io.javalin.plugin.rendering.template.JavalinMustache
import org.mapdb.DBMaker
import java.io.StringWriter

fun main() {
    val dbFile = if (System.getenv("MAPDB_FILE").isNullOrEmpty())
        "vote.db" else System.getenv("MAPDB_FILE")
    val prodMode = "true".equals(System.getenv("PROD"))
    val db = DBMaker.fileDB(dbFile).make();
    val ui = UI(db)

    if (!prodMode) {
        JavalinRenderer.register(mustacheReloader(), ".mustache")
    }

    val app = Javalin.create().start(8080)
    app.routes {
        get("/", ui::homePage)
        path("ballot") {
            get(":id", ui::editBallot)
            get(ui::createBallot)
            post(ui::saveBallot)
        }
        path("vote/:id") {
            get(ui::voteForm)
            post(ui::saveVote)
        }
        get("results/:id", ui::viewResults)
    }
}

class mustacheReloader : FileRenderer {
    override fun render(filePath: String, model: MutableMap<String, Any>, ctx: Context): String {
        val mustacheFactory = DefaultMustacheFactory("./")
        val stringWriter = StringWriter()
        mustacheFactory.compile(filePath).execute(stringWriter, model).close()
        return stringWriter.toString()
    }
}
