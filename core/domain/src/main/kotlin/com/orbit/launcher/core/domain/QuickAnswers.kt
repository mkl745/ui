package com.orbit.launcher.core.domain

import java.util.Locale
import kotlin.math.abs

/** Strict bounded parser: no scripting, side effects, or implicit network access. */
fun quickAnswer(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.length > 120) return null
    val conversion = Regex("^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*(km|mi|m|ft|kg|lb|c|f)\\s+(?:to|in)\\s+(km|mi|m|ft|kg|lb|c|f)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    if (conversion != null) {
        val value = conversion.groupValues[1].toDoubleOrNull() ?: return null
        val from = conversion.groupValues[2].lowercase(Locale.ROOT)
        val to = conversion.groupValues[3].lowercase(Locale.ROOT)
        val factors = mapOf("km" to ("length" to 1000.0), "mi" to ("length" to 1609.344), "m" to ("length" to 1.0), "ft" to ("length" to 0.3048), "kg" to ("mass" to 1.0), "lb" to ("mass" to 0.45359237))
        val result = when {
            from == to -> value
            from == "c" && to == "f" -> value * 9.0 / 5.0 + 32
            from == "f" && to == "c" -> (value - 32) * 5.0 / 9.0
            factors[from] != null && factors[to] != null && factors[from]!!.first == factors[to]!!.first -> value * factors[from]!!.second / factors[to]!!.second
            else -> return null
        }
        return if (result.isFinite()) "${formatAnswer(result)} $to" else null
    }
    // Avoid treating a plain numeric search as a calculator result.
    if (trimmed.none { it in "+-*/×÷()" }) return null
    return try {
        val result = ArithmeticParser(trimmed).parse()
        if (result.isFinite()) formatAnswer(result) else null
    } catch (_: IllegalArgumentException) { null }
}

private fun formatAnswer(number: Double): String {
    if (abs(number) < 1e-10) return "0"
    return String.format(Locale.ROOT, "%.8f", number).trimEnd('0').trimEnd('.')
}

private class ArithmeticParser(private val input: String) {
    private var pos = 0
    fun parse(): Double {
        val result = expression(0)
        whitespace()
        require(pos == input.length)
        return result
    }
    private fun whitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
    private fun consume(char: Char): Boolean { whitespace(); if (pos < input.length && input[pos] == char) { pos++; return true }; return false }
    private fun expression(depth: Int): Double {
        require(depth <= 32)
        var value = term(depth)
        while (true) value = when { consume('+') -> value + term(depth); consume('-') -> value - term(depth); else -> return value }
    }
    private fun term(depth: Int): Double {
        var value = factor(depth)
        while (true) value = when {
            consume('*') || consume('×') -> value * factor(depth)
            consume('/') || consume('÷') -> value / factor(depth)
            else -> return value
        }
    }
    private fun factor(depth: Int): Double {
        require(depth <= 32)
        if (consume('+')) return factor(depth + 1)
        if (consume('-')) return -factor(depth + 1)
        if (consume('(')) { val result = expression(depth + 1); require(consume(')')); return result }
        whitespace()
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        require(start != pos)
        return input.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
    }
}
