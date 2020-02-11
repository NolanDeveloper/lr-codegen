package com.nolan.parser

import com.nolan.parser.expression.parseLogicalExpression
import com.nolan.parser.expression.parseStatement
import java.io.File
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

sealed class Token

object EndOfInput : Token(),
    TokenType<Token> {
    override fun toString() = "^"
    override val regex = "".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

class Identifier(name: String, index: Int?) : Token() {
    val symbol = Symbol(name, index)
    override fun toString() = "${symbol.name}${"$${symbol.index}"}"

    companion object : TokenType<Token> {
        override val regex = """([^$\s]+)(\$(\d+))?""".toRegex()
        override fun produceToken(match: MatchResult?): Token? {
            val name = match!!.groups[1]!!.value
            val index = match.groups[3]?.value?.toInt()
            return Identifier(name, index)
        }
    }
}

class RightArrow : Token() {
    override fun toString() = "->"

    companion object : TokenType<Token> {
        override val regex = "->".toRegex()
        override fun produceToken(match: MatchResult?) = RightArrow()
    }
}

class ConditionBlock(val content: String) : Token() {
    override fun toString() = content

    companion object : TokenType<Token> {
        override val regex = """\[([^\]]*)\]""".toRegex()
        override fun produceToken(match: MatchResult?) =
            ConditionBlock(match!!.groups[1]?.value ?: "")
    }
}

class ActionBlock(val content: String) : Token() {
    override fun toString() = content

    companion object : TokenType<Token> {
        override val regex = """\{([^\}]*)\}""".toRegex()
        override fun produceToken(match: MatchResult?) =
            ActionBlock(match!!.groups[1]?.value ?: "")
    }
}

class NewLine : Token() {
    override fun toString() = "\n"

    companion object : TokenType<Token> {
        override val regex = "\n".toRegex()
        override fun produceToken(match: MatchResult?) = NewLine()
    }
}

class Whitespace(val content: String) : Token() {
    override fun toString() = content

    companion object : TokenType<Token> {
        override val regex = """\s""".toRegex()
        override fun produceToken(match: MatchResult?) = null
    }
}

val tokenTypes = listOf(
    EndOfInput,
    RightArrow,
    ConditionBlock,
    ActionBlock,
    NewLine,
    Whitespace,
    Identifier
)

class ParserContext(private val lexer: Lexer<Token>) {
    inner class ParsingError(reason: String = "") : Error("Parsing fail at ${lexer.location}: $reason")

    private fun <T : Any> require(tokenType: KClass<T>, message: String = ""): T {
        val currentToken = lexer.currentToken
        if (!tokenType.isInstance(currentToken)) {
            throw ParsingError(message)
        }
        lexer.nextToken()
        return tokenType.cast(currentToken)
    }

    private fun <T : Any> expect(tokenType: KClass<T>): T? {
        val currentToken = lexer.currentToken
        if (!tokenType.isInstance(currentToken)) {
            return null
        }
        lexer.nextToken()
        return tokenType.cast(currentToken)
    }

    private fun parseCondition(): LogicalExpression? {
        val conditionBlock = expect(ConditionBlock::class)?.content ?: ""
        return if (conditionBlock.isBlank()) {
            null
        } else {
            parseLogicalExpression(conditionBlock)
        }
    }

    private fun parseAction(): List<Statement> {
        val actionBlock = expect(ActionBlock::class)?.content ?: ""
        return actionBlock.lines().filter { it.isNotBlank() }.map { parseStatement(it) }
    }

    private fun parseRule(): Rule? {
        val nonTerminal = expect(Identifier::class)?.symbol ?: return null
        require(RightArrow::class)
        val production = generateSequence { expect(Identifier::class)?.symbol }.toList()
        val condition = parseCondition()
        val action = parseAction()
        require(NewLine::class)
        return Rule(nonTerminal, production, condition, action)
    }

    private fun skipEmptyLines() {
        generateSequence { expect(NewLine::class) }.count()
    }

    fun parseGrammar(): Grammar {
        val rules: ArrayList<Rule> = ArrayList()
        skipEmptyLines()
        while (true) {
            val rule = parseRule() ?: break
            rules.add(rule)
            skipEmptyLines()
        }
        return Grammar(rules)
    }
}

fun parseGrammar(path: String): Grammar {
    val input = File(path).readText()
    val lexer = Lexer(
        tokenTypes,
        EndOfInput,
        input
    )
    return ParserContext(lexer).parseGrammar()
}