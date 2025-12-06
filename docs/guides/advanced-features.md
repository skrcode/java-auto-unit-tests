# Advanced Features

Unlock the full power of JAIPilot with these advanced capabilities.

## Smart Model Selection

JAIPilot uses different AI models based on class complexity.

### Automatic Model Selection (Recommended)

JAIPilot analyzes your class and chooses the optimal model:

- **Fast Model**: For simple classes (<5 methods, no dependencies)
- **Balanced Model**: For typical classes (5-15 methods, few dependencies)
- **Comprehensive Model**: For complex classes (>15 methods, many dependencies, intricate logic)

**Configure**: `Settings` → `Tools` → `JAIPilot` → `AI Model Selection` → `Automatic`

### Manual Model Selection

Override automatic selection:

1. Go to `Settings` → `Tools` → `JAIPilot`
2. Change `AI Model Selection` to `Manual`
3. Select preferred model for all generations

**When to use manual**:
- You always want maximum quality (choose Comprehensive)
- You prioritize speed over thoroughness (choose Fast)
- You're generating tests for specific project types

## Batch Test Generation

Generate tests for multiple classes efficiently.

### Selecting Multiple Files

**In Project View:**
1. Hold `Cmd` (macOS) or `Ctrl` (Windows/Linux)
2. Click multiple Java files
3. Right-click → "Generate Tests with JAIPilot"

**Select Entire Package:**
1. Right-click on package
2. Select "Generate Tests with JAIPilot"
3. JAIPilot processes all classes in the package

### Batch Generation Best Practices

- **Start small**: Test with 5-10 classes first
- **Monitor progress**: Watch notifications for status
- **Review incrementally**: Don't accumulate review debt
- **Check API usage**: Batch operations consume more credits

## Custom Test Templates

Customize generated test structure (coming soon).

## Integration with Code Coverage Tools

### JaCoCo Integration

JAIPilot works seamlessly with JaCoCo:

```groovy
// build.gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.10"
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

**Generate coverage report:**
```bash
./gradlew test jacocoTestReport
```

### IntelliJ Built-in Coverage

1. Generate tests with JAIPilot
2. Right-click test class
3. Select "Run 'TestClass' with Coverage"
4. View coverage in IDE

## CI/CD Integration

### GitHub Actions

```yaml
name: Tests with JAIPilot

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: ./gradlew test

      - name: Generate coverage report
        run: ./gradlew jacocoTestReport

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./build/reports/jacoco/test/jacocoTestReport.xml
```

### Jenkins

```groovy
pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh './gradlew clean test'
            }
        }

        stage('Coverage') {
            steps {
                sh './gradlew jacocoTestReport'
                publishHTML([
                    reportDir: 'build/reports/jacoco/test/html',
                    reportFiles: 'index.html',
                    reportName: 'Coverage Report'
                ])
            }
        }
    }

    post {
        always {
            junit '**/build/test-results/test/*.xml'
        }
    }
}
```

## Keyboard Shortcuts

Speed up your workflow with shortcuts (customize in IDE settings):

**Default shortcuts:**
- Generate tests: `Alt+Insert` (Windows/Linux), `Cmd+N` (macOS) → Select "JAIPilot Tests"
- Run tests: `Ctrl+Shift+F10` (Windows/Linux), `Ctrl+Shift+R` (macOS)
- Run with coverage: `Ctrl+Shift+F10` with `Alt` (Windows/Linux), `Ctrl+Shift+R` with `Alt` (macOS)

**Custom shortcuts:**
1. `Settings` → `Keymap`
2. Search for "JAIPilot"
3. Right-click → `Add Keyboard Shortcut`
4. Assign your preferred shortcut

## Test Naming Conventions

JAIPilot follows best practices for test naming.

### Default Pattern

```java
@Test
void test{MethodName}_{Scenario}_{ExpectedResult}()
```

**Examples:**
```java
testCalculateDiscount_WithValidInput_ReturnsCorrectAmount()
testCreateOrder_WhenStockUnavailable_ThrowsException()
testGetUser_WithNullId_ReturnsNull()
```

### Customizing Names (Manual)

After generation, you can rename for clarity:

```java
// Generated
@Test
void testProcess_1() { }

