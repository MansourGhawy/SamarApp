package com.example.domain

/**
 * Safe equation evaluator tokenizer
 */
fun evaluateSimpleExpression(expr: String): Double? {
    try {
        val sanitized = expr.replace("×", "*").replace("÷", "/")
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (char in sanitized) {
            if (char.isDigit() || char == '.') {
                currentNum.append(char)
            } else if (char in listOf('+', '-', '*', '/')) {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                }
                tokens.add(char.toString())
            }
        }
        if (currentNum.isNotEmpty()) {
            tokens.add(currentNum.toString())
        }
        
        if (tokens.isEmpty()) return null
        
        // Product stage
        val intermediateTokens = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                if (intermediateTokens.isEmpty() || i + 1 >= tokens.size) return null
                val prev = intermediateTokens.removeAt(intermediateTokens.size - 1).toDouble()
                val next = tokens[i + 1].toDouble()
                val res = if (token == "*") prev * next else prev / next
                intermediateTokens.add(res.toString())
                i += 2
            } else {
                intermediateTokens.add(token)
                i++
            }
        }
        
        // Sum additions stage
        if (intermediateTokens.isEmpty()) return null
        var result = intermediateTokens[0].toDouble()
        var j = 1
        while (j < intermediateTokens.size) {
            val op = intermediateTokens[j]
            if (j + 1 >= intermediateTokens.size) break
            val nextVal = intermediateTokens[j + 1].toDouble()
            if (op == "+") {
                result += nextVal
            } else if (op == "-") {
                result -= nextVal
            }
            j += 2
        }
        return result
    } catch (e: Exception) {
        return null
    }
}
