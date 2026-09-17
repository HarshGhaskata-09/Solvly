package com.example.solvly.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MathSolver is a local, offline mathematical calculation engine.
 * It provides basic to intermediate mathematical solving capabilities without
 * requiring internet access.
 */
public class MathSolver {

    /**
     * Data class to store the result of a math problem solution.
     */
    public static class SolveResult {
        public String title; // Heading of the solved problem
        public String scannedText; // The input text after normalization
        public String answer; // The final answer calculated
        public List<String> steps; // Step-by-step solving procedure
        public boolean isSuccess; // True if the problem was solved successfully
        public String errorMessage; // Error description if solving failed

        public SolveResult() {
            this.steps = new ArrayList<>();
            this.isSuccess = false;
        }
    }

    /**
     * Primary entrypoint to solve a math problem offline.
     * Tries different sub-solvers in a logical order.
     *
     * @param rawText The raw text string containing the math problem.
     * @param isOcr   True if the text comes from OCR (image scan) to apply OCR
     *                cleanup.
     * @return SolveResult containing the solved steps and answer.
     */
    public static SolveResult solve(String rawText, boolean isOcr) {
        SolveResult result = new SolveResult();
        String cleanedText = normalize(rawText, isOcr);
        result.scannedText = cleanedText;

        if (cleanedText.isEmpty()) {
            result.errorMessage = "No text detected.";
            return result;
        }

        // 1. Try Unit Conversion (e.g., "10m to cm")
        if (solveUnitConversion(cleanedText, result)) {
            result.title = "Unit Conversion";
            return result;
        }

        // 2. Try Quadratic Equation (e.g., "x^2 - 5x + 6 = 0")
        if (solveQuadratic(cleanedText, result)) {
            result.title = "Quadratic Equation";
            return result;
        }

        // 3. Try Simultaneous Equations (e.g., "x+y=5, x-y=1")
        if (solveSimultaneous(cleanedText, result)) {
            result.title = "System of Equations";
            return result;
        }

        // 4. Try Linear Equation (e.g., "2x + 5 = 15")
        if (solveLinearEquation(cleanedText, result)) {
            result.title = "Linear Equation";
            return result;
        }

        // 5. Try Calculus Derivatives & Integrals (e.g., "d/dx x^2")
        if (solveCalculus(cleanedText, result)) {
            result.title = "Calculus";
            return result;
        }

        // 6. Try Trigonometry and Scientific Functions (e.g., "sin(30)", "sqrt(16)",
        // "5^3")
        if (solveScientific(cleanedText, result)) {
            result.title = "Scientific Math";
            return result;
        }

        // 7. Fallback to Basic Arithmetic using PEMDAS (e.g., "12 * (3 + 4)")
        if (solveBasicArithmetic(cleanedText, result)) {
            result.title = "Arithmetic Calculation";
            return result;
        }

        result.errorMessage = "I couldn't identify this problem yet. Try a simpler format!";
        return result;
    }