// Enhanced
@Test
@DisplayName("Should process valid order successfully")
void shouldProcessValidOrderSuccessfully() { }
```

## Working with Legacy Code

JAIPilot handles legacy code challenges:

### Classes Without Dependency Injection

```java
// Legacy code
public class LegacyService {
    private Database db = new Database(); // Hard-coded dependency

    public User getUser(int id) {
        return db.query("SELECT * FROM users WHERE id = " + id);
    }
}
```

**JAIPilot's approach:**
1. Generates tests for testable parts
2. Recommends refactoring for better testability
3. Uses PowerMock or similar for hard-to-test code

### Complex Static Methods

```java
public class LegacyUtils {
    public static User getCurrentUser() {
        return SessionManager.getSession().getUser();
    }
}
```

**Generated test strategy:**
- Uses PowerMockito for static mocking
- Or suggests refactoring to injectable pattern

## Performance Optimization

### Parallel Test Execution

Enable parallel test execution:

```groovy
// build.gradle
test {
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
}
```

### Faster Test Execution

Tips for faster tests:
1. **Use in-memory databases**: H2 instead of PostgreSQL
2. **Mock external services**: Don't make real HTTP calls
3. **Minimize setup**: Only initialize what you need
4. **Use @TestInstance**: Reuse test instance in JUnit 5

## Advanced Mocking

### Mockito Features

JAIPilot uses advanced Mockito features:

```java
// Argument captors
@Test
void testSendEmail() {
    ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);

    service.sendWelcomeEmail(user);

    verify(emailService).send(captor.capture());
    Email sentEmail = captor.getValue();
    assertEquals("Welcome!", sentEmail.getSubject());
}

// Spy for partial mocking
@Test
void testWithSpy() {
    OrderService spy = spy(new OrderService());
    doReturn(mockOrder).when(spy).createOrder(any());

    Order result = spy.processOrder(request);

    assertNotNull(result);
}
```

## Test Data Builders

JAIPilot can generate test data builders:

```java
// Generated builder pattern
public class UserBuilder {
    private String name = "John Doe";
    private String email = "john@example.com";
    private int age = 25;

    public UserBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public UserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public User build() {
        return new User(name, email, age);
    }
}

// Usage in tests
@Test
void testUserValidation() {
    User user = new UserBuilder()
        .withAge(17)
        .build();

    assertFalse(validator.isValid(user));
}
```

## Testing Patterns

### Testing Exceptions

```java
// JUnit 5 pattern
@Test
void testDivision_ThrowsException() {
    Exception exception = assertThrows(
        ArithmeticException.class,
        () -> calculator.divide(10, 0)
    );
    assertTrue(exception.getMessage().contains("zero"));
}
```

### Testing Async Code

```java
@Test
void testAsyncOperation() throws Exception {
    CompletableFuture<String> future = service.asyncMethod();

    String result = future.get(5, TimeUnit.SECONDS);

    assertEquals("expected", result);
}
```

### Testing Timeouts

```java
@Test
@Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
void testPerformance() {
    service.fastOperation();
}
```

## Plugin API (Beta)

Integrate JAIPilot programmatically (advanced users only).

### Example API Usage

```java
// Trigger test generation programmatically
JAIPilotService service = ServiceManager.getService(JAIPilotService.class);
service.generateTests(psiClass, testRootPath);
```

**Note**: API is in beta and subject to change.

## Troubleshooting Advanced Issues

### Out of Memory Errors

If generating tests for very large classes:

```bash
# Increase IDE memory
export IDEA_VM_OPTIONS="-Xmx4096m"
```

### Slow Test Generation

Optimize performance:
1. Close unnecessary IDE windows
2. Disable unused plugins
3. Use Fast model for simple classes
4. Generate in smaller batches

## Next Steps

- Review [Best Practices](best-practices.md)
- Check out [Examples](../examples/)
- Read [FAQ](../troubleshooting/faq.md)

## Need Help?

- 💬 [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- 🐛 [Report Issues](https://github.com/skrcode/java-auto-unit-tests/issues)
