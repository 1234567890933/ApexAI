package com.apex.ai.sandbox

/**
 * Recursive-descent arithmetic evaluator.
 *
 * Written by hand instead of using a scripting engine: it is ~4 KB, allocates
 * almost nothing, cannot touch the filesystem or network, and therefore is safe
 * to run on any string a model produces. Grammar:
 *
 *   expr   := term (('+'|'-') term)*
 *   term   := unary (('*'|'/'|'%') unary)*
 *   unary  := ('+'|'-') unary | power
 *   power  := atom ('^' unary)?
 *   atom   := number | '(' expr ')' | ident ('(' args ')')?
 */
class ExpressionEngine(private val source: String) {

    private var pos = 0

    fun evaluate(): Double {
        val value = parseExpr()
        skipWs()
        if (pos < source.length) {
            throw IllegalArgumentException("Unexpected '${source[pos]}' at index $pos")
        }
        if (value.isNaN()) throw ArithmeticException("Result is not a number")
        if (value.isInfinite()) throw ArithmeticException("Result overflowed")
        return value
    }

    private fun parseExpr(): Double {
        var left = parseTerm()
        while (true) {
            skipWs()
            when (peek()) {
                '+' -> { pos++; left += parseTerm() }
                '-' -> { pos++; left -= parseTerm() }
                else -> return left
            }
        }
    }

    private fun parseTerm(): Double {
        var left = parseUnary()
        while (true) {
            skipWs()
            when (peek()) {
                '*' -> { pos++; left *= parseUnary() }
                '/' -> {
                    pos++
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    left /= divisor
                }
                '%' -> {
                    pos++
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                    left %= divisor
                }
                else -> return left
            }
        }
    }

    private fun parseUnary(): Double {
        skipWs()
        return when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            else -> parsePower()
        }
    }

    private fun parsePower(): Double {
        val base = parseAtom()
        skipWs()
        if (peek() == '^') {
            pos++
            return Math.pow(base, parseUnary())
        }
        return base
    }

    private fun parseAtom(): Double {
        skipWs()
        val c = peek() ?: throw IllegalArgumentException("Expression ends unexpectedly")
        if (c == '(') {
            pos++
            val value = parseExpr()
            skipWs()
            if (peek() != ')') throw IllegalArgumentException("Missing ')' at index $pos")
            pos++
            return value
        }
        if (c.isDigit() || c == '.') return parseNumber()
        if (c.isLetter()) return parseIdentifier()
        throw IllegalArgumentException("Unexpected character '$c' at index $pos")
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
        if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
            pos++
            if (pos < source.length && (source[pos] == '+' || source[pos] == '-')) pos++
            while (pos < source.length && source[pos].isDigit()) pos++
        }
        val slice = source.substring(start, pos)
        return slice.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number '$slice'")
    }

    private fun parseIdentifier(): Double {
        val start = pos
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) pos++
        val name = source.substring(start, pos).lowercase()
        skipWs()
        if (peek() == '(') {
            pos++
            val args = mutableListOf<Double>()
            skipWs()
            if (peek() == ')') {
                pos++
            } else {
                args.add(parseExpr())
                skipWs()
                while (peek() == ',') {
                    pos++
                    args.add(parseExpr())
                    skipWs()
                }
                if (peek() != ')') throw IllegalArgumentException("Missing ')' after $name(")
                pos++
            }
            return applyFunction(name, args)
        }
        return constant(name)
    }

    private fun constant(name: String): Double = when (name) {
        "pi" -> Math.PI
        "e" -> Math.E
        "tau" -> Math.PI * 2
        "inf" -> Double.POSITIVE_INFINITY
        else -> throw IllegalArgumentException("Unknown identifier '$name'")
    }

    private fun applyFunction(name: String, a: List<Double>): Double {
        fun one(fn: (Double) -> Double): Double {
            if (a.size != 1) throw IllegalArgumentException("$name() takes exactly 1 argument")
            return fn(a[0])
        }
        return when (name) {
            "sqrt" -> one { if (it < 0) throw ArithmeticException("sqrt of negative") else Math.sqrt(it) }
            "abs" -> one { Math.abs(it) }
            "sin" -> one { Math.sin(it) }
            "cos" -> one { Math.cos(it) }
            "tan" -> one { Math.tan(it) }
            "asin" -> one { Math.asin(it) }
            "acos" -> one { Math.acos(it) }
            "atan" -> one { Math.atan(it) }
            "ln" -> one { if (it <= 0) throw ArithmeticException("ln domain") else Math.log(it) }
            "log" -> one { if (it <= 0) throw ArithmeticException("log domain") else Math.log10(it) }
            "exp" -> one { Math.exp(it) }
            "floor" -> one { Math.floor(it) }
            "ceil" -> one { Math.ceil(it) }
            "round" -> one { Math.round(it).toDouble() }
            "sign" -> one { Math.signum(it) }
            "pow" -> {
                if (a.size != 2) throw IllegalArgumentException("pow() takes 2 arguments")
                Math.pow(a[0], a[1])
            }
            "min" -> a.minOrNull() ?: throw IllegalArgumentException("min() needs arguments")
            "max" -> a.maxOrNull() ?: throw IllegalArgumentException("max() needs arguments")
            "avg" -> {
                if (a.isEmpty()) throw IllegalArgumentException("avg() needs arguments")
                a.average()
            }
            else -> throw IllegalArgumentException("Unknown function '$name'")
        }
    }

    private fun peek(): Char? = if (pos < source.length) source[pos] else null

    private fun skipWs() {
        while (pos < source.length && source[pos].isWhitespace()) pos++
    }
}
