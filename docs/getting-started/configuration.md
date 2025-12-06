# Configuration Guide

Learn how to configure JAIPilot to match your project's needs and workflow.

## Accessing Settings

Open JAIPilot settings:

- **Windows/Linux**: `File` → `Settings` → `Tools` → `JAIPilot`
- **macOS**: `IntelliJ IDEA` → `Preferences` → `Tools` → `JAIPilot`
- **Keyboard**: `Ctrl+Alt+S` (Windows/Linux) or `Cmd+,` (macOS), then search for "JAIPilot"

## Configuration Options

### Test Root Directory

**Description**: The directory where generated tests will be placed.

**Default**: `src/test/java`

**When to change**:
- Your project uses a non-standard test directory
- Multi-module projects with different test directories
- Custom build configurations

**Example configurations**:
```
Standard Maven/Gradle:     src/test/java
Custom structure:          test/java
Multi-module:              module-name/src/test/java
Android:                   app/src/test/java
```

**How to configure**:
1. Open settings: `Tools` → `JAIPilot`
2. Find "Test Root Directory" field
3. Enter your custom path
4. Click "Apply" or "OK"

### Test Framework

**Description**: Choose between JUnit 4 and JUnit 5 for test generation.

**Options**:
- **Auto-detect** (Recommended): JAIPilot detects your project's JUnit version
- **JUnit 4**: Force generation of JUnit 4 tests
- **JUnit 5**: Force generation of JUnit 5 tests

**When to change**:
- Migrating from JUnit 4 to JUnit 5
- Working with mixed JUnit versions
- Project has both but you prefer one

**JUnit 4 Example**:
```java
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CalculatorTest {
    @Before
    public void setUp() { }

    @Test
    public void testAdd() { }
}
```

**JUnit 5 Example**:
```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    @BeforeEach
    void setUp() { }

    @Test
    void testAdd() { }
}
```

### AI Model Selection

**Description**: Control which AI model is used for test generation.

**Options**:
- **Automatic** (Recommended): JAIPilot chooses the best model per class
- **Fast**: Prioritize speed over comprehensiveness
- **Balanced**: Balance between speed and test quality
- **Comprehensive**: Generate most thorough tests (slower)

**When to change**:
- Large classes requiring detailed tests → Use Comprehensive
- Simple classes needing quick tests → Use Fast
- Budget constraints → Use Fast
- Maximum coverage needed → Use Comprehensive

### License Key Activation

**Description**: Activate JAIPilot using your license key to enable test generation.

