package com.nolan.parser

interface TokenType<Token> {
    val regex: Regex
    fun produceToken(match: MatchResult?): Token?
}

class Lexer<Token>(
    tokenTypes: Iterable<TokenType<Token>>,
    private val endOfInputToken: TokenType<Token>,
    private val input: CharSequence
) {
    private val descriptors = tokenTypes.filter { it != endOfInputToken }
    private var offset = 0
    private var line = 1
    private var column = 0
    var currentToken: Token

    val location get() = "offset $offset line $line column $column"

    init {
        currentToken = nextToken()
    }

    tailrec fun nextToken(): Token {
        if (offset >= input.length) { /* end of file */
            val endOfInput = endOfInputToken.produceToken(null)
            currentToken = endOfInput!!
            return currentToken
        }
        var longest = 0
        var longestMatchResult: MatchResult? = null
        var longestDescriptor: TokenType<Token>? = null
        for (descriptor in descriptors) {
            val matchResult = descriptor.regex.find(input, offset) ?: continue
            if (matchResult.range.first != offset) continue
            val length = matchResult.range.last - matchResult.range.first + 1
            if (length > longest) {
                longest = length
                longestMatchResult = matchResult
                longestDescriptor = descriptor
            }
        }
        if (longestMatchResult == null || longestDescriptor == null) {
            val place = input.substring(offset, minOf(input.length, offset + 50))
            throw Error("Unknown token at $location\n$place")
        }
        val matchString = longestMatchResult.value
        for (char in matchString) {
            if ('\n' == char) {
                ++line
                column = 0
            } else {
                ++column
            }
        }
        offset += matchString.length
        // Process input. If `process` returns nothing skip this token.
        val processedToken = longestDescriptor.produceToken(longestMatchResult)
        if (processedToken != null) {
            currentToken = processedToken
        }
        return if (processedToken != null) currentToken else nextToken()
    }
}




