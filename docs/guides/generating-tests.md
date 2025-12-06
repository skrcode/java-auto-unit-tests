# Generating Tests with JAIPilot

This comprehensive guide covers everything you need to know about generating unit tests with JAIPilot.

## Overview

JAIPilot uses advanced AI to analyze your Java code and generate comprehensive, context-aware JUnit tests. The plugin understands your code's logic, dependencies, edge cases, and generates meaningful test cases with proper assertions.

## Basic Test Generation

### Generating Tests for a Single Class

The most common use case is generating tests for a single Java class:

1. **Open your Java class** in the editor
2. **Right-click** anywhere in the editor
3. **Select** "Generate Tests with JAIPilot" from the context menu
4. **Choose test root directory** (e.g., `src/test/java`) if prompted
5. **Wait** while JAIPilot analyzes and generates tests

**Example:**

```java
// Original class: Calculator.java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Division by zero");
        }
        return a / b;
    }
}
```

JAIPilot will generate a comprehensive test class with:
- Setup and teardown methods
- Tests for normal cases
- Tests for edge cases (negative numbers, zero, etc.)
- Exception testing
- Proper assertions

### Generating Tests for Multiple Classes

Generate tests for multiple classes at once:

1. **Select multiple Java files** in the Project view
2. **Right-click** on the selection
3. **Select** "Generate Tests with JAIPilot"
4. JAIPilot will process each class sequentially

## Advanced Generation Options

### Test Framework Selection

JAIPilot supports both JUnit 4 and JUnit 5:

**JUnit 5 (Recommended):**
- Uses `@Test`, `@BeforeEach`, `@AfterEach` annotations
- Modern assertion API
- Better parameterized test support

**JUnit 4:**
- Uses `@Test`, `@Before`, `@After` annotations
- Compatible with legacy projects

Configure in: `Settings` → `Tools` → `JAIPilot` → `Test Framework`

### Custom Test Root Directory

By default, JAIPilot uses `src/test/java`. To customize:

1. Go to `Settings` → `Tools` → `JAIPilot`
2. Set **Test Root Directory** to your preferred path
3. Examples:
   - `src/test/java` (Maven/Gradle standard)
   - `test/java` (alternative structure)
   - `tests` (custom structure)

### AI Model Selection

JAIPilot automatically selects the best AI model for each class. You can also manually configure:

1. Go to `Settings` → `Tools` → `JAIPilot`
2. Choose **AI Model Selection**:
   - **Automatic** (Recommended): JAIPilot chooses the optimal model
   - **Fast**: Prioritizes speed over complexity
   - **Balanced**: Good balance of speed and quality
   - **Comprehensive**: Maximum test coverage and quality

## What JAIPilot Generates

### Standard Test Structure

```java
public class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    @AfterEach
    void tearDown() {
        // Cleanup if needed
    }

    @Test
    void testAdd_PositiveNumbers() {
        int result = calculator.add(5, 3);
        assertEquals(8, result);
    }

    @Test
    void testDivide_ThrowsException_WhenDivisorIsZero() {
        assertThrows(IllegalArgumentException.class,
            () -> calculator.divide(10, 0));
    }
}
```

### Test Categories Generated

JAIPilot generates tests for:

1. **Happy Path Tests**: Normal use cases with valid inputs
2. **Edge Cases**: Boundary values, empty collections, null checks
3. **Exception Tests**: Invalid inputs that should throw exceptions
4. **Negative Tests**: Testing failure scenarios
5. **Parameterized Tests**: Multiple inputs for the same logic (when applicable)

### Dependencies and Mocking

When your class has dependencies, JAIPilot automatically:

- Identifies dependencies (constructor injection, field injection)
- Creates mock objects using Mockito
- Sets up proper mock behavior
- Verifies mock interactions

**Example:**

```java
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
```

**Generated Test:**

