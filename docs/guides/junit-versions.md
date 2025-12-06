# JUnit 4 vs JUnit 5

JAIPilot supports both JUnit 4 and JUnit 5. This guide helps you understand the differences and choose the right version.

## Quick Comparison

| Feature | JUnit 4 | JUnit 5 |
|---------|---------|---------|
| **Release Year** | 2006 | 2017 |
| **Status** | Maintenance mode | Active development |
| **Annotations** | `@Test`, `@Before`, `@After` | `@Test`, `@BeforeEach`, `@AfterEach` |
| **Assertions** | `org.junit.Assert` | `org.junit.jupiter.api.Assertions` |
| **Assumptions** | Limited | Enhanced |
| **Parameterized Tests** | Separate runner | Built-in `@ParameterizedTest` |
| **Display Names** | Limited | `@DisplayName` |
| **Nested Tests** | Not supported | `@Nested` classes |
| **Dynamic Tests** | Not supported | `@TestFactory` |
| **Extensions** | Rules & Runners | Extension API |
| **Minimum Java** | Java 5 | Java 8 |

## When to Use JUnit 4

Use JUnit 4 if:
- ✅ Working with legacy codebase already using JUnit 4
- ✅ Project is stuck on Java 6 or 7
- ✅ Using frameworks that don't support JUnit 5 yet
- ✅ Team is unfamiliar with JUnit 5 and no time to migrate

## When to Use JUnit 5

Use JUnit 5 (Recommended) if:
- ✅ Starting a new project
- ✅ Using Java 8 or later
- ✅ Want modern testing features
- ✅ Need better test organization with `@Nested`
- ✅ Want built-in parameterized testing

## Generated Test Differences

### Test Class Structure

**JUnit 4:**
```java
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class CalculatorTest {

    private Calculator calculator;

    @Before
    public void setUp() {
        calculator = new Calculator();
    }

    @After
    public void tearDown() {
        // cleanup
    }

    @Test
    public void testAdd() {
        assertEquals(5, calculator.add(2, 3));
    }
}
```

**JUnit 5:**
```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    @AfterEach
    void tearDown() {
        // cleanup
    }

    @Test
    void testAdd() {
        assertEquals(5, calculator.add(2, 3));
    }
}
```

### Exception Testing

**JUnit 4:**
```java
@Test(expected = IllegalArgumentException.class)
public void testDivideByZero() {
    calculator.divide(10, 0);
}

// Or with ExpectedException rule
@Rule
public ExpectedException thrown = ExpectedException.none();

@Test
public void testDivideByZero() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot divide by zero");
    calculator.divide(10, 0);
}
```

**JUnit 5:**
```java
@Test
void testDivideByZero() {
    Exception exception = assertThrows(
        IllegalArgumentException.class,
        () -> calculator.divide(10, 0)
    );
    assertTrue(exception.getMessage().contains("Cannot divide by zero"));
}
```

### Parameterized Tests

**JUnit 4:**
```java
@RunWith(Parameterized.class)
public class CalculatorParameterizedTest {

    private int input1;
    private int input2;
    private int expected;

    public CalculatorParameterizedTest(int input1, int input2, int expected) {
        this.input1 = input1;
        this.input2 = input2;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { 1, 2, 3 },
            { 2, 3, 5 },
            { 5, 5, 10 }
        });
    }

    @Test
    public void testAdd() {
        assertEquals(expected, calculator.add(input1, input2));
    }
}
```

**JUnit 5:**
```java
class CalculatorParameterizedTest {

    @ParameterizedTest
    @CsvSource({
        "1, 2, 3",
        "2, 3, 5",
        "5, 5, 10"
    })
    void testAdd(int input1, int input2, int expected) {
        assertEquals(expected, calculator.add(input1, input2));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void testNegativeNumbers(int input) {
        assertTrue(input < 0 || input == 0);
    }
}
```

### Test Organization

