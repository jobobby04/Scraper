package exh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

suspend fun exhTagListScraper(args: Map<String, String>) {
    val cookies = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return listOf(
                Cookie.Builder()
                    .name("ipb_member_id")
                    .value(requireNotNull(args["exhId"]) { "exhId was null" } )
                    .domain("repo.e-hentai.org")
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .build(),
                Cookie.Builder()
                    .name("ipb_pass_hash")
                    .value(requireNotNull(args["exhPassHash"]) { "exhPassHash was null" })
                    .domain("repo.e-hentai.org")
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .build()
            )
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
    }

    val client = OkHttpClient.Builder()
        .cookieJar(cookies)
        .build()

    val response = client.newCall(
        Request.Builder()
            .get()
            .url("https://repo.e-hentai.org/tools.php?act=taggroup&show=0")
            .build()
    ).execute()

    val functions = withContext(Dispatchers.IO) {
        Jsoup.parse(response.body!!.string())
            .select("body p a")
            .toList()
            .map { it.attr("href") }
            // Drop temp
            .drop(1)
            .map {
                async {
                    client.newCall(
                        Request.Builder()
                            .get()
                            .url(it)
                            .build()
                    ).execute()
                }
            }
            .awaitAll()

    }
        .map { Jsoup.parse(it.body!!.string()) }
        .map {
            it.select("tr > td > a")
                .toList()
                .map { it.html() }
        }
        .associateBy {
            it.first().substringBefore(':', "")
        }
        .mapValues {
            if (it.key.isBlank()) {
                it.value.map { "misc:$it" }
            } else it.value
        }
        .mapKeys {
            it.key.ifBlank { "misc" }
        }
        .let {
            it.plus("namespaces" to it.keys.toList())
        }
        .flatMap { (key, values) ->
            values.chunked(2000)
                .mapIndexed { index, tags ->
                    buildString {
                        append("get")
                        append(key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                        append(index)
                        append("Tags")
                    } to tags
                }
        }
        .toMap()



    // The EHTags.kt is in the build folder of the project
    val file = File("build\\EHTags.kt")

    //Create the file , if its already there delete it and create it again
    if(file.createNewFile()){
        println("${file.name} is created successfully.")
    } else{
        println("${file.name} already exists.")
        file.delete()
        file.createNewFile()
    }

    val text = buildString {
        appendLine("package exh.eh\n\nobject EHTags {")
        appendLine()

        appendLine(
            functions.keys.toList()
                .dropLast(1)
                .sortedWith(
                    compareBy {
                        when {
                            it.contains("female", true) -> 0
                            it.contains("male", true) -> 1
                            it.contains("language", true) -> 3
                            it.contains("reclass", true) -> 4
                            it.contains("mixed", true) -> 5
                            it.contains("other", true) -> 6
                            it.contains("cosplayer", true) -> 7
                            it.contains("parody", true) -> 8
                            it.contains("character", true) -> 9
                            it.contains("group", true) -> 10
                            it.contains("artist", true) -> 11
                            else -> 99
                        }
                    }
                )
                .joinToString(separator = " + \n        ", prefix = "    fun getAllTags() = ") {
                    "$it()"
                }
        )

        appendLine()

        functions.forEach { (name, items) ->
            append("    ")
            append("fun")
            append(" ")
            append(name)
            append("()")
            append(" = ")
            appendLine("listOf(")
            items.forEach {
                append("        ")
                append("\"")
                append(it)
                append("\"")
                appendLine(",")
            }
            append("    ")
            appendLine(")")
            appendLine()
        }
        // End the file
        append("}")
    }

    file.appendText(text)

    println(file.absolutePath)
}