package com.nickwoluff.wearcalculator;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends WearActivity {

    private static final String TAG = "WearCalc";
    private TextView tvDisplay, tvHistory;
    private HorizontalScrollView hsvDisplay, hsvHistory;
    private View displayArea;
    private final StringBuilder currentText = new StringBuilder("0");
    private boolean secondMode = false;
    private boolean degreeMode = false;

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

        hsvDisplay.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL
                    && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                int axis = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? MotionEvent.AXIS_SCROLL : MotionEvent.AXIS_VSCROLL;
                v.scrollBy(Math.round(-event.getAxisValue(axis) * 80), 0);
                return true;
            }
            return false;
        });

        // 按钮逻辑
        View.OnClickListener listener = v -> {
            String btnText = ((Button) v).getText().toString();

            // 错误状态恢复
            if (currentText.toString().equals("Error") && !btnText.equals("C")) {
                currentText.setLength(0); currentText.append("0");
            }

            // 🌟 核心状态机修复：处理计算完成后的后续输入
            if (lastActionWasEqual && !btnText.equals("C") && !btnText.equals("=") && !btnText.equals("⌫")
                    && !btnText.equals("rad") && !btnText.equals("deg")) {
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
                case "rad":
                case "deg":
                    degreeMode = !degreeMode;
                    if (!degreeMode) secondMode = false;
                    updateTrigLabels();
                    break;
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
                        handleBackspace();
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
                    if (!degreeMode) break;
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
            refreshDisplay();
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
                R.id.btnCbrt, R.id.btnComma, R.id.btnLog, R.id.btnLeftParen, R.id.btnRightParen,
                R.id.btnSqrt, R.id.btnFactorial, R.id.btnPi, R.id.btnE, R.id.btnScientificAc,
                R.id.btnScientificDel, R.id.btnScientificEq, R.id.btnScientificDiv,
                R.id.btnScientificMul};
        for (int id : scientificIds) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }
        updateTrigLabels();

        // 表冠旋转逻辑
        hsvDisplay.setFocusable(true);
        hsvDisplay.requestFocus();
        if (savedInstanceState != null) {
            currentText.setLength(0);
            currentText.append(savedInstanceState.getString("expression", "0"));
            tvHistory.setText(savedInstanceState.getString("equation", ""));
            secondMode = savedInstanceState.getBoolean("secondMode");
            degreeMode = savedInstanceState.getBoolean("degreeMode");
            if (!degreeMode) secondMode = false;
            lastActionWasEqual = savedInstanceState.getBoolean("lastActionWasEqual");
            updateTrigLabels();
            refreshDisplay();
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
        state.putBoolean("degreeMode", degreeMode);
        state.putBoolean("lastActionWasEqual", lastActionWasEqual);
        state.putInt("buttonPage", ((SwipeLayout) findViewById(R.id.buttonArea)).getCurrentPage());
        state.putInt("displayScroll", hsvDisplay.getScrollX());
        state.putInt("historyScroll", hsvHistory.getScrollX());
    }

    private void handleCalculation() {
        String equation = closeUnclosedParentheses(currentText.toString());
        currentText.setLength(0);
        currentText.append(equation);
        try {
            String expr = equation.replace("×", "*").replace("÷", "/");
            BigDecimal res = eval(expr, degreeMode);
            String result = formatResult(res);
            String displayEquation = degreeMode ? formatDegreeDisplay(equation) : equation;
            tvHistory.setText(String.format("%s=", displayEquation));
            hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
            currentText.setLength(0); currentText.append(result);
            HistoryStore.add(this, displayEquation, result);
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
                View root = getWindow().getDecorView();
                root.getLocationOnScreen(location);
                int rootLeft = location[0];
                int rootTop = location[1];
                int rootRight = rootLeft + root.getWidth();
                int rootBottom = rootTop + root.getHeight();
                View buttonArea = findViewById(R.id.buttonArea);
                buttonArea.getLocationOnScreen(location);
                // 结果区及其上方的整个区域都可以下滑打开历史记录。
                trackingDisplayGesture = event.getRawX() >= rootLeft
                        && event.getRawX() <= rootRight
                        && event.getRawY() >= rootTop
                        && event.getRawY() < location[1]
                        && event.getRawY() <= rootBottom;
                gestureDownX = event.getRawX();
                gestureDownY = event.getRawY();
                trackingBottomGesture = gestureDownY >= rootBottom - dp(24);
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
        if (text.isEmpty()) {
            currentText.append("0.");
            return;
        }
        char last = text.charAt(text.length() - 1);
        if (last == '(' || last == ',' || isOperator(String.valueOf(last))) {
            currentText.append("0.");
            return;
        }
        if (!Character.isDigit(last) && last != '.') return;
        // 小数点只检查当前数字，逗号、括号及乘方都可以开始新的数字。
        int start = text.length();
        while (start > 0 && (Character.isDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '.')) start--;
        if (!text.substring(start).contains(".")) currentText.append(".");
    }

    private void handleBackspace() {
        String text = currentText.toString();
        // 函数名和自动插入的左括号属于一次输入，退格时一并移除。
        for (String token : new String[]{"sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "³√(", "√(",
                "sin(", "cos(", "tan(", "log(", "asin(", "acos(", "atan(", "sqrt(", "cbrt(", "π"}) {
            if (text.endsWith(token)) {
                currentText.setLength(text.length() - token.length());
                if (currentText.length() == 0) currentText.append("0");
                return;
            }
        }
        if (!text.isEmpty()) currentText.setLength(text.offsetByCodePoints(text.length(), -1));
        if (currentText.length() == 0) currentText.append("0");
    }

    static String closeUnclosedParentheses(String expression) {
        int balance = 0;
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) == '(') balance++;
            else if (expression.charAt(i) == ')' && balance > 0) balance--;
        }
        if (balance == 0) return expression;
        StringBuilder completed = new StringBuilder(expression);
        for (int i = 0; i < balance; i++) completed.append(')');
        return completed.toString();
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
            currentText.append(btn).append('(');
            return;
        }
        switch (btn) {
            case "xʸ":
                handleNumberAndOperator("^");
                break;
            case "log":
                replaceLeadingZero(); currentText.append("log(");
                break;
            case "√":
                replaceLeadingZero(); currentText.append("√(");
                break;
            case "³√":
                replaceLeadingZero(); currentText.append("³√(");
                break;
            case ",":
                currentText.append(",");
                break;
            case "π":
                replaceLeadingZero(); currentText.append("π");
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
                btn.endsWith("⁻¹") || btn.equals("log") || btn.equals("√") ||
                btn.equals("³√") || btn.equals("π") ||
                btn.equals("e") || btn.equals("(");
    }

    private void updateTrigLabels() {
        ((Button) findViewById(R.id.btnSin)).setText(secondMode ? "sin⁻¹" : "sin");
        ((Button) findViewById(R.id.btnCos)).setText(secondMode ? "cos⁻¹" : "cos");
        ((Button) findViewById(R.id.btnTan)).setText(secondMode ? "tan⁻¹" : "tan");
        Button second = findViewById(R.id.btnSecond);
        second.setSelected(secondMode);
        second.setEnabled(degreeMode);
        second.setAlpha(degreeMode ? 1f : 0.45f);
        Button angle = findViewById(R.id.btnScientificAc);
        angle.setText(degreeMode ? "deg" : "rad");
        angle.setContentDescription(degreeMode ? "切换到弧度模式" : "切换到角度模式");
    }

    private void refreshDisplay() {
        tvDisplay.setText(degreeMode ? formatDegreeDisplay(currentText.toString()) : currentText.toString());
    }

    private String formatDegreeDisplay(String expression) {
        StringBuilder result = new StringBuilder();
        java.util.ArrayDeque<Integer> opens = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Boolean> trig = new java.util.ArrayDeque<>();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                String name = trigNameBefore(expression, i);
                opens.push(i);
                // 正向三角函数的输入是角度；反三角函数的输入是无量纲数。
                trig.push(isDegreeTrigName(name));
                result.append(c);
            } else if (c == ')') {
                int open = opens.isEmpty() ? -1 : opens.pop();
                boolean isTrig = !trig.isEmpty() && trig.pop();
                String inside = open >= 0 ? expression.substring(open + 1, i) : "";
                if (isTrig && hasNumericTail(inside)) result.append('°');
                result.append(c);
            } else if (isOperator(String.valueOf(c)) && !opens.isEmpty() && trig.peek()) {
                String inside = expression.substring(opens.peek() + 1, i);
                if (isAngleNumber(inside)) result.append('°');
                result.append(c);
            } else {
                result.append(c);
            }
        }
        if (hasOpenTrigWithDigit(expression)) result.append('°');
        return result.toString();
    }

    private boolean hasNumericTail(String value) {
        String trimmed = value.trim();
        int end = trimmed.length();
        int start = end;
        while (start > 0 && (Character.isDigit(trimmed.charAt(start - 1))
                || trimmed.charAt(start - 1) == '.')) start--;
        if (start == end) return false;
        if (start > 0 && (trimmed.charAt(start - 1) == '+' || trimmed.charAt(start - 1) == '-')) start--;
        return isAngleNumber(trimmed.substring(start, end));
    }

    private String trigNameBefore(String expression, int openIndex) {
        String prefix = expression.substring(0, openIndex);
        for (String name : new String[]{"sin⁻¹", "cos⁻¹", "tan⁻¹", "sin", "cos", "tan"}) {
            if (prefix.endsWith(name)) return name;
        }
        return "";
    }

    private boolean hasOpenTrigWithDigit(String expression) {
        java.util.ArrayDeque<Integer> starts = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Boolean> trigFunctions = new java.util.ArrayDeque<>();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                starts.push(i);
                trigFunctions.push(isDegreeTrigName(trigNameBefore(expression, i)));
            } else if (c == ')' && !starts.isEmpty()) {
                starts.pop();
                trigFunctions.pop();
            }
        }
        while (!starts.isEmpty()) {
            int start = starts.pop();
            boolean isTrig = trigFunctions.pop();
            if (isTrig && hasNumericTail(expression.substring(start + 1))) return true;
        }
        return false;
    }

    private boolean isDegreeTrigName(String name) {
        return name.equals("sin") || name.equals("cos") || name.equals("tan");
    }

    private boolean isAngleNumber(String value) {
        String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    }

    private boolean isOperator(String s) { return s.matches("[+\\-×÷^]"); }

    private BigDecimal eval(String str) {
        return eval(str, degreeMode);
    }

    private BigDecimal eval(String str, boolean degrees) {
        return new ExpressionParser(str, degrees).parse();
    }

    static final class ExpressionParser {
        private final String source;
        private final boolean degreeMode;
        private int pos;

        ExpressionParser(String source) {
            this(source, false);
        }

        ExpressionParser(String source, boolean degreeMode) {
            // 显示和历史保留数学符号，仅在计算时转为解析器使用的函数名。
            this.source = source.replace("³√", "cbrt").replace("√", "sqrt")
                    .replace("π", "pi").replace("sin⁻¹", "asin")
                    .replace("cos⁻¹", "acos").replace("tan⁻¹", "atan");
            this.degreeMode = degreeMode;
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
                else if (eat('/')) value = value.divide(power(), BigDecimalMath.MC);
                else return value;
            }
        }

        private BigDecimal power() {
            BigDecimal value = unary();
            if (eat('^')) value = exponentiate(value, power());
            return value;
        }

        private BigDecimal exponentiate(BigDecimal base, BigDecimal exponent) {
            BigDecimal normalized = exponent.stripTrailingZeros();
            if (normalized.scale() <= 0
                    && normalized.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0
                    && normalized.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0) {
                int integerExponent = normalized.intValueExact();
                if (integerExponent >= 0) return base.pow(integerExponent);
                if (base.signum() == 0) throw new ArithmeticException("Invalid power");
                return BigDecimal.ONE.divide(base.pow(-integerExponent), BigDecimalMath.MC);
            }
            if (base.signum() <= 0) throw new ArithmeticException("Invalid power");
            return BigDecimalMath.exp(exponent.multiply(BigDecimalMath.ln(base), BigDecimalMath.MC));
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
                if (name.equals("pi")) return BigDecimalMath.PI;
                if (name.equals("e")) return BigDecimalMath.E;
                if (!eat('(')) throw new IllegalArgumentException("Missing function argument");
                BigDecimal argument = expression();
                if (name.equals("log")) {
                    if (!eat(',')) throw new IllegalArgumentException("Log requires a base and an argument");
                    BigDecimal number = expression();
                    if (!eat(')')) throw new IllegalArgumentException("Missing )");
                    return logarithm(argument, number);
                }
                if (!eat(')')) throw new IllegalArgumentException("Missing )");
                return applyFunction(name, argument);
            }
            throw new IllegalArgumentException("Expected value");
        }

        private BigDecimal applyFunction(String name, BigDecimal argument) {
            switch (name) {
                case "sin": return BigDecimalMath.sin(toRadians(argument));
                case "cos": return BigDecimalMath.cos(toRadians(argument));
                case "tan": return BigDecimalMath.tan(toRadians(argument));
                case "asin": return fromRadians(BigDecimalMath.asin(argument));
                case "acos": return fromRadians(BigDecimalMath.acos(argument));
                case "atan": return fromRadians(BigDecimalMath.atan(argument));
                case "sqrt": return BigDecimalMath.sqrt(argument);
                case "cbrt": return BigDecimalMath.cbrt(argument);
                default: throw new IllegalArgumentException("Unknown function");
            }
        }

        private BigDecimal logarithm(BigDecimal base, BigDecimal number) {
            if (base.signum() <= 0 || base.compareTo(BigDecimal.ONE) == 0 || number.signum() <= 0) {
                throw new ArithmeticException("Invalid logarithm domain");
            }
            return BigDecimalMath.ln(number).divide(BigDecimalMath.ln(base), BigDecimalMath.MC);
        }

        private BigDecimal toRadians(BigDecimal value) {
            return degreeMode
                    ? value.multiply(BigDecimalMath.PI, BigDecimalMath.MC)
                        .divide(BigDecimalMath.ONE_EIGHTY, BigDecimalMath.MC)
                    : value;
        }

        private BigDecimal fromRadians(BigDecimal value) {
            return degreeMode
                    ? value.multiply(BigDecimalMath.ONE_EIGHTY, BigDecimalMath.MC)
                        .divide(BigDecimalMath.PI, BigDecimalMath.MC)
                    : value;
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

        /** 只使用 BigDecimal 的高精度数学函数，避免把计算值转换为 double。 */
        private static final class BigDecimalMath {
            private static final MathContext MC = new MathContext(50, RoundingMode.HALF_UP);
            private static final BigDecimal EPSILON = BigDecimal.ONE.scaleByPowerOfTen(-48);
            private static final BigDecimal ONE = BigDecimal.ONE;
            private static final BigDecimal TWO = BigDecimal.valueOf(2);
            private static final BigDecimal THREE = BigDecimal.valueOf(3);
            private static final BigDecimal ONE_EIGHTY = BigDecimal.valueOf(180);
            private static final BigDecimal HALF_PI = new BigDecimal(
                    "1.5707963267948966192313216916397514420985846996875", MC);
            private static final BigDecimal PI = new BigDecimal(
                    "3.1415926535897932384626433832795028841971693993751", MC);
            private static final BigDecimal E = new BigDecimal(
                    "2.7182818284590452353602874713526624977572470937000", MC);
            private static final BigDecimal LN_TWO = new BigDecimal(
                    "0.69314718055994530941723212145817656807550013436025", MC);

            private static BigDecimal normalize(BigDecimal value) {
                return value.round(MC);
            }

            private static BigDecimal sin(BigDecimal value) {
                BigDecimal x = value.remainder(PI.multiply(TWO, MC), MC);
                if (x.compareTo(PI) > 0) x = x.subtract(PI.multiply(TWO, MC), MC);
                if (x.compareTo(PI.negate()) < 0) x = x.add(PI.multiply(TWO, MC), MC);
                BigDecimal xSquared = x.multiply(x, MC);
                BigDecimal term = x;
                BigDecimal sum = x;
                for (int n = 1; n < 200; n++) {
                    BigDecimal denominator = BigDecimal.valueOf((long) (2 * n) * (2 * n + 1));
                    term = term.multiply(xSquared, MC).divide(denominator, MC).negate();
                    sum = sum.add(term, MC);
                    if (term.abs().compareTo(EPSILON) < 0) break;
                }
                return normalizeTrig(sum);
            }

            private static BigDecimal cos(BigDecimal value) {
                BigDecimal x = value.remainder(PI.multiply(TWO, MC), MC);
                if (x.compareTo(PI) > 0) x = x.subtract(PI.multiply(TWO, MC), MC);
                if (x.compareTo(PI.negate()) < 0) x = x.add(PI.multiply(TWO, MC), MC);
                BigDecimal xSquared = x.multiply(x, MC);
                BigDecimal term = ONE;
                BigDecimal sum = ONE;
                for (int n = 1; n < 200; n++) {
                    BigDecimal denominator = BigDecimal.valueOf((long) (2 * n - 1) * (2 * n));
                    term = term.multiply(xSquared, MC).divide(denominator, MC).negate();
                    sum = sum.add(term, MC);
                    if (term.abs().compareTo(EPSILON) < 0) break;
                }
                return normalizeTrig(sum);
            }

            private static BigDecimal tan(BigDecimal value) {
                BigDecimal cosine = cos(value);
                if (cosine.abs().compareTo(EPSILON) < 0) throw new ArithmeticException("Invalid result");
                return normalizeTrig(sin(value).divide(cosine, MC));
            }

            /**
             * 将三角级数产生的极小残差收敛到常见精确值，避免例如 cos(60°)
             * 因有限级数显示为 0.499999999999...。整个过程仍使用 BigDecimal。
             */
            private static BigDecimal normalizeTrig(BigDecimal value) {
                BigDecimal normalized = normalize(value);
                BigDecimal[] exact = {ZERO, ONE, ONE.negate(), HALF, HALF.negate()};
                for (BigDecimal candidate : exact) {
                    if (normalized.subtract(candidate, MC).abs().compareTo(EPSILON) < 0) {
                        return candidate;
                    }
                }
                return normalized;
            }

            private static BigDecimal atan(BigDecimal value) {
                if (value.signum() == 0) return ZERO;
                boolean negative = value.signum() < 0;
                BigDecimal x = value.abs();
                BigDecimal result;
                if (x.compareTo(ONE) > 0) {
                    result = HALF_PI.subtract(atan(ONE.divide(x, MC)), MC);
                } else if (x.compareTo(new BigDecimal("0.5")) > 0) {
                    result = new BigDecimal("0.78539816339744830961566084581987572104929234984378", MC)
                            .add(atan(x.subtract(ONE, MC).divide(x.add(ONE, MC), MC)), MC);
                } else {
                    BigDecimal xSquared = x.multiply(x, MC);
                    BigDecimal term = x;
                    BigDecimal sum = x;
                    for (int n = 1; n < 500; n++) {
                        term = term.multiply(xSquared, MC).negate();
                        BigDecimal add = term.divide(BigDecimal.valueOf(2L * n + 1), MC);
                        sum = sum.add(add, MC);
                        if (add.abs().compareTo(EPSILON) < 0) break;
                    }
                    result = sum;
                }
                return negative ? result.negate() : result;
            }

            private static BigDecimal asin(BigDecimal value) {
                if (value.abs().compareTo(ONE) > 0) throw new ArithmeticException("Invalid result");
                if (value.compareTo(ONE) == 0) return HALF_PI;
                if (value.compareTo(ONE.negate()) == 0) return HALF_PI.negate();
                BigDecimal root = sqrt(ONE.subtract(value.multiply(value, MC), MC));
                return atan(value.divide(root, MC));
            }

            private static BigDecimal acos(BigDecimal value) {
                return HALF_PI.subtract(asin(value), MC);
            }

            private static BigDecimal sqrt(BigDecimal value) {
                if (value.signum() < 0) throw new ArithmeticException("Invalid result");
                if (value.signum() == 0) return ZERO;
                BigDecimal guess = value.compareTo(ONE) > 0 ? value.divide(TWO, MC) : ONE;
                for (int i = 0; i < 150; i++) {
                    BigDecimal next = guess.add(value.divide(guess, MC), MC).divide(TWO, MC);
                    if (next.subtract(guess).abs().compareTo(EPSILON) < 0) return normalize(next);
                    guess = next;
                }
                return normalize(guess);
            }

            private static BigDecimal cbrt(BigDecimal value) {
                if (value.signum() == 0) return ZERO;
                boolean negative = value.signum() < 0;
                BigDecimal magnitude = value.abs();
                BigDecimal guess = magnitude.compareTo(ONE) > 0 ? magnitude.divide(THREE, MC) : ONE;
                for (int i = 0; i < 200; i++) {
                    BigDecimal squared = guess.multiply(guess, MC);
                    BigDecimal next = guess.multiply(TWO, MC)
                            .add(magnitude.divide(squared, MC), MC).divide(THREE, MC);
                    if (next.subtract(guess).abs().compareTo(EPSILON) < 0) {
                        return normalize(negative ? next.negate() : next);
                    }
                    guess = next;
                }
                return normalize(negative ? guess.negate() : guess);
            }

            private static BigDecimal ln(BigDecimal value) {
                if (value.signum() <= 0) throw new ArithmeticException("Invalid logarithm value");
                if (value.compareTo(ONE) == 0) return ZERO;
                BigDecimal x = value;
                int scale = 0;
                while (x.compareTo(TWO) > 0) { x = x.divide(TWO, MC); scale++; }
                while (x.compareTo(new BigDecimal("0.5")) < 0) { x = x.multiply(TWO, MC); scale--; }
                BigDecimal y = x.subtract(ONE, MC).divide(x.add(ONE, MC), MC);
                BigDecimal ySquared = y.multiply(y, MC);
                BigDecimal term = y;
                BigDecimal sum = y;
                for (int n = 1; n < 500; n++) {
                    term = term.multiply(ySquared, MC);
                    BigDecimal add = term.divide(BigDecimal.valueOf(2L * n + 1), MC);
                    sum = sum.add(add, MC);
                    if (add.abs().compareTo(EPSILON) < 0) break;
                }
                return normalize(sum.multiply(TWO, MC).add(LN_TWO.multiply(BigDecimal.valueOf(scale), MC), MC));
            }

            private static BigDecimal exp(BigDecimal value) {
                if (value.signum() == 0) return ONE;
                boolean negative = value.signum() < 0;
                BigDecimal x = value.abs();
                int halves = 0;
                while (x.compareTo(new BigDecimal("0.5")) > 0 && halves < 200) {
                    x = x.divide(TWO, MC);
                    halves++;
                }
                BigDecimal term = ONE;
                BigDecimal sum = ONE;
                for (int n = 1; n < 500; n++) {
                    term = term.multiply(x, MC).divide(BigDecimal.valueOf(n), MC);
                    sum = sum.add(term, MC);
                    if (term.abs().compareTo(EPSILON) < 0) break;
                }
                for (int i = 0; i < halves; i++) sum = sum.multiply(sum, MC);
                return normalize(negative ? ONE.divide(sum, MC) : sum);
            }

            private static final BigDecimal ZERO = BigDecimal.ZERO;
            private static final BigDecimal HALF = new BigDecimal("0.5");
        }
    }

    private String formatResult(BigDecimal bd) {
        bd = bd.stripTrailingZeros();
        int maximumScale = PrecisionPreferences.enabled(this) ? 16 : 8;
        if (bd.scale() <= maximumScale) return bd.toPlainString();
        BigDecimal rounded = bd.setScale(maximumScale, RoundingMode.HALF_UP).stripTrailingZeros();
        // 三角函数可能产生 0.49999999999999994 这样的浮点误差；若舍入后已落在
        // 更短的小数位，不再误报“还有未显示的小数”。
        String value = rounded.toPlainString();
        return rounded.scale() == maximumScale && rounded.compareTo(bd) != 0
                ? value + "..." : value;
    }
}
