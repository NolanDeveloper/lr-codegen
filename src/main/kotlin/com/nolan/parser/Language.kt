package com.nolan.parser

import com.nolan.drawTable
import java.util.*

data class Item(val rule: Rule, val position: Int) {
    val observableSymbol = rule.production.getOrNull(position)?.name

    override fun toString(): String {
        val nonterminal = rule.nonterminal
        val symbols = rule.production.map { it.name }
        val afterArrow = (symbols.take(position) + listOf(".") + symbols.drop(position)).joinToString(" ")
        return "$nonterminal -> $afterArrow"
    }
}

sealed class Action
data class Shift(val newState: Int) : Action() {
    override fun toString() = "s$newState"
}

data class Reduce(val rule: Int) : Action() {
    override fun toString() = "r$rule"
}

object Accept : Action() {
    override fun toString() = "acc"
}

/**
 * @param rawRules rules without start non terminal.
 */
class Grammar(rawRules: List<Rule>) {
    companion object {
        const val START_NONTERMINAL = "\$START$" // make sure it contains symbols that cannot be used in identifiers
        const val END_TERMINAL = "$"
    }

    data class State(
        val state: Int,
        val token: String,
        val tokenAttributes: MutableMap<String, String> = hashMapOf()
    )

    private val nonterminals = listOf(START_NONTERMINAL) + rawRules.map { it.nonterminal.name }.toSet()
    private val terminals = rawRules.flatMap { it.production.map(Symbol::name) }.toSet() - nonterminals + END_TERMINAL
    private val startRule = Rule(Symbol(START_NONTERMINAL), listOf(rawRules[0].nonterminal))
    private val rules = listOf(startRule) + rawRules
    private val rulesByNonTerminal = rules.groupBy { it.nonterminal.name }
    private val productions = rules.groupBy { it.nonterminal }.mapValues { it.value.map(Rule::production) }
    private val allSymbols = nonterminals + terminals
    private val first: Map<String, Set<String>>
    private val follow: Map<String, Set<String>>
    private val canonicalSet: List<Set<Item>>
    private val tableAction: Map<Pair<Int, String>, Set<Action>>
    private val tableGoto: Map<Pair<Int, String>, Int>

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
        follow[START_NONTERMINAL] = mutableSetOf("$")
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

        canonicalSet = items()

        val tableAction = mutableMapOf<Pair<Int, String>, MutableSet<Action>>()
        for ((i, set) in canonicalSet.withIndex()) {
            for (item in set) {
                if (item.observableSymbol == null || item.position == item.rule.production.size) {
                    if (item.rule.nonterminal.name == START_NONTERMINAL) {
                        tableAction[Pair(i, END_TERMINAL)] = hashSetOf<Action>(Accept)
                    } else {
                        for (a in follow[item.rule.nonterminal.name]!!.filter { it in terminals }) {
                            if (tableAction[Pair(i, a)]?.contains(Accept) ?: false) {
                                continue
                            }
                            if (item.rule.condition != null) {
                                tableAction
                                    .getOrPut(Pair(i, a)) { HashSet() }
                                    .add(Reduce(rules.indexOf(item.rule)))
                            } else {
                                val prevRuleWithoutCondition = tableAction
                                    .getOrPut(Pair(i, a)) { HashSet() }
                                    .singleOrNull { it is Reduce && rules[it.rule].condition == null } as Reduce?
                                // We prefer longer reduces as they are always more specific
                                if (prevRuleWithoutCondition == null) {
                                    tableAction
                                        .getOrPut(Pair(i, a)) { HashSet() }
                                        .add(Reduce(rules.indexOf(item.rule)))
                                } else if (item.rule.production.size >= rules[prevRuleWithoutCondition.rule].production.size) {
                                    val actions = tableAction[Pair(i, a)]!!
                                    actions.remove(prevRuleWithoutCondition)
                                    actions.add(Reduce(rules.indexOf(item.rule)))
                                }
                            }
                        }
                    }
                } else {
                    val a = item.observableSymbol
                    if (a !in terminals) continue
                    val newState = goto(set, item.observableSymbol)
                    val j = canonicalSet.indexOf(newState)
                    tableAction.getOrPut(Pair(i, a)) { HashSet() }.add(Shift(j))
                }
            }
        }
        this.tableAction = tableAction

