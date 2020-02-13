package com.nolan

fun drawTable(table: List<List<String>>) {
    val widths = table.map { it.map { it.length } }
    val nColumns = table.map { it.size }.max() ?: 0
    val columnWidths = (0 until nColumns).map { col -> widths.map { it.getOrNull(col) ?: 0 }.max() }
    for (row in table.indices) {
        print("+")
        for (w in columnWidths) {
            print("-".repeat(w ?: 0))
            print("+")
        }
        println()
        print("|")
        for ((col, w) in columnWidths.withIndex()) {
            val value = table.getOrNull(row)?.getOrNull(col) ?: ""
            print(String.format("%${w}s|", value))
        }
        println()
    }
    print("+")
    for (w in columnWidths) {
        print("-".repeat(w ?: 0))
        print("+")
    }
    println()
}
