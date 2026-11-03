package com.metalsyntax.paritykt.data

/**
 * Calculadora del teclado de montos (evaluateExpression de AddTransaction.tsx): + − × ÷ con precedencia, decimales con
 * punto o coma. Devuelve null si la expresión está incompleta o divide entre cero.
 */
object Calc {
    fun eval(expr: String): Double? {
        val s = expr.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').filter { !it.isWhitespace() }
        if (s.isEmpty()) return null
        var i = 0
        fun number(): Double? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull()
        }
        fun term(): Double? {
            var v = number() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]; val r = number() ?: return null
                v = if (op == '*') v * r else { if (r == 0.0) return null; v / r }
            }
            return v
        }
        var v = term() ?: return null
        while (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val op = s[i++]; val r = term() ?: return null
            v = if (op == '+') v + r else v - r
        }
        return if (i == s.length) v else null
    }

    fun isExpression(s: String) = s.any { it in "+-−*/×÷" }
}
