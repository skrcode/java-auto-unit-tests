# Common Issues and Solutions

Quick solutions to frequently encountered problems with JAIPilot.

## Installation Issues

### Plugin Not Found in Marketplace

**Symptoms**: Cannot find JAIPilot when searching in JetBrains Marketplace.

**Causes**:
- IntelliJ IDEA version too old
- Marketplace connection issues
- Plugin not yet indexed

**Solutions**:
1. Update IntelliJ IDEA to 2023.1 or later
2. Refresh marketplace: Click refresh icon in Plugins window
3. Check internet connection
4. Alternative: [Install from disk](../getting-started/installation.md#method-2-install-from-disk)

### Installation Fails with Error

**Symptoms**: Error message during plugin installation.

**Solutions**:
1. **Check disk space**: Ensure at least 100MB free
2. **Verify permissions**: Make sure you can write to plugins directory
3. **Restart IntelliJ**: Close and reopen IntelliJ IDEA
4. **Clear plugin cache**: Delete `<IDE_CONFIG>/plugins/` cache folder
5. **Check logs**: `Help` → `Show Log in Explorer/Finder`

### Plugin Doesn't Load After Installation

**Symptoms**: Installed but menu items don't appear.

**Solutions**:
1. **Verify enabled**: Settings → Plugins → Installed → Check "JAIPilot" is checked
2. **Restart IDE**: Fully close and restart IntelliJ IDEA
3. **Check conflicts**: Disable other testing plugins temporarily
4. **Invalidate caches**: File → Invalidate Caches → Invalidate and Restart
5. **Reinstall**: Uninstall → Restart → Reinstall

## Authentication Issues

### Cannot Sign In

**Symptoms**: Sign-in process fails or hangs.

**Solutions**:
1. **Check internet**: Verify network connectivity
2. **Firewall/proxy**: Ensure authentication endpoint is accessible
3. **Browser issues**: Try clearing browser cookies/cache
4. **Use different browser**: Set different default browser temporarily
5. **Manual sign-in**: Settings → Tools → JAIPilot → Sign In

### Authentication Token Expired

**Symptoms**: "Authentication failed" errors during test generation.

**Solutions**:
1. **Sign out and back in**: Settings → Tools → JAIPilot
2. **Check credential storage**: Settings → Appearance & Behavior → System Settings → Passwords
3. **Enable password storage**: Ensure "Save passwords" is enabled
4. **Restart IDE**: Sometimes credential cache needs refresh

### Sign-In Window Not Opening

**Symptoms**: Browser doesn't open for sign-in.

**Solutions**:
1. **Check default browser**: Set a valid default browser
2. **Manual URL**: Copy authentication URL and open manually
3. **Browser permissions**: Allow IDE to open browser
4. **Alternative browser**: Temporarily change default browser

## Test Generation Issues

### Tests Not Generating

**Symptoms**: Right-click menu works but no tests are created.

**Diagnosis checklist**:
- ✅ Is JUnit in project dependencies?
- ✅ Does source code compile without errors?
- ✅ Are you signed in to JAIPilot?
- ✅ Do you have remaining credits?
- ✅ Is test directory configured correctly?

**Solutions**:
1. **Add JUnit dependency**:

   Maven:
   ```xml
   <dependency>
       <groupId>org.junit.jupiter</groupId>
       <artifactId>junit-jupiter</artifactId>
       <version>5.10.0</version>
       <scope>test</scope>
   </dependency>
   ```

   Gradle:
   ```gradle
   testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
   ```

2. **Fix compilation errors**: Resolve all errors in source file
3. **Sign in**: Settings → Tools → JAIPilot → Sign In
4. **Check credits**: Settings → Tools → JAIPilot → View Credits
5. **Verify test directory**: Settings → Tools → JAIPilot → Test Root Directory

### Tests Generated in Wrong Location

**Symptoms**: Tests appear in unexpected directory.

**Solutions**:
1. **Configure test root**: Settings → Tools → JAIPilot → Test Root Directory
2. **Mark as test source**: Right-click test folder → Mark Directory as → Test Sources Root
3. **Check module structure**: Ensure test directory in correct module
4. **Verify project structure**: File → Project Structure → Modules

### Wrong JUnit Version Used

**Symptoms**: Generated tests use JUnit 4 when you want JUnit 5 (or vice versa).

**Solutions**:
1. **Check dependencies**: Verify correct JUnit version in build file
2. **Configure explicitly**: Settings → Tools → JAIPilot → Test Framework
3. **Disable auto-detect**: Manually select preferred JUnit version
4. **Update dependencies**: Ensure only one JUnit version in classpath

### Test Generation Times Out

**Symptoms**: Generation takes too long and fails.

**Solutions**:
1. **Simplify class**: Break complex classes into smaller pieces
2. **Check network**: Ensure stable internet connection
3. **Use Fast model**: Settings → AI Model Selection → Fast
4. **Retry**: Sometimes temporary server load causes timeouts
5. **Increase timeout**: Set `JAIPILOT_TIMEOUT` environment variable

### Empty Test File Created

**Symptoms**: Test file created but contains no test methods.

**Solutions**:
1. **Check source class**: Ensure class has public methods to test
2. **Verify class compiles**: Fix compilation errors
3. **Review method complexity**: Very simple getters/setters may be skipped
4. **Regenerate**: Delete empty file and try again
5. **Check logs**: Look for error messages in IDE log

## Test Compilation Issues

### Generated Tests Don't Compile

**Symptoms**: Test file has compilation errors.

**Common causes**:

1. **Missing imports**: Click "Optimize Imports" (Ctrl+Alt+O / Cmd+Option+O)
2. **Wrong package**: Verify test package matches source package
3. **Missing dependencies**: Add required test dependencies
4. **Inner classes**: May need manual adjustment for complex inner classes
5. **Generics**: Complex generic types may need type hints

**Solutions**:
1. **Auto-import**: IntelliJ usually suggests imports automatically
2. **Manual imports**: Add missing imports manually
3. **Check dependencies**: Ensure all project dependencies resolved
4. **Simplify**: Refactor complex code before generating tests
5. **Report issue**: If consistently failing, report bug with code sample

### Unresolved References in Tests

**Symptoms**: Test references classes/methods that can't be resolved.

**Solutions**:
1. **Sync project**: File → Sync Project with Gradle/Maven Files
2. **Reimport**: Reimport Maven/Gradle project
3. **Check module dependencies**: File → Project Structure → Modules
4. **Verify classpath**: Ensure test code can access source code
5. **Mark directories**: Ensure source and test directories correctly marked

## Test Execution Issues

### Tests Won't Run

**Symptoms**: Generated tests exist but clicking "Run" does nothing.

**Solutions**:
1. **Mark as test sources**: Right-click directory → Mark Directory as → Test Sources Root
2. **Check JUnit in classpath**: Verify test scope dependencies
3. **Select correct runner**: Right-click test → Run with JUnit
4. **Verify module**: Ensure test in correct module
5. **Rebuild project**: Build → Rebuild Project

### Tests Fail After Generation

**Symptoms**: Generated tests run but fail.

**Normal behavior**: JAIPilot should automatically fix failing tests. Wait for the refinement process to complete.

**If auto-fix doesn't work**:
1. **Check test logic**: Review test assertions
2. **Verify setup**: Ensure @Before/@BeforeEach initializes correctly
3. **Check dependencies**: Mocked dependencies may need adjustment
4. **Manual fix**: Adjust test as needed
5. **Report issue**: Consistently failing auto-fix may indicate bug

### NullPointerException in Tests

**Symptoms**: Tests throw NPE during execution.

**Solutions**:
1. **Check setup**: Verify @Before/@BeforeEach initializes all fields
2. **Review constructor**: Ensure class constructor doesn't have side effects
3. **Mock dependencies**: Add mock initialization if needed
4. **Regenerate**: Try regenerating with fresh context
5. **Manual adjustment**: Add null checks or initialization

## Performance Issues

### Plugin is Slow

**Symptoms**: JAIPilot takes a long time to generate tests or IDE becomes sluggish.

**Solutions**:
1. **Increase IDE memory**: Help → Edit Custom VM Options → Increase -Xmx
2. **Use Fast AI model**: Settings → AI Model Selection → Fast
3. **Close unused projects**: Only keep active project open
4. **Generate incrementally**: Generate tests for one class at a time
5. **Update IntelliJ**: Ensure latest stable version
6. **Check system resources**: Close other memory-heavy applications

### IDE Freezes During Generation

**Symptoms**: IntelliJ becomes unresponsive during test generation.

**Solutions**:
1. **Wait**: Large classes may take 1-2 minutes
2. **Cancel operation**: Press Escape or click stop button
3. **Smaller chunks**: Generate tests for individual methods
4. **Background tasks**: Check Background Tasks indicator in bottom right
5. **Increase timeout**: Allow more time for complex generations

## Configuration Issues

### Settings Not Saving

**Symptoms**: Changes to JAIPilot settings don't persist.

**Solutions**:
1. **Click Apply**: Press "Apply" before "OK"
2. **Check permissions**: Verify write access to `.idea` directory
3. **Restart IDE**: Some settings require restart
4. **Reset settings**: Delete `.idea/jaipilot.xml` and reconfigure
5. **Check for conflicts**: Disable conflicting plugins

### Test Root Directory Not Recognized

**Symptoms**: Tests generated in wrong location despite settings.

**Solutions**:
1. **Use absolute path**: Try full path instead of relative
2. **Create directory**: Ensure directory exists before generation
3. **Mark as test root**: Manually mark directory in Project Structure
4. **Check syntax**: Ensure no trailing slashes or special characters
5. **Per-project setting**: Configure separately for each project

## Credit and Billing Issues

### Insufficient Credits Error

**Symptoms**: "Not enough credits" message when generating tests.

**Solutions**:
1. **Check balance**: Settings → Tools → JAIPilot → View Credits
2. **Get free credits**: Sign up if new user
3. **Purchase credits**: Visit account dashboard to buy more
4. **Contact support**: For credit discrepancies

### Credits Deducted But No Tests Generated

**Symptoms**: Credits used but no test file created.

**Solutions**:
1. **Check test directory**: Verify test file wasn't created elsewhere
2. **Review recent generations**: Check IDE event log
3. **Contact support**: Request credit refund for failed generation
4. **Retry**: Try generating again

## Advanced Issues

### Multi-Module Project Issues

**Symptoms**: Tests generated in wrong module or dependencies not resolved.

**Solutions**:
1. **Configure per module**: Set test directory for each module
2. **Check module dependencies**: File → Project Structure → Modules → Dependencies
3. **Test scope**: Ensure test code can access other modules
4. **Explicit configuration**: Manually specify test root per module

### Custom Build Configuration

**Symptoms**: JAIPilot doesn't work with custom project structure.

**Solutions**:
1. **Standard structure**: Try to align with Maven/Gradle conventions
2. **Manual configuration**: Explicitly set test root directory
3. **Mark directories**: Manually mark source and test roots
4. **Check build tool**: Ensure build tool is recognized by IntelliJ

### Generated Tests Don't Match Coding Standards

**Symptoms**: Tests don't follow your team's conventions.

**Solutions**:
1. **Manual adjustment**: Edit generated tests as needed
2. **Code formatter**: Apply your code style (Ctrl+Alt+L / Cmd+Option+L)
3. **Feature request**: Request custom template support
4. **Post-processing**: Use IDE refactoring tools

## Getting Help

### Before Reporting an Issue

Checklist:
1. ✅ Searched [existing issues](https://github.com/skrcode/java-auto-unit-tests/issues)
2. ✅ Checked [FAQ](faq.md)
3. ✅ Reviewed [documentation](../README.md)
4. ✅ Tried suggested solutions above
5. ✅ Updated to latest JAIPilot version

### How to Report a Bug

Include in your bug report:
1. **IntelliJ IDEA version**: Help → About
2. **JAIPilot version**: Settings → Plugins → Installed
3. **Java version**: Project SDK
4. **Steps to reproduce**: Detailed reproduction steps
5. **Expected behavior**: What should happen
6. **Actual behavior**: What actually happens
7. **Logs**: Include relevant log entries
8. **Code sample**: Minimal reproducible example (if possible)

Use our [bug report template](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md).

### Where to Get Help

- 📚 **Documentation**: [Read the docs](../README.md)
- 💬 **Discussions**: [Ask community](https://github.com/skrcode/java-auto-unit-tests/discussions)
- 🐛 **Bug reports**: [Open issue](https://github.com/skrcode/java-auto-unit-tests/issues/new)
- 📧 **Support**: Email for account/billing issues

## Still Stuck?

If you've tried everything and still having issues:

1. **Check IDE logs**: Help → Show Log in Explorer/Finder
2. **Enable debug mode**: Set `JAIPILOT_DEBUG=true` environment variable
3. **Provide details**: Include logs and configuration when asking for help
4. **Be patient**: Community support is volunteer-based

---

**Tip**: Most issues are related to project configuration. Double-check that JUnit is properly configured and your test directory is marked as "Test Sources Root" before generating tests.
