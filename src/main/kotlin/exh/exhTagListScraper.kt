package exh

import org.jsoup.Jsoup
import java.io.File


fun exhTagListScraper() {

    val path = System.getProperty("user.dir") + "\\src\\main\\kotlin\\exh"

    val files = File(path).walk().filter { it.extension == "html" }.toMutableList()

    val html = files.map {
        Jsoup.parse(it, Charsets.UTF_8.displayName())
    }

    println("Working Directory = $path")

    val lists = html.mapIndexed { index, document ->
        Pair<String, MutableList<String>>(files[index].nameWithoutExtension ,document.select("tr > td > a")
            .map {
                // misc is a problem because the tags in the page dont contain misc: in front of them, its manually fixed here
                if (files[index].nameWithoutExtension == "misc") {
                    "misc:${it.html()}"
                } else {
                    it.html()
                }

            }.toMutableList())
    }.toMutableList()

    //Split the artist tags into 2 lists because kotlin cannot compile over 8000 tags in one lsit
    val artists = lists.filter { it.first == "artist" }[0].second.spliterator()
    val artists2 = artists.trySplit()
    val artistPair1 = Pair("artist", mutableListOf<String>())
    val artistPair2 = Pair("artist2", mutableListOf<String>())
    artists.forEachRemaining {
        artistPair2.second += it
    }
    artists2.forEachRemaining {
        artistPair1.second += it
    }

    //Manually order the list based on importance, this is mainly a preference ofr mine
    val newLists = lists.filter { it.first == "female" }.toMutableList()
    newLists += lists.filter { it.first == "male" }
    newLists += lists.filter { it.first == "language" }
    newLists += lists.filter { it.first == "reclass" }
    newLists += lists.filter { it.first == "misc" }
    newLists += lists.filter { it.first == "parody" }
    newLists += lists.filter { it.first == "character" }
    newLists += lists.filter { it.first == "group" }
    newLists += artistPair1
    newLists += artistPair2

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

    //start creating most of the EHTags.kt, half is in a string builder so that I can put namespaces and the get all tags at the top(it also helps with performance)
    val stringBuilder = StringBuilder()

    file.appendText("package exh.eh\n\nclass EHTags {\n    companion object {\n")

    //The namespace list that will be filled out, its a set so no duplicates happen(it was a problem with artists)
    val namespaces = mutableSetOf<String>()

    newLists.forEachIndexed { listIndex, tags ->
        println(tags.second.size)
        val namespace = tags.second.first().split(":")[0]
        namespaces += namespace
        stringBuilder.append("        fun get${tags.first.capitalize()}Tags() = listOf(\n")
        tags.second.forEachIndexed { index, tag ->
            if (index != tags.second.size - 1) {
                stringBuilder.append("            \"$tag\",\n")
            } else {
                stringBuilder.append("            \"$tag\"\n        )\n")
            }
        }
        if (listIndex != newLists.size - 1) {
            stringBuilder.append("\n")
        }
    }

    stringBuilder.append("    }\n}\n")

    file.appendText("        fun getAllTags() = ")
    newLists.forEachIndexed { index, namespace ->
        if (index != newLists.size - 1) {
            file.appendText("get${namespace.first.capitalize()}Tags() + ")
        } else {
            file.appendText("get${namespace.first.capitalize()}Tags()\n\n")
        }
    }

    file.appendText("        fun getNameSpaces() = listOf(\n")
    namespaces.forEachIndexed { index, namespace ->
        if (index != namespaces.size - 1) {
            file.appendText("            \"$namespace\",\n")
        } else {
            file.appendText("            \"$namespace\"\n        )\n\n")
        }
    }

    file.appendText(stringBuilder.toString())
}