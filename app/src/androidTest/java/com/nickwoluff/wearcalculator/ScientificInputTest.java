package com.nickwoluff.wearcalculator;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** 在方屏和圆屏上通过真实按钮验证参数输入及计算状态恢复。 */
@RunWith(AndroidJUnit4.class)
public class ScientificInputTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private boolean savedAutoCheck;
    private SharedPreferences layoutPreferences;
    private Boolean savedRound;
    private SharedPreferences historyPreferences;
    private String savedHistory;

    @Before
    public void launchCalculator() {
        Context context = instrumentation.getTargetContext();
        savedAutoCheck = UpdatePreferences.autoCheck(context);
        UpdatePreferences.setAutoCheck(context, false);
        layoutPreferences = context.getSharedPreferences("screen_layout", Context.MODE_PRIVATE);
        savedRound = layoutPreferences.contains("round") ? layoutPreferences.getBoolean("round", false) : null;
        LayoutPreferences.setRound(context, context.getResources().getConfiguration().isScreenRound());
        historyPreferences = context.getSharedPreferences("calculator_history", Context.MODE_PRIVATE);
        savedHistory = historyPreferences.getString("entries", null);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
    }

    @After
    public void restorePreferences() {
        if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
        SharedPreferences.Editor layout = layoutPreferences.edit();
        if (savedRound == null) layout.remove("round");
        else layout.putBoolean("round", savedRound);
        layout.commit();
        SharedPreferences.Editor history = historyPreferences.edit();
        if (savedHistory == null) history.remove("entries");
        else history.putString("entries", savedHistory);
        history.commit();
        UpdatePreferences.setAutoCheck(instrumentation.getTargetContext(), savedAutoCheck);
    }

    @Test
    public void logButtonUsesBaseCommaAndNumberAcrossPages() {
        press(R.id.btnLog, R.id.btn2, R.id.btnComma, R.id.btn8, R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(2,8)")));
        press(R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("3")));
        onView(withId(R.id.tvHistory)).check(matches(withText("log(2,8)=")));
    }

    @Test
    public void logarithmSupportsNaturalBaseConstant() {
        press(R.id.btnLog, R.id.btnE, R.id.btnComma, R.id.btnE, R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("1")));
    }

    @Test
    public void decimalPointIsIndependentInBothArgumentsAndCannotRepeat() {
        press(R.id.btnLog, R.id.btnDot, R.id.btnDot, R.id.btn5, R.id.btnComma,
                R.id.btnDot, R.id.btn2, R.id.btn5, R.id.btnDot, R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(0.5,0.25)")));
        press(R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("2")));
    }

    @Test
    public void cubeRootSupportsNegativeNumbersAndStartsFreshAfterEquals() {
        press(R.id.btnCbrt, R.id.btnSub, R.id.btn8, R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("-2")));
        press(R.id.btnCbrt, R.id.btn2, R.id.btn7, R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("³√(27)")));
        press(R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("3")));
    }

    @Test
    public void invalidLogarithmShowsErrorAndNextFunctionRecovers() {
        press(R.id.btnLog, R.id.btn1, R.id.btnComma, R.id.btn8, R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("Error")));
        press(R.id.btnCbrt, R.id.btn8, R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("2")));
    }

    @Test
    public void functionPrefixesAndConstantsDeleteInOnePress() {
        // 默认是弧度模式；切换到角度模式后才能使用 2nd 反函数。
        press(R.id.btnScientificAc);
        int[] keys = {R.id.btnLog, R.id.btnSqrt, R.id.btnCbrt, R.id.btnSin, R.id.btnCos,
                R.id.btnTan, R.id.btnPi, R.id.btnE};
        String[] tokens = {"log(", "√(", "³√(", "sin(", "cos(", "tan(", "π", "e"};
        for (int i = 0; i < keys.length; i++) {
            press(keys[i]);
            onView(withId(R.id.tvDisplay)).check(matches(withText(tokens[i])));
            press(R.id.btnScientificDel);
            onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
            press(R.id.btn2, R.id.btnAdd, keys[i], R.id.btnDel);
            onView(withId(R.id.tvDisplay)).check(matches(withText("2+")));
            press(R.id.btnAc);
        }
        press(R.id.btnSecond);
        int[] inverseKeys = {R.id.btnSin, R.id.btnCos, R.id.btnTan};
        String[] inverseTokens = {"sin⁻¹(", "cos⁻¹(", "tan⁻¹("};
        for (int i = 0; i < inverseKeys.length; i++) {
            press(inverseKeys[i]);
            onView(withId(R.id.tvDisplay)).check(matches(withText(inverseTokens[i])));
            press(R.id.btnScientificDel);
            onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
        }
    }

    @Test
    public void backspaceRemovesArgumentsBeforeWholeNestedFunctionPrefix() {
        press(R.id.btnLog, R.id.btn2, R.id.btnComma, R.id.btnSqrt, R.id.btn9,
                R.id.btnRightParen, R.id.btnScientificDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(2,√(9")));
        press(R.id.btnScientificDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(2,√(")));
        press(R.id.btnScientificDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(2,")));
        press(R.id.btnDel, R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("log(")));
        press(R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
        press(R.id.btnLeftParen, R.id.btnLeftParen, R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("(")));
        press(R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
        press(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("12")));
    }

    @Test
    public void mathematicalSymbolsRemainInDisplayAndCalculationHistory() {
        press(R.id.btnSqrt, R.id.btn9, R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("√(9)")));
        press(R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("3")));
        onView(withId(R.id.tvHistory)).check(matches(withText("√(9)=")));
        press(R.id.btnCbrt, R.id.btnSub, R.id.btn8, R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("-2")));
        onView(withId(R.id.tvHistory)).check(matches(withText("³√(-8)=")));
        press(R.id.btnAc, R.id.btnSin, R.id.btnPi, R.id.btnDiv, R.id.btn2,
                R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("1")));
        onView(withId(R.id.tvHistory)).check(matches(withText("sin(π÷2)=")));
        press(R.id.btnAc, R.id.btnScientificAc, R.id.btnSecond, R.id.btnCos, R.id.btn1, R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("cos⁻¹(1°)")));
        press(R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
        onView(withId(R.id.tvHistory)).check(matches(withText("cos⁻¹(1)=")));
    }

    @Test
    public void backspaceAfterEqualsClearsTheResult() {
        press(R.id.btn1, R.id.btn2, R.id.btnAdd, R.id.btn3, R.id.btnEq, R.id.btnDel);
        onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
    }

    @Test
    public void angleModeAddsDisplayOnlyDegreeUnitAndEnablesSecond() {
        // 默认使用弧度模式，2nd 应处于禁用状态。
        onView(withId(R.id.btnSecond)).check(matches(androidx.test.espresso.matcher.ViewMatchers.isNotEnabled()));
        press(R.id.btnScientificAc, R.id.btnSin, R.id.btn9);
        onView(withId(R.id.btnSecond)).check(matches(androidx.test.espresso.matcher.ViewMatchers.isEnabled()));
        onView(withId(R.id.tvDisplay)).check(matches(withText("sin(9°")));
        press(R.id.btnRightParen);
        onView(withId(R.id.tvDisplay)).check(matches(withText("sin(9°)")));
        press(R.id.btnAc, R.id.btnSecond);
        onView(withId(R.id.btnSin)).check(matches(withText("sin⁻¹")));
    }

    @Test
    public void degreeCalculationKeepsDegreeInEquationAndAvoidsFalseEllipsis() {
        press(R.id.btnScientificAc, R.id.btnSin, R.id.btn3, R.id.btn0,
                R.id.btnRightParen, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("0.5")));
        onView(withId(R.id.tvHistory)).check(matches(withText("sin(30°)=")));
    }

    @Test
    public void degreeUnitStaysWithTrigArgumentWhenOperatorIsEntered() {
        press(R.id.btnScientificAc, R.id.btnSin, R.id.btn3, R.id.btn0, R.id.btnAdd);
        onView(withId(R.id.tvDisplay)).check(matches(withText("sin(30°+")));
    }

    @Test
    public void equalsClosesUnmatchedParenthesesBeforeCalculation() {
        press(R.id.btnLeftParen, R.id.btn1, R.id.btnAdd, R.id.btn2, R.id.btnScientificEq);
        onView(withId(R.id.tvDisplay)).check(matches(withText("3")));
        onView(withId(R.id.tvHistory)).check(matches(withText("(1+2)=")));
    }

    private void press(int... ids) {
        for (int id : ids) {
            instrumentation.runOnMainSync(() -> {
                View scientific = activity.findViewById(R.id.scientificButtons);
                ((SwipeLayout) activity.findViewById(R.id.buttonArea))
                        .setCurrentPage(scientific.findViewById(id) == null ? 0 : 1);
            });
            instrumentation.waitForIdleSync();
            onView(withId(id)).perform(click());
        }
    }
}
