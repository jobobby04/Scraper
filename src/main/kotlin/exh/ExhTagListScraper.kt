package exh

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
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
import java.util.Locale
import kotlin.io.path.Path

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

    if (!response.isSuccessful) {
        throw IllegalStateException("${response.code}: ${response.message}")
    }

    val functions = withContext(Dispatchers.IO) {
        Jsoup.parse(response.body.string())
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
        .onEach {
            if (!it.isSuccessful) {
                throw IllegalStateException("${it.code}: ${it.message}")
            }
        }
        .map { Jsoup.parse(it.body.string()) }
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
        .toMap()

    val functionCount = 3

    // The EHTags.kt is in the build folder of the project
    val currentPackage = "exh.eh"
    val tagsPackage = "$currentPackage.tags"
    val superInterface = generateTagListInterface(functionCount)
    val interfaceName = ClassName(tagsPackage, superInterface.name!!)

    val functionTypeSpecs = functions.flatMap { (namespace, tags) ->
        tags.chunked(6000).mapIndexed { index, strings ->
            generateNamespaceObject(if (index > 0) namespace + (index + 1) else namespace, strings, interfaceName)
        }
    }

    val ehTags = generateEhTagsObject(tagsPackage, functionTypeSpecs, functions.keys)

    FileSpec.builder(tagsPackage, "TagList")
        .addType(superInterface)
        .build()
        .writeTo(Path("build"))

    functionTypeSpecs.forEach {
        FileSpec.builder(tagsPackage, it.name!!)
            .addType(it)
            .build()
            .writeTo(Path("build"))
    }

    FileSpec.builder(currentPackage, "EHTags")
        .addType(ehTags)
        .build()
        .writeTo(Path("build"))
}

fun generateTagListInterface(functionCount: Int): TypeSpec {
    return TypeSpec.interfaceBuilder("TagList")
        .also {
            repeat(functionCount) { index ->
                it.addFunction(
                    FunSpec.builder("getTags${index + 1}")
                        .also {
                            if (index > 0) {
                                it.addCode("return emptyList()")
                            } else {
                                it.addModifiers(KModifier.ABSTRACT)
                            }
                        }
                        .returns(List::class.parameterizedBy(String::class))
                        .build()
                )
            }
        }
        .addFunction(
            FunSpec.builder("getTags")
                .returns(List::class.asTypeName().parameterizedBy(List::class.parameterizedBy(String::class)))
                .addCode(
                    "return listOf(\n${(0 until functionCount).joinToString(separator = ",\n    ", prefix = "    ") { "getTags${it + 1}()" }}\n)"
                )
                .build()
        )
        .build()
}

fun generateNamespaceObject(
    namespace: String,
    tags: List<String>,
    superInterface: ClassName
): TypeSpec {
    return TypeSpec.objectBuilder(namespace.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
        .addSuperinterface(superInterface)
        .also { typeSpec ->
            tags.chunked(2000).forEachIndexed { index, funcTags ->
                typeSpec.addFunction(
                    FunSpec.builder("getTags${index + 1}")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(List::class.parameterizedBy(String::class))
                        .addCode(
                            "return listOf(\n${funcTags.joinToString(separator = ",\n    ", prefix = "    ") { "\"$it\"" }}\n)"
                        )
                        .build()
                )
            }
        }
        .build()
}

fun generateEhTagsObject(
    tagsPackage: String,
    functions: List<TypeSpec>,
    namespaces: Set<String>
): TypeSpec {
    val sortedFunctions = functions
        .map {
            ClassName(tagsPackage, it.name!!)
        }
        .sortedBy {
            when {
                it.simpleName.contains("female", true) -> 0
                it.simpleName.contains("male", true) -> 1
                it.simpleName.contains("language", true) -> 3
                it.simpleName.contains("reclass", true) -> 4
                it.simpleName.contains("mixed", true) -> 5
                it.simpleName.contains("other", true) -> 6
                it.simpleName.contains("cosplayer", true) -> 7
                it.simpleName.contains("parody", true) -> 8
                it.simpleName.contains("character", true) -> 9
                it.simpleName.contains("group", true) -> 10
                it.simpleName.contains("artist", true) -> 11
                else -> 99
            }
        }
    return TypeSpec.objectBuilder("EHTags")
        .addFunction(
            FunSpec.builder("getAllTags")
                .returns(List::class.parameterizedBy(String::class))
                .addCode(
                    "return listOf(\n${(sortedFunctions.indices).joinToString(separator = ",\n    ", prefix = "    ") { "%T.getTags()" }}\n).flatten().flatten()",
                    *sortedFunctions.toTypedArray()
                )
                .build()
        )
        .addFunction(
            FunSpec.builder("getNamespaces")
                .returns(List::class.parameterizedBy(String::class))
                .addCode(
                    "return listOf(\n${namespaces.joinToString(separator = ",\n    ", prefix = "    ") { "\"$it\"" }}\n)"
                )
                .build()
        )
        .build()
}