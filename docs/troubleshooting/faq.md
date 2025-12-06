# Frequently Asked Questions (FAQ)

Find answers to common questions about JAIPilot.

## General Questions

### What is JAIPilot?

JAIPilot is an AI-powered IntelliJ IDEA plugin that automatically generates JUnit tests for your Java code. It understands your code context, generates comprehensive test cases, runs them, and automatically fixes failures.

### Is JAIPilot free?

JAIPilot offers **40 request attempts** (~4 classes worth of test generation) as free credits on signup - no credit card required. You can start generating tests immediately. Once you use your free credits, you can top up with transparent pay-as-you-go pricing.

### What JUnit versions are supported?

JAIPilot supports both **JUnit 4** and **JUnit 5**. It can auto-detect which version your project uses or you can manually specify your preference in settings.

### What IntelliJ IDEA versions are compatible?

JAIPilot requires **IntelliJ IDEA 2023.1 or later**, including both Community and Ultimate editions.

### Does JAIPilot work with other JetBrains IDEs?

Currently, JAIPilot is designed specifically for IntelliJ IDEA. Support for other JetBrains IDEs (Android Studio, etc.) may be considered in future releases.

## Installation & Setup

### How do I install JAIPilot?

See our detailed [Installation Guide](../getting-started/installation.md). The quickest method:
1. Open IntelliJ IDEA
2. Go to Settings → Plugins → Marketplace
3. Search for "JAIPilot"
4. Click Install and restart

### Why can't I find JAIPilot in the marketplace?

