package com.apex.ai

import com.apex.ai.sandbox.ExpressionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionEngineTest {

    @Test
    fun respectsOperatorPrecedence() {
        assertEquals(14.0, ExpressionEngine("2 + 3 * 4").evaluate(), 1e-9)
    }

    @Test
    fun handlesParentheses() {
        assertEquals(20.0, ExpressionEngine("(2 + 3) * 4").evaluate(), 1e-9)
    }

    @Test
    fun handlesPowerRightAssociatively() {
        assertEquals(512.0, ExpressionEngine("2 ^ 3 ^ 2").evaluate(), 1e-9)
    }

    @Test
    fun handlesUnaryMinus() {
        assertEquals(-9.0, ExpressionEngine("-(4 + 5)").evaluate(), 1e-9)
    }

    @Test
    fun handlesFunctionsAndConstants() {
        assertEquals(5.0, ExpressionEngine("sqrt(25)").evaluate(), 1e-9)
        assertEquals(3.0, ExpressionEngine("max(1, 3, 2)").evaluate(), 1e-9)
        assertEquals(0.0, ExpressionEngine("sin(0)").evaluate(), 1e-9)
    }

    @Test
    fun divisionByZeroIsRejected() {
        var thrown = false
        try {
            ExpressionEngine("1 / 0").evaluate()
        } catch (e: ArithmeticException) {
            thrown = true
        }
        assertEquals(true, thrown)
    }

    @Test
    fun unknownIdentifierIsRejected() {
        var thrown = false
        try {
            ExpressionEngine("2 + foo").evaluate()
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertEquals(true, thrown)
    }
}