**How to activate**:
1. Sign up at [jaipilot.com](https://jaipilot.com) (no credit card required)
2. Receive **40 request attempts** (~4 classes) worth of free credits
3. Copy your **license key** from:
   - Your signup confirmation email, OR
   - Your account page at [jaipilot.com/account](https://jaipilot.com/account)
4. Open IntelliJ: `Settings` → `Tools` → `JAIPilot Plugin`
5. Paste your license key in the "License Key" field
6. Click "Apply" or "OK"

**Important notes**:
- You must activate your license key before generating tests
- Keep your license key safe - you'll need it for reinstalls or other machines
- License keys are tied to your account and credits
- Check remaining credits at [jaipilot.com/account](https://jaipilot.com/account)
- Top up credits with pay-as-you-go billing when you run out

**License key storage**:
- Stored securely in IntelliJ's credential manager
- Never logged or transmitted to third parties (except for validation)
- Encrypted at rest

## Project-Specific Configuration

### Configuring via .idea Directory

JAIPilot stores per-project settings in `.idea/jaipilot.xml` (auto-generated).

**Example**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="JAIPilotSettings">
    <option name="testRootDirectory" value="src/test/java" />
    <option name="junitVersion" value="JUNIT5" />
    <option name="aiModelPreference" value="AUTO" />
  </component>
</project>
```

**Note**: This file is typically gitignored. Commit it if you want team-wide settings.

### Multi-Module Projects

For multi-module projects, configure each module separately:

1. Open module's source file
2. Right-click → "Generate Tests with JAIPilot"
3. Select appropriate test directory for that module
4. JAIPilot remembers the setting for future generations

## Advanced Configuration

### Custom Test Templates

Currently, JAIPilot generates tests based on best practices. Custom templates are planned for future releases.

**Upcoming features**:
- Custom test method naming conventions
- Preferred assertion styles
- Mock library preferences (Mockito, EasyMock, etc.)

### Ignored Files and Packages

**Want to exclude certain classes from test generation?**

Currently, you can:
- Simply don't invoke JAIPilot on files you want to skip
- Delete generated tests you don't need

**Upcoming features**:
- Exclude patterns (e.g., `**/generated/**`)
- Package-level exclusions
- Annotation-based exclusions (e.g., `@NoTests`)

## Integration Settings

### IDE Integration

JAIPilot integrates with IntelliJ's testing infrastructure:

- **Test Runner**: Uses IntelliJ's built-in JUnit runner
- **Coverage**: Compatible with IntelliJ's coverage tools
- **Debugger**: Generated tests work with IntelliJ debugger

### Build Tool Integration

JAIPilot works with:
- ✅ **Maven**: Standard Maven project structure
- ✅ **Gradle**: Standard Gradle project structure
- ✅ **Ant**: Custom configuration may be needed
- ✅ **No build tool**: Manual classpath configuration

## Troubleshooting Configuration

### Tests Generated in Wrong Directory

**Problem**: Tests appear in unexpected locations.

**Solutions**:
1. Verify "Test Root Directory" in settings
2. Ensure test directory is marked as "Test Sources" (green folder icon)
3. Right-click test directory → "Mark Directory as" → "Test Sources Root"

### Wrong JUnit Version Used

**Problem**: Generated tests use wrong JUnit version.

**Solutions**:
1. Check "Test Framework" setting
2. Verify JUnit dependencies in `pom.xml` or `build.gradle`
3. Manually select correct version in settings (disable auto-detect)

### License Key Not Working

**Problem**: License key not being accepted or not persisting.

**Solutions**:
1. Copy the full license key from your email or [jaipilot.com/account](https://jaipilot.com/account)
2. Ensure there are no extra spaces or characters when pasting
3. Check IDE credential storage: `Settings` → `Appearance & Behavior` → `System Settings` → `Passwords`
4. Enable "Save passwords" if disabled
5. Try re-entering the license key
6. Check firewall/antivirus isn't blocking credential storage
7. Contact support at support@jaipilot.com if issue persists

### Settings Not Saving

**Problem**: Changes to settings don't persist.

**Solutions**:
1. Click "Apply" before "OK"
2. Check write permissions in `.idea` directory
3. Restart IntelliJ IDEA after changes
4. Check for plugin conflicts

## Best Practices

### Recommended Settings

For most projects, we recommend:

```
Test Root Directory:  src/test/java (or project default)
Test Framework:       Auto-detect
AI Model Selection:   Automatic
```

### Team Configuration

**Sharing settings across a team**:

1. Configure JAIPilot settings in one project
2. Commit `.idea/jaipilot.xml` to version control
3. Team members will inherit the same settings
4. Document custom settings in project README

**Example team documentation**:
```markdown
## JAIPilot Configuration

This project uses:
- Test directory: `src/test/java`
- JUnit 5 for all tests
- Auto-detect AI model selection

Configure via: Settings → Tools → JAIPilot
```

### Performance Optimization

**For large projects**:
- Use "Fast" AI model for simple classes
- Generate tests incrementally (class by class)
- Avoid regenerating tests unnecessarily

**For complex projects**:
- Use "Comprehensive" model for critical classes
- Review generated tests before committing
- Customize tests as needed after generation

## Environment Variables

JAIPilot respects these environment variables (advanced usage):

```bash
# Custom API endpoint (for enterprise users)
JAIPILOT_API_URL=https://api.custom-domain.com

# Debug mode (verbose logging)
JAIPILOT_DEBUG=true

# Timeout for test generation (seconds)
JAIPILOT_TIMEOUT=120
```

**Set in IntelliJ**:
1. `Run` → `Edit Configurations`
2. Select your configuration
3. Add to "Environment variables"

## Keyboard Shortcuts

Configure keyboard shortcuts for JAIPilot actions:

1. Go to `Settings` → `Keymap`
2. Search for "JAIPilot"
3. Right-click action → "Add Keyboard Shortcut"

**Suggested shortcuts**:
- **Generate Tests**: `Ctrl+Shift+T` (Windows/Linux) or `Cmd+Shift+T` (macOS)
- **Regenerate Tests**: `Ctrl+Shift+R` (Windows/Linux) or `Cmd+Shift+R` (macOS)

## Configuration Examples

### Example 1: Standard Maven Project

```
Test Root Directory:  src/test/java
Test Framework:       Auto-detect (JUnit 5)
AI Model:            Automatic
```

### Example 2: Android Project

```
Test Root Directory:  app/src/test/java
Test Framework:       JUnit 4
AI Model:            Fast (for quick iterations)
```

### Example 3: Multi-Module Gradle Project

```
Module A:
  Test Root Directory:  module-a/src/test/java
  Test Framework:       JUnit 5

Module B:
  Test Root Directory:  module-b/src/test/java
  Test Framework:       JUnit 5
```

### Example 4: Legacy Project Migration

```
Test Root Directory:  tests/unit
Test Framework:       JUnit 4 (migrating to JUnit 5)
AI Model:            Balanced
```

## Exporting and Importing Configuration

### Export Settings

**For backup or sharing**:

1. Go to `File` → `Manage IDE Settings` → `Export Settings`
2. Check "JAIPilot" in the list
3. Choose export location
4. Click "OK"

### Import Settings

**To restore or apply shared settings**:

1. Go to `File` → `Manage IDE Settings` → `Import Settings`
2. Select the exported settings file
3. Check "JAIPilot"
4. Click "OK"
5. Restart IntelliJ IDEA

## Next Steps

- 🚀 [Generate your first test](quick-start.md)
- 📖 [Learn about test generation](../guides/generating-tests.md)
- 🔧 [Best practices for using JAIPilot](../guides/best-practices.md)

## Need Help?

- 📚 [FAQ](../troubleshooting/faq.md)
- 🐛 [Report Issues](https://github.com/skrcode/java-auto-unit-tests/issues)
- 💬 [Ask Questions](https://github.com/skrcode/java-auto-unit-tests/discussions)
