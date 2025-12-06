# Quick Start Guide

Get started with JAIPilot and generate your first unit test in under 5 minutes.
- 📚 [Examples](../examples/simple-class.md) - See examples

## Prerequisites

Before you begin, make sure you have:

- ✅ [JAIPilot installed](installation.md) in IntelliJ IDEA
- ✅ A Java project open with JUnit dependencies
- ✅ At least one Java class to test

## Step 1: Sign Up and Activate JAIPilot

Before generating tests, you need to create an account and activate your license key.

### Create Account

1. Visit [jaipilot.com](https://jaipilot.com) and **sign up** (no credit card required)
2. You'll receive **40 request attempts** (~4 classes) worth of free credits
3. Check your email for your **license key**
4. You can also find your license key anytime at [jaipilot.com/account](https://jaipilot.com/account)

### Activate License Key

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** → **Tools** → **JAIPilot Plugin**
3. **Paste your license key** in the License Key field
4. Click **OK** to save

> **Important:** Keep your license key safe - you'll need it if you reinstall the plugin or use it on another machine.

## Step 2: Generate Your First Test

Let's generate a test for a simple Java class.

### Example Class

Create or open a Java class like this:

```java
package com.example.demo;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }
}
```

### Generate Tests

1. **Open** the `Calculator.java` file in the editor
2. **Right-click** anywhere in the class
3. Select **"Generate Tests with JAIPilot"** from the context menu
4. **Choose** your test root directory:
   - Default: `src/test/java`
   - Or select a custom directory
5. Click **OK**

### What Happens Next

JAIPilot will:

1. ✨ **Analyze** your class and understand its logic
2. 🧪 **Generate** comprehensive test cases
3. ✅ **Run** the generated tests automatically
4. 🔧 **Fix** any failing tests (if needed)

**This usually takes a few minutes.**

## Step 3: Review Generated Tests

JAIPilot creates a test file at:
```
src/test/java/com/example/demo/CalculatorTest.java
```

### Generated Test Example

```java
package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    @Test
    void testAdd_PositiveNumbers() {
        assertEquals(5, calculator.add(2, 3));
    }

    @Test
    void testAdd_NegativeNumbers() {
        assertEquals(-5, calculator.add(-2, -3));
    }

    @Test
    void testAdd_MixedNumbers() {
        assertEquals(1, calculator.add(-2, 3));
    }

    @Test
    void testSubtract_PositiveNumbers() {
        assertEquals(1, calculator.subtract(3, 2));
    }

    @Test
    void testMultiply_PositiveNumbers() {
        assertEquals(6, calculator.multiply(2, 3));
    }

    @Test
    void testMultiply_WithZero() {
        assertEquals(0, calculator.multiply(5, 0));
    }

    @Test
    void testDivide_ValidInput() {
        assertEquals(2, calculator.divide(6, 3));
    }

    @Test
    void testDivide_ThrowsExceptionWhenDivideByZero() {
        assertThrows(IllegalArgumentException.class,
            () -> calculator.divide(10, 0));
    }
}
```

### Test Features

Notice that JAIPilot:

- ✅ Created **multiple test cases** for each method
- ✅ Tested **edge cases** (negative numbers, zero, exceptions)
- ✅ Used **meaningful test names** that describe what's being tested
- ✅ Added **proper assertions** (assertEquals, assertThrows)
- ✅ Included **setup code** (@BeforeEach)
- ✅ Tests **compile and pass** without manual editing

### View Test Results

In the Run window, you'll see:
- ✅ Green checkmarks for passing tests
- ❌ Red X marks for failing tests (rare with JAIPilot)
- Test execution time
- Code coverage (once run with coverage after test generation)

## Step 5: Handling Test Failures

If a test fails, JAIPilot can fix it automatically.

### Automatic Fixing

JAIPilot detects failures and:

1. Analyzes the error message and stack trace
2. Identifies the root cause
3. Regenerates the failing test
4. Runs the test again
5. Repeats until all tests pass

**You don't need to do anything** - just wait for the process to complete.

### Manual Review

If you want to review or customize tests:

1. Open the generated test file
2. Make your changes
3. Run the tests again
4. JAIPilot will respect your modifications in future generations

## Common Use Cases

### Regenerating Tests

**Need to regenerate tests after code changes?**

1. Make your code changes
2. **Right-click** the class
3. Select **"Generate Tests with JAIPilot"** again

## Tips for Best Results

### ✅ Do's

- **Write clear, simple code** - easier for AI to understand
- **Use descriptive method names** - helps generate better test names
- **Include JavaDoc comments** - provides context for test generation
- **Keep methods focused** - single responsibility = better tests

### ❌ Don'ts

- **Don't test with compilation errors** - fix errors first
- **Don't test empty methods** - add implementation first
- **Don't expect tests for untestable code** - refactor legacy code
- **Don't generate tests in production code** - use proper test directories

## Troubleshooting

### Tests Not Generating?

**Check these common issues:**

- ✅ Is your license key activated in plugin settings?
- ✅ Is JUnit in your project dependencies?
- ✅ Does the source file compile successfully?
- ✅ Do you have remaining credits? (Check at [jaipilot.com/account](https://jaipilot.com/account))

See [Common Issues](../troubleshooting/common-issues.md) for more help.

### License Key Issues?

- Copy the full license key from your email or [jaipilot.com/account](https://jaipilot.com/account)
- Ensure there are no extra spaces when pasting
- Go to `Settings` → `Tools` → `JAIPilot Plugin` and re-enter the key
- Check your internet connection

### Out of Credits?

- Check your remaining credits at [jaipilot.com/account](https://jaipilot.com/account)
- Top up with pay-as-you-go billing when you run out of free credits

### Generated Tests Not Running?

- Ensure JUnit is in your classpath
- Check test directory is marked as "Test Sources" (green folder)
- Verify JUnit version matches your project setup

## Need Help?

- 📚 [FAQ](../troubleshooting/faq.md) - Common questions answered
- 🐛 [Report Issues](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
- 💬 [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions) - Ask questions

## Feedback

We'd love to hear about your experience:

- ⭐ [Star us on GitHub](https://github.com/skrcode/java-auto-unit-tests)
- 🎯 [Rate on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing/reviews)
- 💡 [Suggest Features](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=feature_request.md)

---

**Congratulations!** You've successfully generated your first test with JAIPilot. Happy testing! 🎉