```java
public class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFindById_UserExists() {
        User mockUser = new User(1L, "John");
        when(repository.findById(1L)).thenReturn(Optional.of(mockUser));

        User result = userService.findById(1L);

        assertEquals(mockUser, result);
        verify(repository).findById(1L);
    }

    @Test
    void testFindById_ThrowsNotFoundException_WhenUserNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> userService.findById(999L));
    }
}
```

## Test Generation Best Practices

### 1. Ensure Clean Compilation

Before generating tests:
- Make sure your source code compiles without errors
- Resolve any dependency issues
- Ensure all imports are correct

### 2. Provide Good Method Names

Descriptive method names help JAIPilot understand intent:

```java
// Good
public boolean isEligibleForDiscount(Customer customer)

// Less helpful
public boolean check(Customer c)
```

### 3. Use Meaningful Variable Names

Clear variable names improve test quality:

```java
// Good
public void processOrder(Order order, Customer customer)

// Less helpful
public void process(Order o, Customer c)
```

### 4. Add Javadoc Comments

While optional, Javadoc helps JAIPilot understand complex logic:

```java
/**
 * Calculates shipping cost based on weight and destination.
 * @param weight in kilograms
 * @param destination country code
 * @return cost in USD
 */
public double calculateShipping(double weight, String destination)
```

### 5. Review Generated Tests

Always review generated tests:
- Verify assertions are meaningful
- Check edge cases are covered
- Ensure mocks behave correctly
- Add additional tests if needed

## Handling Special Cases

### Abstract Classes

JAIPilot skips abstract classes but can generate tests for concrete methods. Create a concrete subclass for testing.

### Interfaces

Interfaces cannot be tested directly. Test the implementing classes instead.

### Private Methods

JAIPilot focuses on public APIs. Test private methods indirectly through public methods.

### Static Methods

JAIPilot generates tests for static methods:

```java
public class MathUtils {
    public static int max(int a, int b) {
        return a > b ? a : b;
    }
}
```

### Generic Classes

JAIPilot handles generic types:

```java
public class Container<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
```

Generated test will use concrete types:

```java
public class ContainerTest {
    @Test
    void testSetAndGet_WithString() {
        Container<String> container = new Container<>();
        container.set("test");
        assertEquals("test", container.get());
    }
}
```

## Performance Tips

### Generation Speed

- **Simple classes**: 5-15 seconds
- **Complex classes**: 15-30 seconds
- **Classes with many dependencies**: 30-60 seconds

### Optimizing Generation

1. **Close unnecessary files**: Reduces context analysis time
2. **Disable other plugins temporarily**: Frees up resources
3. **Use automatic model selection**: Optimizes for your class complexity
4. **Generate in batches**: More efficient than one-by-one for many classes

## Troubleshooting Generation

### Tests Not Generated

**Problem**: Generation completes but no test file is created.

**Solutions**:
- Check IDE logs for errors
- Ensure test root directory exists
- Verify write permissions
- Check your API credits

### Incomplete Tests

**Problem**: Only partial test file is generated.

**Solutions**:
- Check network connectivity
- Verify API credits haven't run out
- Try regenerating
- Report the issue with your class example

### Compilation Errors in Generated Tests

**Problem**: Generated tests don't compile.

**Solutions**:
- Wait for automatic refinement (JAIPilot fixes failing tests)
- Check JUnit version in dependencies matches configuration
- Ensure Mockito is in test dependencies if mocking is used
- Manually fix and report the issue

### Tests Fail After Generation

**Problem**: Generated tests compile but fail when run.

**Solutions**:
- JAIPilot automatically detects and fixes failing tests
- Wait for the refinement process to complete
- Check if your code has side effects or external dependencies
- Review test assumptions and adjust if needed

## Next Steps

- Learn about [Test Refinement](test-refinement.md)
- Explore [Advanced Features](advanced-features.md)
- Check out [Best Practices](best-practices.md)
- See [Examples](../examples/) for real-world scenarios

## Need Help?

- 📚 [FAQ](../troubleshooting/faq.md)
- 🐛 [Report Issues](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
- 💬 [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
