package main

import exh.exhTagListScraper

suspend fun main(args: Array<String>) {
    val sortedArgs = args.associate {
        val key = it.substringBefore('=', "")
        val value = it.substringAfter('=', "")
        key to value
    }
    exhTagListScraper(sortedArgs)
}