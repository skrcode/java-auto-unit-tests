# Test Refinement

JAIPilot's autonomous test refinement feature automatically detects, analyzes, and fixes failing tests until they pass.

## How Test Refinement Works

### The Refinement Cycle

1. **Test Generation**: JAIPilot creates initial test suite
2. **Automatic Execution**: Tests are run immediately
3. **Failure Detection**: Any failing tests are identified
4. **Error Analysis**: JAIPilot analyzes error messages and stack traces
5. **Test Regeneration**: Failed tests are regenerated with fixes
6. **Re-execution**: Updated tests are run again
7. **Iteration**: Steps 3-6 repeat until all tests pass (or max attempts reached)

### What Gets Fixed Automatically

JAIPilot can automatically fix:

- **Compilation errors**: Missing imports, wrong types, syntax errors
- **Assertion failures**: Incorrect expected values, wrong assertion types
- **Mock setup issues**: Incorrect mock behavior, missing stubs
- **Null pointer exceptions**: Missing null checks or initializations
- **Type mismatches**: Generic types, casting issues
- **Exception handling**: Wrong exception types or missing exception tests

## Monitoring Refinement Progress

### Visual Indicators

During refinement, you'll see:

1. **Progress notification**: Shows current status
   - "Generating tests..."
   - "Running tests..."
   - "Analyzing failures..."
   - "Fixing and regenerating..."

2. **Test execution results**: In the IDE's test runner window

3. **Final status**:
   - ✅ "All tests passed!"
   - ⚠️ "Some tests still failing - manual review needed"

### Refinement Logs

View detailed logs:
1. Open **Event Log** (bottom-right corner)
2. Find JAIPilot notifications
3. Click to expand details

## Refinement Examples

### Example 1: Fixing Assertion Errors

**Initial Generated Test:**

```java
@Test
void testCalculateTotal() {
    ShoppingCart cart = new ShoppingCart();
    cart.addItem(new Item("Book", 20.00));
    cart.addItem(new Item("Pen", 5.00));

    assertEquals(20.00, cart.calculateTotal()); // Wrong!
}
```

**Error Detected:**
```
Expected: 20.00
Actual: 25.00
```

**After Refinement:**

```java
@Test
void testCalculateTotal() {
    ShoppingCart cart = new ShoppingCart();
    cart.addItem(new Item("Book", 20.00));
    cart.addItem(new Item("Pen", 5.00));

    assertEquals(25.00, cart.calculateTotal()); // Fixed!
}
```

### Example 2: Fixing Mock Setup

**Initial Generated Test:**

```java
@Test
void testGetUser() {
    when(userRepository.findById(1L)).thenReturn(null); // Wrong!

    User user = userService.getUser(1L);
    assertEquals("John", user.getName()); // NullPointerException!
}
```

**Error Detected:**
```
NullPointerException at userService.getUser(UserService.java:15)
```

**After Refinement:**

```java
@Test
void testGetUser() {
    User mockUser = new User(1L, "John");
    when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser)); // Fixed!

    User user = userService.getUser(1L);
    assertEquals("John", user.getName());
}
```

### Example 3: Fixing Exception Tests

**Initial Generated Test:**

```java
@Test
void testDivide_ThrowsException() {
    assertThrows(ArithmeticException.class, // Wrong exception type!
        () -> calculator.divide(10, 0));
}
```

**Error Detected:**
```
Expected ArithmeticException to be thrown, but IllegalArgumentException was thrown
```

**After Refinement:**

```java
@Test
void testDivide_ThrowsException() {
    assertThrows(IllegalArgumentException.class, // Fixed!
        () -> calculator.divide(10, 0));
}
```

## Refinement Configuration

### Maximum Refinement Attempts

JAIPilot attempts refinement up to 3 times by default. Configure in settings:

1. `Settings` → `Tools` → `JAIPilot`
2. Adjust **Max Refinement Attempts** (1-5)
   - Lower: Faster completion, may leave some tests failing
   - Higher: More thorough fixing, takes longer

### Refinement Strategy

Choose refinement approach:

- **Aggressive**: Attempts to fix all issues in each iteration
- **Conservative**: Fixes one issue at a time (default)
- **Balanced**: Mix of both approaches

## When Manual Intervention is Needed

Sometimes tests require manual fixes. Common scenarios:

### 1. Complex Business Logic

**Scenario**: Test expectations depend on business rules JAIPilot doesn't know.

**Solution**: Review the test and adjust assertions based on your requirements.

### 2. External Dependencies

**Scenario**: Tests interact with databases, APIs, or file systems.

**Solution**:
- Add proper test doubles/mocks
- Configure test database connections
- Use in-memory alternatives

### 3. Time-Dependent Logic

**Scenario**: Tests involve dates, times, or delays.

**Solution**:
- Mock time-related dependencies
- Use fixed test dates
- Add `@Timeout` annotations if needed

### 4. Multi-Threading Issues

**Scenario**: Tests involve concurrent code.

**Solution**:
- Add proper synchronization
- Use `CountDownLatch` or `CompletableFuture` for coordination
- Consider using awaitility library

### 5. Environment-Specific Issues

**Scenario**: Tests pass locally but fail in CI or vice versa.

**Solution**:
- Check environment configurations
- Verify dependencies and versions
- Add environment-specific test profiles

## Best Practices for Refinement

### 1. Let Refinement Complete

Don't interrupt the refinement process. JAIPilot needs time to:
- Analyze failures
- Regenerate fixes
- Re-run tests

### 2. Review Fixed Tests

Even after successful refinement:
- Read through the generated tests
- Verify assertions match your expectations
- Check edge cases are properly covered

### 3. Provide Feedback

If refinement consistently fails for certain patterns:
- Report the issue with code examples
- Help improve JAIPilot's refinement algorithms

### 4. Keep Tests Maintainable

After refinement:
- Refactor duplicate code
- Extract common setup to helper methods
- Add explanatory comments where needed

## Troubleshooting Refinement

### Refinement Takes Too Long

**Problem**: Refinement cycle runs for several minutes.

**Solutions**:
- Check network connectivity (JAIPilot needs to communicate with AI service)
- Reduce max refinement attempts
- Check for complex classes with many tests
- Review IDE logs for stuck processes

### Refinement Gives Up

**Problem**: JAIPilot stops after max attempts with failing tests.

**Solutions**:
- Review the failure messages
- Fix the underlying code issues
- Manually fix and save one test as an example
- Regenerate with JAIPilot

### Same Error Keeps Occurring

**Problem**: Refinement cycles but doesn't fix the issue.

**Solutions**:
- The issue might be in your source code, not the test
- Check if the error indicates a bug in your implementation
- Manually review and fix one instance
- Report the issue if it seems like a JAIPilot bug

### Tests Pass but Assertions are Wrong

**Problem**: Tests pass but don't actually verify correct behavior.

**Solutions**:
- This is rare but can happen
- Review generated assertions carefully
- Verify they match your business requirements
- Adjust assertions manually if needed

## Disabling Automatic Refinement

If you prefer to handle test failures manually:

1. `Settings` → `Tools` → `JAIPilot`
2. Uncheck **Enable Automatic Test Refinement**
3. JAIPilot will generate tests but not automatically fix failures

**Note**: This is not recommended for most users. Automatic refinement saves significant time.

## Next Steps

- Learn about [Advanced Features](advanced-features.md)
- See [Examples](../examples/) with refinement scenarios
- Read [Best Practices](best-practices.md)

## Need Help?

- 📚 [FAQ](../troubleshooting/faq.md)
- 🐛 [Report Issues](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