        val tableGoto = mutableMapOf<Pair<Int, String>, Int>()
        for ((i, set) in canonicalSet.withIndex()) {
            for (a in nonterminals) {
                val nextState = canonicalSet.indexOf(goto(set, a))
                if (nextState == -1) continue
                require(Pair(i, a) !in tableGoto)
                tableGoto[Pair(i, a)] = nextState
            }
        }
        this.tableGoto = tableGoto

        // Draw canonical sets
        for ((i, state) in canonicalSet.withIndex()) {
            println("I$i")
            for ((j, item) in state.withIndex()) {
                println(String.format("%5d: %s", j, item))
            }
        }
        // Draw tables
        run {
            val table =
                listOf(listOf("") + terminals) +
                        canonicalSet.mapIndexed { i, _ ->
                            listOf(i.toString()) + terminals.map { a -> tableAction[Pair(i, a)]?.toString() ?: "" }
                        }
            drawTable(table)
        }
        run {
            val table =
                listOf(listOf("") + nonterminals) +
                        canonicalSet.mapIndexed { i, _ ->
                            listOf(i.toString()) + nonterminals.map { a -> tableGoto[Pair(i, a)]?.toString() ?: "" }
                        }
            drawTable(table)
        }

        // Check tables have nos conflicts
        for (i in canonicalSet.indices) {
            for (a in terminals) {
                val actions = tableAction[Pair(i, a)] ?: continue
                val nShifts = actions.count { it is Shift }
                val nReduce = actions.count { it is Reduce }
                val nReduceWithoutCondition = actions.count { it is Reduce && rules[it.rule].condition == null }
                val nAccepts = actions.count { it is Accept }
                //
                if (nAccepts > 0) {
                    check(nAccepts == 1 && nShifts == 0 && nReduce == 0) { "accept is only allowed exclusively" }
                }
                // shift is only allowed exclusively
                if (nShifts > 0) {
                    check(nShifts == 1 && nReduce == 0) { "shift is only allowed exclusively" }
                }
                // no obvious reduce-reduce conflicts
                check(nReduceWithoutCondition <= 1)
            }
            for (a in nonterminals) {
                check(tableGoto[Pair(i, a)] ?: 0 >= 0)
            }
        }
    }

    private fun closure(items: Iterable<Item>): Set<Item> {
        var current = items.toSet()
        var end: Boolean
        do {
            end = true
            var next = current
            for (item in current.filter { it.observableSymbol != null && it.observableSymbol in nonterminals }) {
                for (rule in rulesByNonTerminal[item.observableSymbol]!!) {
                    val newItem = Item(rule, 0)
                    if (newItem !in current) {
                        next = next + newItem
                        end = false
                    }
                }
            }
            current = next
        } while (!end)
        return current
    }

    private fun goto(items: Set<Item>, symbol: String): Set<Item> {
        return closure(items
            .filter { it.observableSymbol == symbol }
            .map { Item(it.rule, it.position + 1) })
    }

    // Returns canonical collection of item sets.
    private fun items(): List<Set<Item>> {
        val firstItemSet = closure(listOf(Item(startRule, 0)))
        var current = setOf(firstItemSet)
        var end: Boolean
        do {
            end = true
            var next = current
            for (itemSet in current) {
                for (symbol in allSymbols) {
                    val gotoSet = goto(itemSet, symbol)
                    if (gotoSet.isNotEmpty() && gotoSet !in current) {
                        next = next + setOf(gotoSet)
                        end = false
                    }
                }
            }
            current = next
        } while (!end)
        return listOf(firstItemSet) + (current - setOf(firstItemSet)).toList()
    }

    private var currentRegister = 0
    private val stack = Stack<State>()

    fun evalExpression(expression: Expression, context: Map<Target, MutableMap<String, String>>): String {
        return when (expression) {
            NewReg -> currentRegister++.toString()
            is Literal -> expression.value
            is AttributeExpression -> context[expression.attribute.target]!![expression.attribute.attribute]!!
        }
    }

    fun evalLogicalExpression(
        expression: LogicalExpression,
        context: Map<Target, MutableMap<String, String>>
    ): Boolean {
        return when (expression) {
            is Equals -> evalExpression(expression.left, context) == evalExpression(expression.right, context)
        }
    }

    fun execAction(
        action: RuleAction,
        context: Map<Target, MutableMap<String, String>>
    ) {
        for (statement in action.statements) {
            when (statement) {
                is EmitStatement -> {
                    println(statement.parts.joinToString("") { evalExpression(it, context) })
                }
                is AssignmentStatement -> {
                    val target = statement.destination.target
                    val attribute = statement.destination.attribute
                    context[target]!![attribute] = evalExpression(statement.value, context)
                }
            }
        }
    }

    fun buildContextForRule(rule: Rule): Map<Target, MutableMap<String, String>> {
        val result = hashMapOf<Target, MutableMap<String, String>>()
        var hashMap: MutableMap<String, String> = hashMapOf()
        result[NamedTarget("\$NEW_NONTERMINAL$")] = hashMap
        result[NamedTarget(rule.nonterminal.name)] = hashMap
        if (rule.nonterminal.index != null) {
            result[NumberedTarget(rule.nonterminal.index)] = hashMap
        }
        val n = rule.production.size
        for ((i, symbol) in rule.production.withIndex()) {
            hashMap = stack[stack.size - (n - i)].tokenAttributes
            result[NamedTarget(symbol.name)] = hashMap
            if (symbol.index != null) {
                result[NumberedTarget(symbol.index)] = hashMap
            }
            check(stack[stack.size - (n - i)].token == symbol.name)
        }
        return result
    }

    data class Terminal(val name: String, val attributes: MutableMap<String, String>)

    fun parse(input: Iterator<Terminal>) {
        currentRegister = 0
        while (stack.isNotEmpty()) {
            stack.pop()
        }

        stack.push(State(0, START_NONTERMINAL))
        if (!input.hasNext()) error("Parsing failed :(")
        var nextTerminal = input.next()
        var a = nextTerminal.name
        while (true) {
            if (a in terminals) {
                val actions = tableAction[Pair(stack.peek().state, a)]
                check(actions != null && actions.isNotEmpty())
                if (actions.contains(Accept)) {
                    break
                } else if (actions.first() is Reduce) {
                    // Choose best rule to apply
                    val action = actions
                        .filterIsInstance<Reduce>()
                        .sortedByDescending { rules[it.rule].production.size } // longer is better
                        .first {
                            val rule = rules[it.rule]
                            val condition = rule.condition
                            // only those satisfying condition
                            condition == null || evalLogicalExpression(condition, buildContextForRule(rule))
                        }
                    val rule = rules[action.rule]
                    val context = buildContextForRule(rule)
                    if (rule.action != null) {
                        execAction(rule.action, context)
                    }
                    repeat(rule.production.size) { stack.pop() }
                    val newState = tableGoto[Pair(stack.peek().state, rule.nonterminal.name)]!!
                    val symbolAttributes = context[NamedTarget("\$NEW_NONTERMINAL$")]!!
                    stack.push(State(newState, rule.nonterminal.name, symbolAttributes))
                } else {
                    val action = actions.single() as Shift
                    stack.push(State(action.newState, a, nextTerminal.attributes))
                    if (!input.hasNext()) error("Parsing failed :(")
                    nextTerminal = input.next()
                    a = nextTerminal.name
                }
            } else {
                check(a in nonterminals) { "Unknown token '$a'" }
            }
        }
        println("(^.^) SUCCESS (^.^)")
    }

    override fun toString(): String {
        return listOf(rules.joinToString("\n"), first, follow).joinToString("\n")
    }
}

data class Symbol(val name: String, val index: Int? = null) {
    override fun toString() = name
}

data class Rule(
    val nonterminal: Symbol,
    val production: List<Symbol>,
    val condition: LogicalExpression? = null,
    val action: RuleAction? = null
) {
    override fun toString() = "$nonterminal -> ${production.joinToString(" ")}"
}

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
data class Equals(val left: Expression, val right: Expression) : LogicalExpression()

data class RuleAction(val statements: List<Statement>)
