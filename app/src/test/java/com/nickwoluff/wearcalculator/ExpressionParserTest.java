package com.nickwoluff.wearcalculator;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 覆盖双参数对数、嵌套表达式、定义域及新增立方根。 */
public class ExpressionParserTest {
    private BigDecimal evaluate(String expression) {
        return new MainActivity.ExpressionParser(expression).parse();
    }

    @Test
    public void logarithmUsesFirstArgumentAsBase() {
        assertEquals(3.0, evaluate("log(2,8)").doubleValue(), 1e-12);
        assertEquals(2.0, evaluate("log(10,100)").doubleValue(), 1e-12);
        assertEquals(3.0, evaluate("log(10,1000)").doubleValue(), 1e-12);
        assertEquals(1.0, evaluate("log(e,e)").doubleValue(), 1e-12);
        assertEquals(0.0, evaluate("log(2,1)").doubleValue(), 0);
        assertEquals(-3.0, evaluate("log(2,0.125)").doubleValue(), 1e-12);
        assertEquals(2.0, evaluate("log(0.5,0.25)").doubleValue(), 1e-12);
    }

    @Test
    public void logarithmAcceptsExpressionsAndNestedFunctions() {
        assertEquals(3.0, evaluate("log(1+1,2^3)").doubleValue(), 1e-12);
        assertEquals(3.0, evaluate("log(log(10,100),sqrt(64))").doubleValue(), 1e-12);
        assertEquals(4.0, evaluate("log(2,log(2,256))+1").doubleValue(), 1e-12);
        assertEquals(2.0, evaluate(" log ( 0.5 , 0.25 ) ").doubleValue(), 1e-12);
    }

    @Test
    public void logarithmRejectsInvalidDomains() {
        for (String expression : new String[]{"log(0,8)", "log(-2,8)", "log(1,8)",
                "log(1.00,8)", "log(2,0)", "log(2,-8)"}) {
            assertThrows(expression, ArithmeticException.class, () -> evaluate(expression));
        }
    }

    @Test
    public void logarithmRequiresExactlyTwoNonEmptyArguments() {
        for (String expression : new String[]{"log(8)", "log(,8)", "log(2,)",
                "log(2,8,4)", "log(2,8", "2,8", "sin(2,8)"}) {
            assertThrows(expression, IllegalArgumentException.class, () -> evaluate(expression));
        }
    }

    @Test
    public void removedLogarithmFunctionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> evaluate("lg(100)"));
        assertThrows(IllegalArgumentException.class, () -> evaluate("ln(e)"));
    }

    @Test
    public void cubeRootSupportsPositiveNegativeAndZeroValues() {
        assertEquals(3.0, evaluate("cbrt(27)").doubleValue(), 0);
        assertEquals(-2.0, evaluate("cbrt(-8)").doubleValue(), 0);
        assertEquals(0.0, evaluate("cbrt(0)").doubleValue(), 0);
        assertEquals(0.5, evaluate("cbrt(0.125)").doubleValue(), 1e-12);
        assertEquals(2.0, evaluate("cbrt(2*4)").doubleValue(), 0);
        assertEquals(3.0, evaluate("log(2,cbrt(512))").doubleValue(), 1e-12);
    }

    @Test
    public void displayedSymbolsCalculateLikeTheirFunctionNames() {
        assertEquals(3.0, evaluate("√(9)").doubleValue(), 0);
        assertEquals(-2.0, evaluate("³√(-8)").doubleValue(), 0);
        assertEquals(3.0, evaluate("log(2,³√(512))").doubleValue(), 1e-12);
        assertEquals(4.0, evaluate("√(√(256))").doubleValue(), 0);
        assertEquals(Math.PI, evaluate("π").doubleValue(), 0);
        assertEquals(1.0, evaluate("sin(π/2)").doubleValue(), 1e-12);
        assertEquals(Math.PI / 2, evaluate("sin⁻¹(1)").doubleValue(), 1e-12);
        assertEquals(0.0, evaluate("cos⁻¹(1)").doubleValue(), 0);
        assertEquals(Math.PI / 4, evaluate("tan⁻¹(1)").doubleValue(), 1e-12);
        assertThrows(ArithmeticException.class, () -> evaluate("√(-1)"));
    }

    @Test
    public void trigonometricFunctionsUseDegreesWhenRequested() {
        assertEquals(1.0, new MainActivity.ExpressionParser("sin(90)", true).parse().doubleValue(), 1e-12);
        assertEquals(90.0, new MainActivity.ExpressionParser("sin⁻¹(1)", true).parse().doubleValue(), 1e-12);
        assertEquals(Math.sin(90), new MainActivity.ExpressionParser("sin(90)", false).parse().doubleValue(), 1e-12);
    }

    @Test
    public void highPrecisionFunctionsKeepExactSimpleValuesWithoutFloatingPointConversion() {
        assertEquals(0, new BigDecimal("0.5").compareTo(
                new MainActivity.ExpressionParser("sin(30)", true).parse().stripTrailingZeros()));
        assertEquals(0, new BigDecimal("0.5").compareTo(
                new MainActivity.ExpressionParser("cos(60)", true).parse().stripTrailingZeros()));
        assertEquals(0, new BigDecimal("3").compareTo(evaluate("sqrt(9)").stripTrailingZeros()));
        assertEquals(0, new BigDecimal("3").compareTo(evaluate("cbrt(27)").stripTrailingZeros()));
    }

    @Test
    public void unclosedParenthesesAreClosedOnlyAtCalculationBoundary() {
        assertEquals("sin(90))", MainActivity.closeUnclosedParentheses("sin(90))"));
        assertEquals("sin(90)", MainActivity.closeUnclosedParentheses("sin(90"));
        assertEquals("log(2,8)", MainActivity.closeUnclosedParentheses("log(2,8"));
    }

    @Test
    public void existingArithmeticAndScientificFunctionsKeepWorking() {
        assertEquals(0, new BigDecimal("0.3").compareTo(evaluate("0.1+0.2")));
        assertEquals(0, new BigDecimal("512").compareTo(evaluate("2^3^2")));
        assertEquals(new BigDecimal("1152921504606846976"), evaluate("2^60"));
        assertEquals(122.0, evaluate("5!+sqrt(4)").doubleValue(), 0);
        assertEquals(0.5, evaluate("50%").doubleValue(), 0);
        assertEquals(1.0, evaluate("sin(pi/2)").doubleValue(), 1e-12);
    }

    @Test
    public void powerAndDecimalFunctionsRetainBigDecimalPrecision() {
        assertEquals(new BigDecimal("1152921504606846976"), evaluate("2^60"));
        assertEquals(new BigDecimal("0.01"), evaluate("0.1^2").stripTrailingZeros());
        assertEquals(new BigDecimal("0.3"), evaluate("0.1+0.2").stripTrailingZeros());
        assertEquals(0, new BigDecimal("0.5").compareTo(
                new MainActivity.ExpressionParser("sin(30)", true).parse().stripTrailingZeros()));
    }
}
