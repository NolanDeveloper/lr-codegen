package com.nolan.parser.expression

import com.nolan.parser.*
import com.nolan.parser.Target
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

sealed class Token

object EndOfInput : Token(),
    TokenType<Token> {
    override fun toString() = "^"
    override val regex = "".toRegex()

    override fun produceToken(match: MatchResult?) = this
}

class Attribute(target: Target, attributeName: String) : Token() {
    val attributeIdentifier = AttributeIdentifier(target, attributeName)
    override fun toString() = "${attributeIdentifier.target}.${attributeIdentifier.attribute}"

    companion object : TokenType<Token> {
        override val regex = """(([^$\s]+)|\$(\d+))\.([^$\s]+)""".toRegex()
        override fun produceToken(match: MatchResult?): Token? {
            match!!
            val name = match.groups[2]
            val target =
                if (name != null) {
                    NamedTarget(name.value)
                } else {
                    NumberedTarget(match.groups[3]!!.value.toInt())
                }
            val attributeName = match.groups[4]!!.value
            return Attribute(target, attributeName)
        }
    }
}

object KeywordNewReg : Token(), TokenType<Token> {
    override fun toString() = "<newReg>"

    override val regex = """<newReg>""".toRegex()
    override fun produceToken(match: MatchResult?) = KeywordNewReg
}

object KeywordEmit : Token(), TokenType<Token> {
    override fun toString() = "emit"

    override val regex = """emit""".toRegex()
    override fun produceToken(match: MatchResult?) = KeywordEmit
}

class StringLiteral(val quotedContent: String) : Token() {
    val unquotedContent = quotedContent.substring(1, quotedContent.length - 1)

    override fun toString() = quotedContent

    companion object : TokenType<Token> {
        override val regex = """"[^"\n]*"""".toRegex()
        override fun produceToken(match: MatchResult?) = StringLiteral(match!!.value)
    }
}

object EqualSign : Token(), TokenType<Token> {
    override fun toString() = "="

    override val regex = """=""".toRegex()
    override fun produceToken(match: MatchResult?) = EqualSign
}

class Whitespace(val content: String) : Token() {
    override fun toString() = content

    companion object : TokenType<Token> {
        override val regex = """\s""".toRegex()
        override fun produceToken(match: MatchResult?): Nothing? = null
    }
}

val tokenTypes = listOf(
    EndOfInput,
    Attribute,
    KeywordNewReg,
    KeywordEmit,
    StringLiteral,
    EqualSign,
    Whitespace
)

class ParserContext(private val lexer: Lexer<Token>) {
    inner class ParsingError(reason: String = "") : Error("Parsing fail at ${lexer.location}: $reason")

    private fun <T : Token> require(tokenType: KClass<T>, message: String = ""): T {
        val currentToken = lexer.currentToken
        if (!tokenType.isInstance(currentToken)) {
            throw ParsingError(message)
        }
        lexer.nextToken()
        return tokenType.cast(currentToken)
    }

    private fun <T : Token> expect(tokenType: KClass<T>): T? {
        val currentToken = lexer.currentToken
        if (!tokenType.isInstance(currentToken)) {
            return null
        }
        lexer.nextToken()
        return tokenType.cast(currentToken)
    }

    private fun parseNewRegExpression(): NewReg? {
        return expect(KeywordNewReg::class)?.let { NewReg }
    }

    private fun parseLiteral(): Literal? {
        return expect(StringLiteral::class)?.unquotedContent?.let(::Literal)
    }

    private fun parseAttributeExpression(): AttributeExpression? {
        return expect(Attribute::class)?.attributeIdentifier?.let(::AttributeExpression)
    }

    private fun parseExpression(): Expression? {
        return parseNewRegExpression() ?:
            parseLiteral() ?:
            parseAttributeExpression()
    }

    private fun parseEmitStatement(): EmitStatement? {
        expect(KeywordEmit::class) ?: return null
        val parts = generateSequence { parseExpression() }
        return EmitStatement(parts.toList())
    }

    private fun parseAssignmentStatement(): AssignmentStatement {
        val attribute = require(Attribute::class)
        require(EqualSign::class)
        val value = parseExpression() ?: throw ParsingError("Expression expected after '='.")
        return AssignmentStatement(attribute.attributeIdentifier, value)
    }

    fun parseStatement(): Statement {
        return parseEmitStatement() ?: parseAssignmentStatement()
    }

    fun parseLogicalExpression(): LogicalExpression {
        val lhs = parseExpression() ?: throw ParsingError("Expression was expected")
        require(EqualSign::class)
        val rhs = parseExpression() ?: throw ParsingError("Expression was expected")
        return Equals(lhs, rhs)
    }
}

fun parseStatement(input: String): Statement {
    val lexer = Lexer(
        tokenTypes,
        EndOfInput,
        input
    )
    return ParserContext(lexer).parseStatement()
}

fun parseLogicalExpression(input: String): LogicalExpression {
    val lexer = Lexer(
        tokenTypes,
        EndOfInput,
        input
    )
    return ParserContext(lexer).parseLogicalExpression()
}