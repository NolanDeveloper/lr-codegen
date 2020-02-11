package com.nolan

import com.nolan.parser.parseGrammar

fun main() {
    val grammar = parseGrammar("data/rules.txt")
    print(grammar)
}