Possible reasons:
- Your IntelliJ IDEA version is too old (need 2023.1+)
- Marketplace connection issue - try refreshing
- Use alternative installation: [Install from disk](../getting-started/installation.md#method-2-install-from-disk)

### How do I sign up and activate JAIPilot?

To get started:
1. Visit [jaipilot.com](https://jaipilot.com) and sign up (no credit card required)
2. You'll receive **40 request attempts** (~4 classes) worth of free credits
3. Check your email for your **license key**
4. You can also find your license key at [jaipilot.com/account](https://jaipilot.com/account)
5. In IntelliJ, go to **Settings** → **Tools** → **JAIPilot Plugin**
6. Paste your license key to activate test generation

### Where is my license key stored?

Your license key is stored securely in IntelliJ IDEA's built-in credential manager:
- Encrypted at rest
- Never logged or exposed (except for validation)
- Not shared with third parties
- Can be re-entered at Settings → Tools → JAIPilot Plugin

## Test Generation

### How does test generation work?

JAIPilot:
1. Analyzes your class/method code
2. Understands dependencies and logic
3. Generates test cases using AI models
4. Creates test file in appropriate location
5. Runs tests automatically
6. Fixes any failures iteratively

### How long does test generation take?

Typical generation time:
- **Simple classes**: 5-15 seconds
- **Medium complexity**: 15-30 seconds
- **Complex classes**: 30-60 seconds
- **Large classes**: 1-2 minutes

### Can I generate tests for a single method?

Yes! Place your cursor inside the method, right-click, and select "Generate Tests with JAIPilot". Only that method will be tested.

### Can I generate tests for multiple classes at once?

Yes! Select multiple files in the Project view, right-click, and choose "Generate Tests with JAIPilot". They'll be processed sequentially.

### What if tests fail after generation?

JAIPilot automatically detects and fixes failing tests:
1. Analyzes the failure
2. Regenerates the test
3. Runs it again
4. Repeats until passing

No manual intervention needed!

### Can I customize generated tests?

Yes! Generated tests are regular Java code:
- Edit them as needed
- Add your own test cases
- Modify assertions
- JAIPilot respects your changes

### What testing features are supported?

JAIPilot supports:
- ✅ Basic assertions (assertEquals, assertTrue, etc.)
- ✅ Exception testing (assertThrows, expected exceptions)
- ✅ Parameterized tests
- ✅ Test lifecycle methods (@Before, @BeforeEach, etc.)
- ✅ Mock dependencies (basic mocking)
- ⏳ Advanced mocking (Mockito) - coming soon
- ⏳ Integration tests - coming soon

## Compatibility & Integration

### What build tools are supported?

JAIPilot works with:
- ✅ Maven
- ✅ Gradle (Groovy and Kotlin DSL)
- ✅ Ant (with manual configuration)
- ✅ No build tool (manual classpath)

### Does JAIPilot work with Spring Boot?

Yes! JAIPilot can generate tests for Spring Boot applications, including:
- Service classes
- Controllers (basic tests)
- Utility classes
- Repositories (with some limitations)

**Note**: Advanced Spring features (context loading, autowiring) may require manual test adjustments.

### Can I use JAIPilot with Android projects?

JAIPilot works with Android projects in IntelliJ IDEA or Android Studio, but:
- Best for pure Java/Kotlin logic
- Android-specific components may need manual adjustments
- Robolectric/Android Test support is limited

### Does JAIPilot support Kotlin?

Currently, JAIPilot is optimized for **Java**. Kotlin support is planned for a future release.

### What about other test frameworks (TestNG, Spock)?

Currently, only JUnit 4 and JUnit 5 are supported. Other frameworks are on the roadmap:
- TestNG - planned
- Spock - under consideration
- Cucumber - under consideration

## Troubleshooting

### Tests aren't generating. What's wrong?

Common issues:
1. **License key not activated**: Activate your license key in Settings → Tools → JAIPilot Plugin
2. **No JUnit dependency**: Add JUnit to `pom.xml` or `build.gradle`
3. **Compilation errors**: Fix errors in source code first
4. **No credits remaining**: Check credits at [jaipilot.com/account](https://jaipilot.com/account) and top up if needed
5. **Wrong test directory**: Configure in Settings → Tools → JAIPilot

### Generated tests don't compile

Possible causes:
1. **Missing dependencies**: Ensure all project dependencies are resolved
2. **Package mismatch**: Check test package matches source package
3. **Import issues**: IntelliJ may need "Optimize Imports" (Ctrl+Alt+O)
4. **JUnit version**: Verify correct JUnit version in project

### Tests compile but won't run

Check these:
1. **Test directory**: Must be marked as "Test Sources" (green folder)
2. **JUnit in classpath**: Verify test dependencies
3. **Test runner**: Use IntelliJ's built-in JUnit runner
4. **Module structure**: Ensure tests are in correct module

### Plugin is slow or unresponsive

Performance tips:
1. **Close unnecessary projects**: Only keep active project open
2. **Increase IDE memory**: Adjust Xmx in Help → Edit Custom VM Options
3. **Use Fast AI model**: For simpler classes
4. **Update IntelliJ**: Ensure latest version
5. **Check system resources**: Close other memory-heavy applications

### License key not working

Solutions:
1. **Copy full license key**: Get it from email or [jaipilot.com/account](https://jaipilot.com/account)
2. **Remove extra spaces**: Ensure no spaces before/after when pasting
3. **Check internet connection**: JAIPilot requires network access to validate
4. **Firewall/proxy**: Ensure JAIPilot can reach validation servers
5. **Re-enter license key**: Settings → Tools → JAIPilot Plugin
6. **Contact support**: Email support@jaipilot.com if issue persists

### I'm getting "Insufficient credits" or "No credits remaining" errors

Options:
1. **Check credit balance**: Visit [jaipilot.com/account](https://jaipilot.com/account)
2. **Free credits used up**: You've used your 40 free request attempts
3. **Top up credits**: Purchase more credits with pay-as-you-go billing at your account page
4. **Contact support**: Email support@jaipilot.com for credit issues or inquiries

## Privacy & Security

### What data does JAIPilot collect?

JAIPilot collects:
- Code snippets for test generation (temporary, not stored)
- Usage metrics (anonymous)
- Error logs (for debugging)

JAIPilot does NOT collect:
- Personal information beyond email
- Full source code files
- Sensitive business logic
- API keys or secrets from your code

### Is my code secure?

Yes:
- Code transmitted over **encrypted HTTPS**
- Code used **only** for test generation
- Not stored permanently on servers
- Not used for model training
- Not shared with third parties

See our [Security Policy](../../SECURITY.md) for details.

### Can I use JAIPilot on proprietary code?

Yes! JAIPilot is designed for commercial use:
- Your code remains confidential
- Check your company's AI tool policies
- Consider air-gapped environments for highest sensitivity

### How do I report a security vulnerability?

**Do not report security issues publicly.**

Instead:
- Email: security@skrcode.com
- GitHub Security Advisory
- See [Security Policy](../../SECURITY.md) for details

## Pricing & Licensing

### What does the plugin cost?

JAIPilot uses a **credit-based system**:
- **40 request attempts free** (~4 classes) on signup - no credit card required
- **Pay-as-you-go** for additional credits after free tier
- No subscription required
- Transparent pricing - check [jaipilot.com/pricing](https://jaipilot.com/pricing)

### Is there a subscription plan?

Currently, JAIPilot uses pay-per-use credits with a generous free tier. Subscription plans may be introduced in the future based on user feedback.

### Can I use JAIPilot commercially?

Yes! JAIPilot can be used in commercial projects. Enterprise plans with additional features may be available - contact sales for details.

### What's the license for the plugin?

The JAIPilot plugin itself is licensed under **Mozilla Public License 2.0** (MPL 2.0). Generated tests are your code with no licensing restrictions.

### What's the refund policy?

Unused credits can be refunded within 30 days of purchase. Contact support with your request.

## Features & Roadmap

### What features are planned?

Upcoming features:
- **Mockito integration**: Advanced mocking support
- **Test coverage metrics**: Display coverage in IDE
- **Batch generation**: Generate tests for entire packages
- **Custom templates**: Configure test generation patterns
- **TestNG support**: Alternative test framework
- **Kotlin support**: Generate tests for Kotlin code

### Can I request a feature?

Absolutely! We welcome feature requests:
- [Open a feature request](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=feature_request.md)
- Include use case and expected behavior
- Vote on existing feature requests

### How do I contribute?

We welcome contributions! See our [Contributing Guide](../../CONTRIBUTING.md) for:
- Bug reports
- Feature suggestions
- Documentation improvements
- Code contributions

## Support

### How do I get help?

Multiple support channels:
- 📚 [Documentation](../README.md)
- 💬 [GitHub Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- 🐛 [Issue Tracker](https://github.com/skrcode/java-auto-unit-tests/issues)
- 📧 Email support (for account/billing issues)

### How do I report a bug?

1. Check [existing issues](https://github.com/skrcode/java-auto-unit-tests/issues)
2. Use [bug report template](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
3. Include:
   - IntelliJ IDEA version
   - JAIPilot version
   - Steps to reproduce
   - Expected vs actual behavior

### Where can I find examples?

Check our [examples directory](../examples/):
- Simple class testing
- Complex class testing
- Testing with dependencies
- Parameterized tests

### Is there a community?

Yes! Join our community:
- [GitHub Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- [JetBrains Marketplace Reviews](https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing/reviews)
- Star the project on [GitHub](https://github.com/skrcode/java-auto-unit-tests)

## Comparison with Other Tools

### How is JAIPilot different from manually writing tests?

| Aspect | Manual Testing | JAIPilot |
|--------|---------------|----------|
| Speed | Hours per class | Seconds per class |
| Coverage | Varies by developer | Comprehensive |
| Edge cases | Often missed | Automatically detected |
| Consistency | Varies | Uniform |
| Maintenance | Manual updates | Quick regeneration |

### How does JAIPilot compare to other test generators?

JAIPilot advantages:
- ✅ AI-powered (understands context)
- ✅ Automatic test fixing
- ✅ One-click integration
- ✅ Meaningful test names
- ✅ Comprehensive coverage

Other tools:
- Often template-based (less intelligent)
- Require manual fixing
- May produce brittle tests

### Should I use JAIPilot or write tests manually?

**Use JAIPilot for**:
- Initial test coverage
- Legacy code testing
- Regression test suites
- Time-sensitive projects

**Write manually for**:
- Specific test scenarios
- Complex business logic tests
- Integration tests
- Acceptance tests

**Best approach**: Combine both! Let JAIPilot create baseline coverage, then add specialized tests manually.

## Still Have Questions?

- 💬 [Ask in Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- 📖 [Browse Documentation](../README.md)
- 🐛 [Report an Issue](https://github.com/skrcode/java-auto-unit-tests/issues)
- ⭐ [Star us on GitHub](https://github.com/skrcode/java-auto-unit-tests)

---

**Last Updated**: December 2024
