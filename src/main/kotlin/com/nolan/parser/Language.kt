package com.nolan.parser

/**
 * @param rawRules rules without start non terminal.
 */
class Grammar(rawRules: List<Rule>) {
    companion object {
        const val START_NONTERMINAL = "\$START$" // make sure it contains symbols that cannot be used in identifiers
    }

    private val rules = listOf(
        Rule(
            Symbol(
                START_NONTERMINAL,
                null
            ), listOf(rawRules[0].nonterminal),
            null, emptyList()
        )
    ) + rawRules
    private val productions = rules.groupBy { it.nonterminal }.mapValues { it.value.map(Rule::production) }
    private val nonterminals = rules.map { it.nonterminal.name }.toSet()
    private val terminals = rules.flatMap { it.production.map(Symbol::name) }.toSet() - nonterminals
    private val first: Map<String, Set<String>>
    private val follow: Map<String, Set<String>>

    private fun firstForSentence(sentence: List<String>): Set<String> {
        val result = mutableSetOf<String>()
        var allContainEmpty = true
        for (symbol in sentence) {
            val firstOfSymbol = checkNotNull(first[symbol])
            result += firstOfSymbol - ""
            if (firstOfSymbol.none { it.isEmpty() }) {
                allContainEmpty = false
                break
            }
        }
        if (allContainEmpty) {
            result += ""
        }
        return result
    }

    init {
        val first = mutableMapOf<String, Set<String>>()
        this.first = first
        for (symbol in terminals) {
            first[symbol] = mutableSetOf(symbol)
        }
        for (symbol in nonterminals) {
            first[symbol] = mutableSetOf()
        }
        var end: Boolean
        do {
            end = true
            for (rule in rules) {
                val before = first[rule.nonterminal.name]!!
                first[rule.nonterminal.name] = first[rule.nonterminal.name]!! +
                        firstForSentence(rule.production.map(Symbol::name))
                val after = first[rule.nonterminal.name]!!
                if (before != after) {
                    end = false
                }
            }
        } while (!end)
        val follow = mutableMapOf<String, Set<String>>()
        this.follow = follow
        for (symbol in nonterminals) {
            follow[symbol] = mutableSetOf()
        }
        follow[START_NONTERMINAL] = mutableSetOf("^")
        do {
            end = true
            for (rule in rules) {
                val n = rule.production.size
                for (i in rule.production.indices) {
                    val nonterminal = rule.production[i].name
                    if (!nonterminals.contains(nonterminal)) continue
                    val before = follow[nonterminal]!!
                    val rest = rule.production.subList(i + 1, n)
                    val firstOfRest = lazy { firstForSentence(rule.production.map { it.name }.subList(i + 1, n)) }
                    if (rest.isNotEmpty()) {
                        follow[nonterminal] = follow[nonterminal]!! + firstOfRest.value - ""
                    }
                    if (rest.isEmpty() || firstOfRest.value.contains("")) {
                        follow[nonterminal] = follow[nonterminal]!! + follow[rule.nonterminal.name]!!
                    }
                    val after = follow[nonterminal]!!
                    if (before != after) {
                        end = false
                    }
                }
            }
        } while (!end)
    }

    override fun toString(): String {
        return listOf(rules.joinToString("\n"), first, follow).joinToString("\n")
    }
}

data class Symbol(val name: String, val index: Int?)

data class Rule(
    val nonterminal: Symbol,
    val production: List<Symbol>,
    val condition: LogicalExpression?,
    val action: List<Statement>
)

sealed class Target
data class NamedTarget(val name: String) : Target()
data class NumberedTarget(val number: Int) : Target()

data class AttributeIdentifier(val target: Target, val attribute: String)

sealed class Statement
data class EmitStatement(val parts: List<Expression>) : Statement()
data class AssignmentStatement(val destination: AttributeIdentifier, val value: Expression) : Statement()

sealed class Expression
object NewReg : Expression() {
    override fun toString() = "<newReg>"
}
data class Literal(val value: String) : Expression()
data class AttributeExpression(val attribute: AttributeIdentifier) : Expression()

sealed class LogicalExpression
data class Equals(val left: Expression, val right: Expression): LogicalExpression()
