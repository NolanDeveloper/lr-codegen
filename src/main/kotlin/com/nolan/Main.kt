package com.nolan

import com.nolan.parser.Grammar
import com.nolan.parser.Lexer
import com.nolan.parser.TokenType
import com.nolan.parser.parseGrammar
import java.io.File

// todo: replace hard coded lexer with user provided

sealed class Token(val attributes: MutableMap<String, String>)

object EndOfInput : Token(hashMapOf()), TokenType<Token> {
    override fun toString() = "$"
    override val regex = "".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

object PlusSign : Token(hashMapOf()), TokenType<Token> {
    override fun toString() = "+"
    override val regex = """\+""".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

object EqualSign : Token(hashMapOf()), TokenType<Token> {
    override fun toString() = "="
    override val regex = """=""".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

object Indirection : Token(hashMapOf()), TokenType<Token> {
    override fun toString() = "ind"
    override val regex = """ind""".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

data class Constant(val value: String) : Token(hashMapOf(Pair("value", value))) {
    override fun toString() = "C"

    companion object : TokenType<Token> {
        override val regex = """C(\S+)""".toRegex()
        override fun produceToken(match: MatchResult?) = Constant(match!!.groups[1]!!.value)
    }
}

data class Whitespace(val content: String) : Token(hashMapOf()) {
    override fun toString() = content

    companion object : TokenType<Token> {
        override val regex = """\s""".toRegex()
        override fun produceToken(match: MatchResult?) = null
    }
}

data class Register(val value: String) : Token(hashMapOf(Pair("value", value))) {
    override fun toString() = "register"

    companion object : TokenType<Token> {
        override val regex = """R(\S+)""".toRegex()
        override fun produceToken(match: MatchResult?) = Register(match!!.groups[1]!!.value)
    }
}

data class Memory(val value: String) : Token(hashMapOf(Pair("value", value))) {
    override fun toString() = "memory"

    companion object : TokenType<Token> {
        override val regex = """M(\S+)""".toRegex()
        override fun produceToken(match: MatchResult?) = Memory(match!!.groups[1]!!.value)
    }
}

val tokenTypes = listOf(EndOfInput, PlusSign, EqualSign, Indirection, Constant, Whitespace, Register, Memory)

fun main() {
    val grammar = parseGrammar("data/rules.txt")
    val input =
        File("data/code.ir")
            .readLines()
            .filter { it.isNotBlank() && !it.trim().startsWith("//") }
            .joinToString("\n")
    val lexer = Lexer(tokenTypes, EndOfInput, input)
    val terminals =
        generateSequence {
            val token = lexer.currentToken
            val terminal = Grammar.Terminal(token.toString(), token.attributes)
            lexer.nextToken()
            terminal
        }.iterator()
    grammar.parse(terminals)
}