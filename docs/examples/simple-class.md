# Example: Testing a Simple Class

This example demonstrates how JAIPilot generates tests for a simple utility class.

## Source Code

Let's test a simple `StringUtils` class:

```java
package com.example.utils;

public class StringUtils {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String reverse(String str) {
        if (str == null) {
            return null;
        }
        return new StringBuilder(str).reverse().toString();
    }

    public static int countWords(String str) {
        if (isEmpty(str)) {
            return 0;
        }
        String[] words = str.trim().split("\\s+");
        return words.length;
    }
}
```

## Generating Tests

1. Open `StringUtils.java` in IntelliJ IDEA
2. Right-click in the editor
3. Select **"Generate Tests with JAIPilot"**
4. Choose test directory (e.g., `src/test/java`)
5. Wait for generation to complete

## Generated Tests

JAIPilot generates comprehensive tests:

```java
package com.example.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void testIsEmpty_WithNull() {
        assertTrue(StringUtils.isEmpty(null));
    }

    @Test
    void testIsEmpty_WithEmptyString() {
        assertTrue(StringUtils.isEmpty(""));
    }

    @Test
    void testIsEmpty_WithWhitespace() {
        assertTrue(StringUtils.isEmpty("   "));
    }

    @Test
    void testIsEmpty_WithNonEmptyString() {
        assertFalse(StringUtils.isEmpty("hello"));
    }

    @Test
    void testCapitalize_WithNull() {
        assertNull(StringUtils.capitalize(null));
    }

    @Test
    void testCapitalize_WithEmptyString() {
        assertEquals("", StringUtils.capitalize(""));
    }

    @Test
    void testCapitalize_WithLowercaseString() {
        assertEquals("Hello", StringUtils.capitalize("hello"));
    }

    @Test
    void testCapitalize_WithAlreadyCapitalized() {
        assertEquals("Hello", StringUtils.capitalize("Hello"));
    }

    @Test
    void testCapitalize_WithSingleCharacter() {
        assertEquals("A", StringUtils.capitalize("a"));
    }

    @Test
    void testReverse_WithNull() {
        assertNull(StringUtils.reverse(null));
    }

    @Test
    void testReverse_WithEmptyString() {
        assertEquals("", StringUtils.reverse(""));
    }

    @Test
    void testReverse_WithRegularString() {
        assertEquals("olleh", StringUtils.reverse("hello"));
    }

    @Test
    void testReverse_WithPalindrome() {
        assertEquals("racecar", StringUtils.reverse("racecar"));
    }

    @Test
    void testCountWords_WithNull() {
        assertEquals(0, StringUtils.countWords(null));
    }

    @Test
    void testCountWords_WithEmptyString() {
        assertEquals(0, StringUtils.countWords(""));
    }

    @Test
    void testCountWords_WithSingleWord() {
        assertEquals(1, StringUtils.countWords("hello"));
    }

    @Test
    void testCountWords_WithMultipleWords() {
        assertEquals(5, StringUtils.countWords("the quick brown fox jumps"));
    }

    @Test
    void testCountWords_WithExtraWhitespace() {
        assertEquals(3, StringUtils.countWords("  hello   world  test  "));
    }
}
```

## What JAIPilot Did

### 1. Edge Case Detection

JAIPilot automatically identified and tested:
- `null` inputs
- Empty strings
- Whitespace-only strings
- Single characters
- Already-capitalized strings
- Palindromes
- Extra whitespace

### 2. Complete Coverage

All four methods are tested with multiple scenarios:
- Normal use cases
- Boundary conditions
- Error conditions

### 3. Meaningful Test Names

Test names clearly describe what's being tested:
- `testIsEmpty_WithNull`
- `testCapitalize_WithLowercaseString`
- `testReverse_WithPalindrome`
- `testCountWords_WithExtraWhitespace`

## Running the Tests

Run tests in IntelliJ IDEA:

1. **All tests**: Right-click on the test class → Run 'StringUtilsTest'
2. **Single test**: Click the green arrow next to the test method
3. **With coverage**: Run 'StringUtilsTest' with Coverage

Expected result: ✅ All tests pass

## Customizing Generated Tests

You can enhance the generated tests:

### Add More Edge Cases

```java
@Test
void testCapitalize_WithNumbers() {
    assertEquals("123abc", StringUtils.capitalize("123abc"));
}

@Test
void testCapitalize_WithSpecialCharacters() {
    assertEquals("!hello", StringUtils.capitalize("!hello"));
}
```

### Add Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t", "\n"})
void testIsEmpty_WithVariousWhitespace(String input) {
    assertTrue(StringUtils.isEmpty(input));
}
```

### Group Related Tests

```java
@Nested
@DisplayName("isEmpty() tests")
class IsEmptyTests {
    @Test
    void withNull() {
        assertTrue(StringUtils.isEmpty(null));
    }

    @Test
    void withEmptyString() {
        assertTrue(StringUtils.isEmpty(""));
    }
}
```

## Best Practices

1. **Review generated tests**: Always verify assertions match expectations
2. **Run tests immediately**: Ensure they pass before committing
3. **Add business logic tests**: JAIPilot covers technical cases; add domain-specific ones
4. **Refactor if needed**: Extract common test data or setup methods

## Next Steps

- See [Complex Class Example](complex-class.md) for more advanced scenarios
- Learn about [Test Refinement](../guides/test-refinement.md)
- Check [Best Practices](../guides/best-practices.md)
