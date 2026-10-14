package com.nickwoluff.wearcalculator;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends WearActivity {

    private static final String TAG = "WearCalc";
    private TextView tvDisplay, tvHistory;
    private HorizontalScrollView hsvDisplay, hsvHistory;
    private View displayArea;
    private final StringBuilder currentText = new StringBuilder("0");
    private boolean secondMode = false;

    // 🌟 核心调优：震动累加器
    private float rotaryAccumulator = 0f;

    // 🌟 修复 Bug 核心：标记上一步是不是刚刚按了 "="
    private boolean lastActionWasEqual = false;
    private float gestureDownX;
    private float gestureDownY;
    private boolean trackingDisplayGesture;
    private boolean trackingBottomGesture;
    private boolean navigationDragging;
    private boolean directionLocked;
    private StartupUpdateUiController startupUpdateUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppAppearance.applyToViews(findViewById(android.R.id.content));

        tvDisplay = findViewById(R.id.tvDisplay);
        tvHistory = findViewById(R.id.tvHistory);
        hsvDisplay = findViewById(R.id.hsvDisplay);
        hsvHistory = findViewById(R.id.hsvHistory);
        displayArea = findViewById(R.id.displayArea);

        // 按钮逻辑
        View.OnClickListener listener = v -> {
            String btnText = ((Button) v).getText().toString();

            // 错误状态恢复
            if (currentText.toString().equals("Error") && !btnText.equals("C")) {
                currentText.setLength(0); currentText.append("0");
            }

            // 🌟 核心状态机修复：处理计算完成后的后续输入
            if (lastActionWasEqual && !btnText.equals("C") && !btnText.equals("=")) {
                if (btnText.matches("[+\\-×÷]")) {
                    // 如果刚算完就按运算符：保留结果，但必须斩断 "..." 尾巴
                    if (currentText.toString().endsWith("...")) {
                        currentText.setLength(currentText.length() - 3);
                    }
                } else if (btnText.matches("[0-9]")) {
                    // 如果刚算完就按数字：清空屏幕，开始全新计算
                    currentText.setLength(0);
                } else if (btnText.equals(".")) {
                    // 如果刚算完按小数点：以 "0." 开始全新计算
                    currentText.setLength(0);
                    currentText.append("0");
                } else if (startsNewScientificExpression(btnText)) {
                    currentText.setLength(0);
                    currentText.append("0");
                }
                lastActionWasEqual = false; // 状态解除
            }

            switch (btnText) {
                case "C":
                    currentText.setLength(0); currentText.append("0"); tvHistory.setText("");
                    lastActionWasEqual = false;
                    break;
                case "⌫":
                    if (lastActionWasEqual) {
                        // 刚算完按退格，直接清零重来最符合直觉
                        currentText.setLength(0); currentText.append("0");
                        lastActionWasEqual = false;
                    } else {
                        if (currentText.length() > 1) currentText.setLength(currentText.length() - 1);
                        else { currentText.setLength(0); currentText.append("0"); }
                    }
                    break;
                case "=":
                    if (!lastActionWasEqual) { // 防止疯狂连按等号
                        handleCalculation();
                        lastActionWasEqual = true; // 标记计算完成
                    }
                    break;
                case ".":
                    handleDot();
                    break;
                case "%":
                    currentText.append("%");
                    break;
                case "2nd":
                    secondMode = !secondMode;
                    updateTrigLabels();
                    break;
                default:
                    handleScientificOrBasicInput(btnText);
                    break;
            }
            if (!btnText.equals("=") && !btnText.equals("C") && !btnText.equals("2nd")) {
                lastActionWasEqual = false;
            }
            tvDisplay.setText(currentText.toString());
            hsvDisplay.post(() -> hsvDisplay.fullScroll(View.FOCUS_RIGHT));
        };

        // 注册所有按钮
        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnAdd, R.id.btnSub, R.id.btnMul,
                R.id.btnDiv, R.id.btnDot, R.id.btnEq, R.id.btnAc, R.id.btnDel, R.id.btnPercent};
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }

        int[] scientificIds = {R.id.btnSin, R.id.btnCos, R.id.btnTan, R.id.btnSecond, R.id.btnPower,
                R.id.btnLg, R.id.btnLn, R.id.btnLog, R.id.btnLeftParen, R.id.btnRightParen,
                R.id.btnSqrt, R.id.btnFactorial, R.id.btnPi, R.id.btnE, R.id.btnScientificAc,
                R.id.btnScientificDel, R.id.btnScientificEq, R.id.btnScientificDiv,
                R.id.btnScientificMul};
        for (int id : scientificIds) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }

        // 表冠旋转逻辑
        hsvDisplay.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {

                int axis = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        MotionEvent.AXIS_SCROLL : MotionEvent.AXIS_VSCROLL;

                float delta = -event.getAxisValue(axis);

                if (delta != 0) {
                    v.scrollBy(Math.round(delta * 80), 0);
                    rotaryAccumulator += delta;

                    if (Math.abs(rotaryAccumulator) >= 0.6f) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            v.performHapticFeedback(18);
                        } else {
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        rotaryAccumulator = 0f;
                    }
                }
                return true;
            }
            return false;
        });

        hsvDisplay.setFocusable(true);
        hsvDisplay.requestFocus();
        if (savedInstanceState != null) {
            currentText.setLength(0);
            currentText.append(savedInstanceState.getString("expression", "0"));
            tvDisplay.setText(currentText.toString());
            tvHistory.setText(savedInstanceState.getString("equation", ""));
            secondMode = savedInstanceState.getBoolean("secondMode");
            lastActionWasEqual = savedInstanceState.getBoolean("lastActionWasEqual");
            updateTrigLabels();
            SwipeLayout pager = findViewById(R.id.buttonArea);
            pager.setCurrentPage(savedInstanceState.getInt("buttonPage"));
            hsvDisplay.post(() -> hsvDisplay.scrollTo(savedInstanceState.getInt("displayScroll"), 0));
            hsvHistory.post(() -> hsvHistory.scrollTo(savedInstanceState.getInt("historyScroll"), 0));
        }
        StartupUpdateViewModel updates = new ViewModelProvider(this).get(StartupUpdateViewModel.class);
        startupUpdateUi = new StartupUpdateUiController(this, updates);
        ((ViewGroup) findViewById(android.R.id.content)).addView(startupUpdateUi.getView(),
                new ViewGroup.LayoutParams(-1, -1));
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            updates.checkOnce(this, version);
        } catch (PackageManager.NameNotFoundException ignored) {
            // 自动检查不影响计算器启动。
        }
    }

    @Override protected void onStop() {
        if (startupUpdateUi != null) startupUpdateUi.dismissToast();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("expression", currentText.toString());
        state.putString("equation", tvHistory.getText().toString());
        state.putBoolean("secondMode", secondMode);
        state.putBoolean("lastActionWasEqual", lastActionWasEqual);
        state.putInt("buttonPage", ((SwipeLayout) findViewById(R.id.buttonArea)).getCurrentPage());
        state.putInt("displayScroll", hsvDisplay.getScrollX());
        state.putInt("historyScroll", hsvHistory.getScrollX());
    }

    private void handleCalculation() {
        String equation = currentText.toString();
        try {
            String expr = equation.replace("×", "*").replace("÷", "/");
            BigDecimal res = eval(expr);
            String result = formatResult(res);
            tvHistory.setText(String.format("%s=", equation));
            hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
            currentText.setLength(0); currentText.append(result);
            HistoryStore.add(this, equation, result);
        } catch (Exception e) {
            currentText.setLength(0); currentText.append("Error");
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (displayArea != null) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                int[] location = new int[2];
                displayArea.getLocationOnScreen(location);
                trackingDisplayGesture = event.getRawX() >= location[0]
                        && event.getRawX() <= location[0] + displayArea.getWidth()
                        && event.getRawY() >= location[1]
                        && event.getRawY() <= location[1] + displayArea.getHeight();
                gestureDownX = event.getRawX();
                gestureDownY = event.getRawY();
                View root = getWindow().getDecorView();
                root.getLocationOnScreen(location);
                trackingBottomGesture = gestureDownY >= location[1] + root.getHeight() - dp(24);
                navigationDragging = false;
                directionLocked = false;
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                directionLocked = true;
                trackingBottomGesture = false;
                trackingDisplayGesture = false;
            } else if (action == MotionEvent.ACTION_MOVE && !directionLocked) {
                float dx = event.getRawX() - gestureDownX;
                float dy = event.getRawY() - gestureDownY;
                int slop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (Math.max(Math.abs(dx), Math.abs(dy)) > slop) {
                    directionLocked = true;
                    navigationDragging = Math.abs(dy) > Math.abs(dx)
                            && ((trackingDisplayGesture && dy > 0) || (trackingBottomGesture && dy < 0));
                    if (navigationDragging) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                    }
                }
            }
            if (navigationDragging) {
                if (action == MotionEvent.ACTION_UP) {
                    float dy = event.getRawY() - gestureDownY;
                    if (Math.abs(dy) > dp(32)) {
                        startActivity(new Intent(this,
                                dy > 0 ? HistoryActivity.class : SettingsActivity.class));
                        if (Build.VERSION.SDK_INT < 34) {
                            overridePendingTransition(dy > 0 ? R.anim.history_enter : R.anim.settings_enter,
                                    R.anim.page_hold);
                        }
                    }
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    navigationDragging = false;
                }
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void handleDot() {
        String text = currentText.toString();
        if (text.length() > 0 && !isOperator(text.substring(text.length() - 1))) {
            int lastOp = -1;
            for (String op : new String[]{"+", "-", "×", "÷"}) lastOp = Math.max(lastOp, text.lastIndexOf(op));
            if (!text.substring(lastOp + 1).contains(".")) currentText.append(".");
        }
    }

    private void handleNumberAndOperator(String btn) {
        if (currentText.toString().equals("0") && !isOperator(btn)) currentText.setLength(0);
        if (isOperator(btn) && currentText.length() > 0) {
            char last = currentText.charAt(currentText.length() - 1);
            if (isOperator(String.valueOf(last)) || last == '.') currentText.setLength(currentText.length() - 1);
        }
        currentText.append(btn);
    }

    private void handleScientificOrBasicInput(String btn) {
        if (btn.equals("sin") || btn.equals("cos") || btn.equals("tan") ||
                btn.equals("sin⁻¹") || btn.equals("cos⁻¹") || btn.equals("tan⁻¹")) {
            replaceLeadingZero();
            boolean inverse = btn.endsWith("⁻¹");
            String function = btn.startsWith("sin") ? "sin" : btn.startsWith("cos") ? "cos" : "tan";
            currentText.append(inverse ? "a" + function + "(" : function + "(");
            return;
        }
        switch (btn) {
            case "xʸ":
                handleNumberAndOperator("^");
                break;
            case "lg":
                replaceLeadingZero(); currentText.append("lg(");
                break;
            case "ln":
                replaceLeadingZero(); currentText.append("ln(");
                break;
            case "log":
                replaceLeadingZero(); currentText.append("log(");
                break;
            case "√":
                replaceLeadingZero(); currentText.append("sqrt(");
                break;
            case "π":
                replaceLeadingZero(); currentText.append("pi");
                break;
            case "e":
                replaceLeadingZero(); currentText.append("e");
                break;
            case "(":
                replaceLeadingZero(); currentText.append("(");
                break;
            case ")":
                currentText.append(")");
                break;
            case "!":
                currentText.append("!");
                break;
            default:
                handleNumberAndOperator(btn);
                break;
        }
    }

    private void replaceLeadingZero() {
        if (currentText.toString().equals("0")) currentText.setLength(0);
    }

    private boolean startsNewScientificExpression(String btn) {
        return btn.equals("sin") || btn.equals("cos") || btn.equals("tan") ||
                btn.endsWith("⁻¹") || btn.equals("lg") || btn.equals("ln") ||
                btn.equals("log") || btn.equals("√") || btn.equals("π") ||
                btn.equals("e") || btn.equals("(");
    }

    private void updateTrigLabels() {
        ((Button) findViewById(R.id.btnSin)).setText(secondMode ? "sin⁻¹" : "sin");
        ((Button) findViewById(R.id.btnCos)).setText(secondMode ? "cos⁻¹" : "cos");
        ((Button) findViewById(R.id.btnTan)).setText(secondMode ? "tan⁻¹" : "tan");
        findViewById(R.id.btnSecond).setSelected(secondMode);
    }

    private boolean isOperator(String s) { return s.matches("[+\\-×÷^]"); }

    private BigDecimal eval(String str) {
        return new ExpressionParser(str).parse();
    }

    private static final class ExpressionParser {
        private final String source;
        private int pos;

        ExpressionParser(String source) {
            this.source = source;
        }

        BigDecimal parse() {
            BigDecimal result = expression();
            skipSpaces();
            if (pos != source.length()) throw new IllegalArgumentException("Unexpected token");
            return result;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                if (eat('+')) value = value.add(term());
                else if (eat('-')) value = value.subtract(term());
                else return value;
            }
        }

        private BigDecimal term() {
            BigDecimal value = power();
            while (true) {
                if (eat('*')) value = value.multiply(power());
                else if (eat('/')) value = value.divide(power(), 16, RoundingMode.HALF_UP);
                else return value;
            }
        }

        private BigDecimal power() {
            BigDecimal value = unary();
            if (eat('^')) value = BigDecimal.valueOf(Math.pow(value.doubleValue(), power().doubleValue()));
            return value;
        }

        private BigDecimal unary() {
            if (eat('+')) return unary();
            if (eat('-')) return unary().negate();
            BigDecimal value = primary();
            while (true) {
                if (eat('!')) value = factorial(value);
                else if (eat('%')) value = value.movePointLeft(2);
                else break;
            }
            return value;
        }

        private BigDecimal primary() {
            skipSpaces();
            if (eat('(')) {
                BigDecimal value = expression();
                if (!eat(')')) throw new IllegalArgumentException("Missing )");
                return value;
            }
            if (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
                int start = pos;
                while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) pos++;
                return new BigDecimal(source.substring(start, pos));
            }
            if (pos < source.length() && Character.isLetter(source.charAt(pos))) {
                int start = pos;
                while (pos < source.length() && Character.isLetter(source.charAt(pos))) pos++;
                String name = source.substring(start, pos);
                if (name.equals("pi")) return BigDecimal.valueOf(Math.PI);
                if (name.equals("e")) return BigDecimal.valueOf(Math.E);
                if (!eat('(')) throw new IllegalArgumentException("Missing function argument");
                BigDecimal argument = expression();
                if (!eat(')')) throw new IllegalArgumentException("Missing )");
                return applyFunction(name, argument);
            }
            throw new IllegalArgumentException("Expected value");
        }

        private BigDecimal applyFunction(String name, BigDecimal argument) {
            double value = argument.doubleValue();
            double result;
            switch (name) {
                case "sin": result = Math.sin(value); break;
                case "cos": result = Math.cos(value); break;
                case "tan": result = Math.tan(value); break;
                case "asin": result = Math.asin(value); break;
                case "acos": result = Math.acos(value); break;
                case "atan": result = Math.atan(value); break;
                case "sqrt": result = Math.sqrt(value); break;
                case "lg":
                case "log": result = Math.log10(value); break;
                case "ln": result = Math.log(value); break;
                default: throw new IllegalArgumentException("Unknown function");
            }
            if (!Double.isFinite(result)) throw new ArithmeticException("Invalid result");
            return BigDecimal.valueOf(result);
        }

        private BigDecimal factorial(BigDecimal value) {
            if (value.signum() < 0 || value.scale() > 0 || value.compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new ArithmeticException("Invalid factorial");
            }
            int n = value.intValueExact();
            BigDecimal result = BigDecimal.ONE;
            for (int i = 2; i <= n; i++) result = result.multiply(BigDecimal.valueOf(i));
            return result;
        }

        private boolean eat(char expected) {
            skipSpaces();
            if (pos < source.length() && source.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
        }
    }

    private String formatResult(BigDecimal bd) {
        bd = bd.stripTrailingZeros();
        return bd.scale() > 8 ? bd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "..." : bd.toPlainString();
    }
}