    /**
     * Parser and solver for unit conversion problems.
     */
    private static boolean solveUnitConversion(String text, SolveResult result) {
        Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(m|cm|km|kg|g|ml|l)\\s*to\\s*(m|cm|km|kg|g|ml|l)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1));
                String from = matcher.group(3).toLowerCase();
                String to = matcher.group(4).toLowerCase();
                Double converted = convertUnit(val, from, to);
                if (converted == null)
                    return false;
                double factor = unitToBase(from) / unitToBase(to);
                result.answer = formatResult(converted) + " " + to;
                result.isSuccess = true;
                result.steps.add("Unit Conversion: Convert " + from + " to " + to);
                result.steps.add("Conversion Factor: 1 " + from + " = " + formatResult(factor) + " " + to);
                result.steps.add("Calculation: " + val + " * " + formatResult(factor) + " = " + result.answer);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Parser and solver for linear equations in the formats:
     * - 'ax + b = c' / 'ax - b = c' (e.g. 2x + 5 = 15, 1/2x - 3.5 = 12)
     * - 'b + ax = c' / 'b - ax = c' (e.g. 12 + 2x = 16, 3/4 - 1.5x = 5.2)
     * - 'ax = c' (e.g. 2x = 10, x/2 = 4)
     */
    private static boolean solveLinearEquation(String text, SolveResult result) {
        // Preprocess variable division:
        // 1. "x/2" -> "1/2 x", "-x/3" -> "-1/3 x", "+x/4" -> "+1/4 x"
        text = text.replaceAll("([+-]?)\\s*x\\s*/\\s*(\\d+(?:\\.\\d+)?)", "$1 1/$2 x");
        // 2. "2x/5" -> "2/5 x", "-3.5x/2" -> "-3.5/2 x"
        text = text.replaceAll("([+-]?\\s*\\d+(?:\\.\\d+)?)\\s*x\\s*/\\s*(\\d+(?:\\.\\d+)?)", "$1/$2 x");

        String numRegex = "(-?\\d+(?:\\.\\d+)?(?:\\s*/\\s*\\d+(?:\\.\\d+)?)?)";
        String coefRegex = "(-?\\d*(?:\\.\\d+)?(?:\\s*/\\s*\\d+(?:\\.\\d+)?)?)";

        // Format 1: ax + b = c or ax - b = c
        Pattern pattern1 = Pattern.compile(coefRegex + "\\s*x\\s*([+\\-])\\s*" + numRegex + "\\s*=\\s*" + numRegex);
        Matcher matcher1 = pattern1.matcher(text);

        if (matcher1.find()) {
            try {
                double a = parseValue(matcher1.group(1), 1.0);
                if (a == 0)
                    return false;

                String op = matcher1.group(2);
                double b = parseValue(matcher1.group(3), 0.0);
                double c = parseValue(matcher1.group(4), 0.0);

                double x;
                if (op.equals("+")) {
                    x = (c - b) / a;
                    result.steps
                            .add("Equation: " + formatResult(a) + "x + " + formatResult(b) + " = " + formatResult(c));
                    result.steps.add("Step 1: Subtract " + formatResult(b) + " from both sides:");
                    result.steps.add(formatResult(a) + "x = " + formatResult(c - b));
                } else {
                    x = (c + b) / a;
                    result.steps
                            .add("Equation: " + formatResult(a) + "x - " + formatResult(b) + " = " + formatResult(c));
                    result.steps.add("Step 1: Add " + formatResult(b) + " to both sides:");
                    result.steps.add(formatResult(a) + "x = " + formatResult(c + b));
                }

                if (a != 1.0) {
                    result.steps.add("Step 2: Divide both sides by " + formatResult(a) + ":");
                }
                result.answer = "x = " + formatResult(x);
                result.steps.add("Final Result: " + result.answer);
                result.isSuccess = true;
                return true;
            } catch (Exception ignored) {
            }
        }

        // Format 2: b + ax = c or b - ax = c
        Pattern pattern2 = Pattern.compile(numRegex + "\\s*([+\\-])\\s*" + coefRegex + "\\s*x\\s*=\\s*" + numRegex);
        Matcher matcher2 = pattern2.matcher(text);

        if (matcher2.find()) {
            try {
                double b = parseValue(matcher2.group(1), 0.0);
                String op = matcher2.group(2);
                double a = parseValue(matcher2.group(3), 1.0);
                if (op.equals("-"))
                    a = -a;
                if (a == 0)
                    return false;

                double c = parseValue(matcher2.group(4), 0.0);

                double x = (c - b) / a;
                result.steps.add("Equation: " + formatResult(b) + " " + op + " " + formatResult(Math.abs(a)) + "x = "
                        + formatResult(c));
                result.steps.add("Step 1: Subtract " + formatResult(b) + " from both sides:");
                result.steps.add(formatResult(a) + "x = " + formatResult(c - b));

                if (a != 1.0) {
                    result.steps.add("Step 2: Divide both sides by " + formatResult(a) + ":");
                }
                result.answer = "x = " + formatResult(x);
                result.steps.add("Final Result: " + result.answer);
                result.isSuccess = true;
                return true;
            } catch (Exception ignored) {
            }
        }

        // Format 3: ax = c
        Pattern pattern3 = Pattern.compile(coefRegex + "\\s*x\\s*=\\s*" + numRegex);
        Matcher matcher3 = pattern3.matcher(text);

        if (matcher3.find()) {
            try {
                double a = parseValue(matcher3.group(1), 1.0);
                if (a == 0)
                    return false;
                double c = parseValue(matcher3.group(2), 0.0);

                double x = c / a;
                result.steps.add("Equation: " + formatResult(a) + "x = " + formatResult(c));
                if (a != 1.0) {
                    result.steps.add("Step 1: Divide both sides by " + formatResult(a) + ":");
                }
                result.answer = "x = " + formatResult(x);
                result.steps.add("Final Result: " + result.answer);
                result.isSuccess = true;
                return true;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private static double parseValue(String valStr, double defaultVal) {
        if (valStr == null || valStr.trim().isEmpty())
            return defaultVal;
        String clean = valStr.replaceAll("\\s+", "");
        if (clean.equals("+"))
            return 1.0;
        if (clean.equals("-"))
            return -1.0;
        if (clean.contains("/")) {
            String[] parts = clean.split("/");
            double numerator = parts[0].isEmpty() ? 1.0 : Double.parseDouble(parts[0]);
            if (parts[0].equals("+"))
                numerator = 1.0;
            if (parts[0].equals("-"))
                numerator = -1.0;
            double denominator = Double.parseDouble(parts[1]);
            if (denominator == 0)
                throw new ArithmeticException("Division by zero");
            return numerator / denominator;
        }
        return Double.parseDouble(clean);
    }

    /**
     * Parser and solver for scientific expressions (sqrt, sin, cos, tan,
     * exponents).
     */
    private static boolean solveScientific(String text, SolveResult result) {
        try {
            // Square Root: sqrt(x)
            if (text.contains("sqrt")) {
                Pattern p = Pattern.compile("sqrt\\s*\\(?\\s*(\\d+(\\.\\d+)?)\\s*\\)?");
                Matcher m = p.matcher(text);
                if (m.find()) {
                    double val = Double.parseDouble(m.group(1));
                    double res = Math.sqrt(val);
                    result.answer = formatResult(res);
                    result.isSuccess = true;
                    result.steps.add("Operation: Square Root (√)");
                    result.steps.add("Calculation: √" + val + " = " + result.answer);
                    return true;
                }
            }

            // Trigonometry: sin(x), cos(x), tan(x) in degrees
            Pattern trigPattern = Pattern.compile("(sin|cos|tan)\\s*\\(?\\s*(\\d+(\\.\\d+)?)\\s*\\)?");
            Matcher trigMatcher = trigPattern.matcher(text);
            if (trigMatcher.find()) {
                String func = trigMatcher.group(1).toLowerCase();
                double angle = Double.parseDouble(trigMatcher.group(2));
                double rad = Math.toRadians(angle);
                double res = 0;

                if (func.equals("sin"))
                    res = Math.sin(rad);
                else if (func.equals("cos"))
                    res = Math.cos(rad);
                else if (func.equals("tan"))
                    res = Math.tan(rad);

                result.answer = formatResult(res);
                result.isSuccess = true;
                result.steps.add("Trigonometry: " + func + "(" + angle + "°)");
                result.steps.add(
                        "Step 1: Convert angle to radians: " + angle + "° = " + String.format("%.4f", rad) + " rad");
                result.steps.add("Step 2: Evaluate trig function: " + func + "(" + String.format("%.4f", rad) + ") = "
                        + result.answer);
                return true;
            }

            // Exponentiation/Power: x^y
            Pattern powPattern = Pattern.compile("(\\d+(\\.\\d+)?)\\s*\\^\\s*(\\d+(\\.\\d+)?)");
            Matcher powMatcher = powPattern.matcher(text);
            if (powMatcher.find()) {
                double base = Double.parseDouble(powMatcher.group(1));
                double exp = Double.parseDouble(powMatcher.group(3));
                double res = Math.pow(base, exp);
                result.answer = formatResult(res);
                result.isSuccess = true;
                result.steps.add("Operation: Exponentiation (Power ^)");
                result.steps.add("Calculation: " + base + " ^ " + exp + " = " + result.answer);
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    /**
     * Parser and solver for quadratic equations in the format 'ax^2 + bx + c = 0'.
     */
    private static boolean solveQuadratic(String text, SolveResult result) {
        Pattern pattern = Pattern.compile("(\\d*)\\s*x\\^2\\s*([+\\-])\\s*(\\d*)\\s*x\\s*([+\\-])\\s*(\\d+)\\s*=\\s*0");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                double a = matcher.group(1).isEmpty() ? 1 : Double.parseDouble(matcher.group(1));
                String op1 = matcher.group(2);
                double b = matcher.group(3).isEmpty() ? 1 : Double.parseDouble(matcher.group(3));
                if (op1.equals("-"))
                    b = -b;
                String op2 = matcher.group(4);
                double c = Double.parseDouble(matcher.group(5));
                if (op2.equals("-"))
                    c = -c;

                double discriminant = b * b - 4 * a * c;
                result.steps.add("Quadratic Equation: " + formatResult(a) + "x² + (" + formatResult(b) + ")x + ("
                        + formatResult(c) + ") = 0");
                result.steps.add("Step 1: Calculate Discriminant (D = b² - 4ac)");
                result.steps.add("D = (" + formatResult(b) + ")² - 4(" + formatResult(a) + ")(" + formatResult(c)
                        + ") = " + formatResult(discriminant));

                if (discriminant < 0) {
                    result.answer = "No real roots";
                    result.steps.add("Discriminant is negative. The roots are imaginary / complex.");
                } else {
                    double r1 = (-b + Math.sqrt(discriminant)) / (2 * a);
                    double r2 = (-b - Math.sqrt(discriminant)) / (2 * a);
                    result.answer = "x1 = " + formatResult(r1) + ", x2 = " + formatResult(r2);
                    result.steps.add("Step 2: Apply Quadratic Formula x = [-b ± √D] / 2a");
                    result.steps.add("x = [" + formatResult(-b) + " ± √" + formatResult(discriminant) + "] / "
                            + formatResult(2 * a));
                    result.steps.add("Final Roots: " + result.answer);
                }
                result.isSuccess = true;
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Parser and solver for calculus problems (differentiation and integration
     * rules).
     */
    private static boolean solveCalculus(String text, SolveResult result) {
        // Derivative Rule: d/dx(ax^n)
        Pattern pattern = Pattern.compile("d/dx\\s*\\(?\\s*(\\d*)\\s*x\\s*\\^\\s*(\\d+)\\s*\\)?");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            double a = matcher.group(1).isEmpty() ? 1 : Double.parseDouble(matcher.group(1));
            int n = Integer.parseInt(matcher.group(2));

            double newA = a * n;
            int newN = n - 1;

            result.answer = formatResult(newA) + "x^" + newN;
            result.isSuccess = true;
            result.steps.add("Operation: Differentiation (d/dx)");
            result.steps.add("Power Rule: d/dx(ax^n) = (a * n) * x^(n - 1)");
            result.steps
                    .add("Calculation: (" + formatResult(a) + " * " + n + ") * x^(" + n + " - 1) = " + result.answer);
            return true;
        }

        // Integral Rule: integral x^n dx
        Pattern intPattern = Pattern.compile("(integral|int)\\s*\\(?\\s*(\\d*)\\s*x\\s*\\^\\s*(\\d+)\\s*\\)?");
        Matcher intMatcher = intPattern.matcher(text);
        if (intMatcher.find()) {
            double a = intMatcher.group(2).isEmpty() ? 1 : Double.parseDouble(intMatcher.group(2));
            int n = Integer.parseInt(intMatcher.group(3));

            double newA = a / (n + 1);
            int newN = n + 1;

            result.answer = formatResult(newA) + "x^" + newN + " + C";
            result.isSuccess = true;
            result.steps.add("Operation: Integration (∫)");
            result.steps.add("Power Rule: ∫ ax^n dx = (a / (n + 1)) * x^(n + 1) + C");
            result.steps.add("Calculation: (" + formatResult(a) + " / " + (n + 1) + ") * x^(" + n + " + 1) + C = "
                    + result.answer);
            return true;
        }
        return false;
    }

    /**
     * Parser and solver for simultaneous linear equations.
     */
    private static boolean solveSimultaneous(String text, SolveResult result) {
        // Pattern matches format: "ax + by = c, dx + ey = f"
        Pattern pattern = Pattern.compile(
                "(\\d*)\\s*x\\s*([+\\-])\\s*(\\d*)\\s*y\\s*=\\s*(\\d+)[,\\s]+(\\d*)\\s*x\\s*([+\\-])\\s*(\\d*)\\s*y\\s*=\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                double a1 = matcher.group(1).isEmpty() ? 1 : Double.parseDouble(matcher.group(1));
                String op1 = matcher.group(2);
                double b1 = matcher.group(3).isEmpty() ? 1 : Double.parseDouble(matcher.group(3));
                if (op1.equals("-"))
                    b1 = -b1;
                double c1 = Double.parseDouble(matcher.group(4));

                double a2 = matcher.group(5).isEmpty() ? 1 : Double.parseDouble(matcher.group(5));
                String op2 = matcher.group(6);
                double b2 = matcher.group(7).isEmpty() ? 1 : Double.parseDouble(matcher.group(7));
                if (op2.equals("-"))
                    b2 = -b2;
                double c2 = Double.parseDouble(matcher.group(8));

                // Solve using Cramer's Rule
                double det = a1 * b2 - a2 * b1;
                if (det == 0) {
                    result.errorMessage = "No unique solution exists (Determinant is 0).";
                    return false;
                }

                double x = (c1 * b2 - c2 * b1) / det;
                double y = (a1 * c2 - a2 * c1) / det;

                result.answer = "x = " + formatResult(x) + ", y = " + formatResult(y);
                result.isSuccess = true;
                result.steps.add("Simultaneous Equations:");
                result.steps.add("Eq 1: " + formatResult(a1) + "x + (" + formatResult(b1) + ")y = " + formatResult(c1));
                result.steps.add("Eq 2: " + formatResult(a2) + "x + (" + formatResult(b2) + ")y = " + formatResult(c2));
                result.steps.add("Method: Cramer's Rule");
                result.steps.add("Determinant (D) = " + formatResult(det));
                result.steps
                        .add("Dx = " + formatResult(c1 * b2 - c2 * b1) + ", Dy = " + formatResult(a1 * c2 - a2 * c1));
                result.steps.add("Final Result: " + result.answer);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Parser and solver for basic arithmetic equations (PEMDAS).
     */
    private static boolean solveBasicArithmetic(String text, SolveResult result) {
        try {
            // Clean operations
            String expression = text.replace("×", "*").replace("÷", "/");

            // Treat letter x as multiply ONLY when sandwiched between two digits
            // (e.g. 2x3 = 2*3). This preserves algebra like 2x+3.
            expression = expression.replaceAll("(\\d)\\s*[xX]\\s*(\\d)", "$1*$2");

            // Evaluate using recursive descent parser
            double solveVal = new ExpressionEvaluator(expression).parse();
            if (!Double.isFinite(solveVal))
                return false;

            result.answer = formatResult(solveVal);
            result.isSuccess = true;
            result.steps.add("Expression: " + text);
            result.steps.add("Calculation: Evaluated using standard mathematical order of operations (PEMDAS).");
            result.steps.add("Result: " + result.answer);
            return true;
        } catch (ArithmeticException e) {
            result.isSuccess = false;
            String msg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage() : "Arithmetic error";
            result.errorMessage = msg;
            result.answer = "Undefined";
            result.steps.clear();
            result.steps.add("Arithmetic Error: " + msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Private helper class implementing a Recursive Descent Parser for math
     * expressions.
     * Supports addition, subtraction, multiplication, division, parentheses, and
     * exponents.
     */
    private static class ExpressionEvaluator {
        private final String str;
        private int pos = -1;
        private int ch;

        public ExpressionEvaluator(String str) {
            this.str = str;
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ')
                nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        public double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length())
                throw new RuntimeException("Unexpected character: " + (char) ch);
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+'))
                    x += parseTerm(); // addition
                else if (eat('-'))
                    x -= parseTerm(); // subtraction
                else
                    return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*'))
                    x *= parseFactor(); // multiplication
                else if (eat('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0)
                        throw new ArithmeticException("Division by zero");
                    x /= divisor;
                } else
                    return x;
            }
        }

        private double parseFactor() {
            if (eat('+'))
                return parseFactor(); // unary plus
            if (eat('-'))
                return -parseFactor(); // unary minus

            double x;
            int startPos = this.pos;
            if (eat('(')) { // parentheses
                x = parseExpression();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                while ((ch >= '0' && ch <= '9') || ch == '.')
                    nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else {
                throw new RuntimeException("Unexpected factor element: " + (char) ch);
            }

            if (eat('^'))
                x = Math.pow(x, parseFactor()); // exponentiation

            return x;
        }
    }

    /**
     * Normalizes and cleans the text representation of mathematical expressions.
     */
    private static String normalize(String text, boolean isOcr) {
        if (text == null)
            return "";
        String normalized = text.trim()
                .toLowerCase()
                .replace("\n", " ")
                .replaceAll("\\s+", " ");

        // Only fix common OCR errors if the text came from a camera scan
        if (isOcr) {
            // Only replace if they are NOT part of function names
            if (!normalized.contains("sin") && !normalized.contains("cos") && !normalized.contains("tan")
                    && !normalized.contains("sqrt")) {
                normalized = normalized.replace("l", "1").replace("i", "1")
                        .replace("o", "0");
            }
        }

        return normalized;
    }

    /**
     * Convert between supported metric units of the same dimension.
     * Length base = meters, mass base = kilograms, volume base = litres.
     */
    private static Double convertUnit(double value, String from, String to) {
        Double fromToBase = unitToBase(from);
        Double toToBase = unitToBase(to);
        if (fromToBase == null || toToBase == null)
            return null;
        if (!unitDimension(from).equals(unitDimension(to)))
            return null;
        return value * fromToBase / toToBase;
    }

    private static String unitDimension(String unit) {
        switch (unit) {
            case "m":
            case "cm":
            case "km":
                return "length";
            case "kg":
            case "g":
                return "mass";
            case "l":
            case "ml":
                return "volume";
            default:
                return "";
        }
    }

    private static Double unitToBase(String unit) {
        switch (unit) {
            case "m":
                return 1.0;
            case "cm":
                return 0.01;
            case "km":
                return 1000.0;
            case "kg":
                return 1.0;
            case "g":
                return 0.001;
            case "l":
                return 1.0;
            case "ml":
                return 0.001;
            default:
                return null;
        }
    }

    /**
     * Formatting helper for clean decimal displays.
     */
    private static String formatResult(double val) {
        if (val == (long) val)
            return String.format("%d", (long) val);
        return String.format("%.4f", val).replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    public static String toMath(String text) {
        return text;
    }
}