**JUnit 5 Only:**
```java
class UserServiceTest {

    @Nested
    @DisplayName("User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("Should create user with valid data")
        void shouldCreateUserWithValidData() {
            // test
        }

        @Test
        @DisplayName("Should throw exception with invalid email")
        void shouldThrowExceptionWithInvalidEmail() {
            // test
        }
    }

    @Nested
    @DisplayName("User Update Tests")
    class UserUpdateTests {

        @Test
        void shouldUpdateUserName() {
            // test
        }
    }
}
```

## Migration from JUnit 4 to JUnit 5

### Automatic Migration

JAIPilot can help migrate tests:

1. **Regenerate with JUnit 5**:
   - Change setting to JUnit 5
   - Regenerate tests
   - Compare with old tests
   - Merge custom logic

2. **Use IDE migration tool**:
   - IntelliJ: `Refactor` → `Migrate` → `JUnit 4 to JUnit 5`

### Manual Migration Checklist

- [ ] Update dependencies in `build.gradle` or `pom.xml`
- [ ] Change imports from `org.junit` to `org.junit.jupiter.api`
- [ ] Replace `@Before` with `@BeforeEach`
- [ ] Replace `@After` with `@AfterEach`
- [ ] Replace `@BeforeClass` with `@BeforeAll` (add `static`)
- [ ] Replace `@AfterClass` with `@AfterAll` (add `static`)
- [ ] Replace `@Ignore` with `@Disabled`
- [ ] Update exception testing to use `assertThrows`
- [ ] Convert `@RunWith` to JUnit 5 extensions
- [ ] Update parameterized tests to use `@ParameterizedTest`

### Dependency Updates

**Maven (pom.xml):**

Remove:
```xml
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>
```

Add:
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>
```

**Gradle (build.gradle.kts):**

```kotlin
dependencies {
    // Remove
    // testImplementation("junit:junit:4.13.2")

    // Add
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}
```

## Configuring JAIPilot

### Set Default JUnit Version

1. Open `Settings/Preferences` → `Tools` → `JAIPilot`
2. Find **Test Framework** dropdown
3. Select `JUnit 4` or `JUnit 5`
4. Click `Apply`

### Auto-Detection

JAIPilot can auto-detect your project's JUnit version:

1. Enable `Auto-detect JUnit version`
2. JAIPilot scans your dependencies
3. Generates tests matching your project

## Best Practices

### For JUnit 4 Projects

- Consider migrating to JUnit 5 if possible
- Use ExpectedException rule for better exception testing
- Leverage @Category for test grouping
- Use @RunWith for custom test runners

### For JUnit 5 Projects

- Use `@DisplayName` for readable test names
- Organize tests with `@Nested` classes
- Leverage `@ParameterizedTest` for data-driven tests
- Use `assertAll()` for multiple related assertions:

```java
@Test
void testUserDetails() {
    User user = service.getUser(1);

    assertAll("user",
        () -> assertEquals("John", user.getName()),
        () -> assertEquals("john@example.com", user.getEmail()),
        () -> assertTrue(user.isActive())
    );
}
```

## Common Issues

### Mixed JUnit Versions

**Problem**: Both JUnit 4 and 5 in classpath

**Solution**:
```gradle
configurations.testImplementation {
    exclude group: 'junit', module: 'junit'
}
```

### Tests Not Running

**Problem**: JUnit 5 tests not discovered

**Solution**: Add to `build.gradle`:
```gradle
test {
    useJUnitPlatform()
}
```

### Migration Failures

**Problem**: Some tests fail after migration

**Solutions**:
- Check assertion imports changed correctly
- Verify exception tests use `assertThrows`
- Ensure lifecycle methods have correct annotations
- Update test runners to JUnit 5 extensions

## Resources

### JUnit 4
- [JUnit 4 Docs](https://junit.org/junit4/)
- [JUnit 4 Javadoc](https://junit.org/junit4/javadoc/latest/)

### JUnit 5
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JUnit 5 API Docs](https://junit.org/junit5/docs/current/api/)
- [Migration Tips](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)

## Next Steps

- Learn about [Generating Tests](generating-tests.md)
- See [Examples](../examples/)
- Read [Best Practices](best-practices.md)

## Need Help?

- 📚 [FAQ](../troubleshooting/faq.md)
- 💬 [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
