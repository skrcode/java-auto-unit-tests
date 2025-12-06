<div align="center">
  <h1>JAIPilot</h1>
  <p><strong>One-Click AI Agent for Java Unit Testing</strong></p>

  <p>
    <a href="https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing">
      <img src="https://img.shields.io/jetbrains/plugin/v/27706?label=Version&logo=jetbrains&style=for-the-badge" alt="Version">
    </a>
    <a href="https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing">
      <img src="https://img.shields.io/jetbrains/plugin/d/27706?label=Downloads&logo=jetbrains&style=for-the-badge" alt="Downloads">
    </a>
    <a href="https://github.com/skrcode/java-auto-unit-tests/actions">
      <img src="https://img.shields.io/github/actions/workflow/status/skrcode/java-auto-unit-tests/build.yml?branch=main&label=Build&logo=github&style=for-the-badge" alt="Build">
    </a>
    <a href="https://github.com/skrcode/java-auto-unit-tests/blob/main/LICENSE">
      <img src="https://img.shields.io/badge/License-MPL%202.0-blue?style=for-the-badge" alt="License">
    </a>
  </p>

  <p>
    <a href="#-features">Features</a> •
    <a href="#-installation">Installation</a> •
    <a href="#-quick-start">Quick Start</a> •
    <a href="#-documentation">Documentation</a> •
    <a href="#-contributing">Contributing</a>
  </p>

  <p><em>Generate, run, and fix JUnit tests automatically — right inside IntelliJ IDEA</em></p>
</div>

---

## Why JAIPilot?

Writing comprehensive unit tests is time-consuming. **JAIPilot** eliminates the tedious work by generating intelligent, context-aware JUnit tests in seconds. Focus on building features while JAIPilot ensures your code is thoroughly tested.

### The Problem
- Writing unit tests manually is repetitive and time-consuming
- Achieving high test coverage requires significant effort
- Fixing failing tests often involves multiple iterations
- Keeping tests up-to-date with code changes is tedious

### The Solution
JAIPilot is an AI-powered IntelliJ IDEA plugin that:
- **Understands your code** context, dependencies, and edge cases
- **Generates complete tests** with meaningful assertions in seconds
- **Automatically fixes** failing tests until they pass
- **Integrates seamlessly** with your existing JUnit workflow

---

## ✨ Features

### 🤖 AI-Powered Test Generation
- **Context-aware analysis** — understands method logic, parameters, dependencies, and edge cases
- **Intelligent test creation** — generates meaningful test cases with proper assertions
- **Multiple test frameworks** — supports JUnit 4 and JUnit 5

### ⚡ One-Click Automation
- **Right-click any class or method** to instantly generate comprehensive test suites
- **Batch generation** — create tests for multiple classes at once
- **Custom test locations** — specify your preferred test root directory (e.g., `src/test/java`)

### 🔄 Autonomous Test Refinement
- **Automatic test execution** — runs generated tests immediately
- **Smart failure detection** — identifies and analyzes test failures
- **Self-healing tests** — automatically fixes failing tests until they pass
- **Iterative improvement** — refines tests based on compilation and runtime errors

### 🎯 Optimized Performance
- **Dynamic model selection** — chooses the best AI model per class for speed and accuracy
- **Fast generation** — creates tests in seconds, not minutes
- **Resource efficient** — minimal impact on IDE performance

### 🔗 Seamless Integration
- **Works out-of-the-box** with existing Java projects
- **JUnit integration** — fully compatible with your test infrastructure
- **IDE native experience** — feels like a built-in IntelliJ feature
- **No configuration required** — start generating tests immediately

### 🆓 Free Credits to Get Started
- **40 request attempts free on signup** (~4 classes worth of test generation)
- **No credit card required** — start generating tests immediately
- **Pay-as-you-go billing** — top up credits after free tier
- **License key activation** — simple setup via email after registration

---

## 📦 Installation

### Option 1: Install from JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for **"JAIPilot"** or **"java-auto-unit-tests"**
4. Click **Install** and restart your IDE

<div align="center">
  <a href="https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing">
    <img src="https://img.shields.io/badge/Install_from-JetBrains_Marketplace-000000?style=for-the-badge&logo=jetbrains" alt="Install from JetBrains Marketplace">
  </a>
</div>

### Option 2: Install from Disk

1. Download the [latest release](https://github.com/skrcode/java-auto-unit-tests/releases/latest)
2. Open IntelliJ IDEA
3. Go to **Settings/Preferences** → **Plugins** → **⚙️** → **Install Plugin from Disk...**
4. Select the downloaded `.zip` file and restart your IDE

---

## 🚀 Quick Start

### 1. Install and Activate JAIPilot

1. Install JAIPilot from the JetBrains Marketplace and restart IntelliJ
2. Sign up for a JAIPilot account at [jaipilot.com](https://jaipilot.com) (no credit card required)
3. You'll receive **40 request attempts (~4 classes)** worth of free credits
4. Check your email for the **license key** (also available in your JAIPilot account page)
5. In IntelliJ, go to **Settings/Preferences** → **Tools** → **JAIPilot Plugin**
6. Paste your license key to activate test generation

### 2. Generate Your First Test

```java
// Your Java class
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new IllegalArgumentException("Cannot divide by zero");
        return a / b;
    }
}
```

**Steps:**
1. Right-click on the `Calculator` class
2. Select **"Generate Tests with JAIPilot"**
3. Choose your test root directory (e.g., `src/test/java`)
4. Wait a few seconds while JAIPilot generates comprehensive tests
5. Review the generated test file with full coverage

**Generated Test (Example):**
```java
public class CalculatorTest {
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
    void testDivide_ValidInput() {
        assertEquals(2, calculator.divide(6, 3));
    }

    @Test
    void testDivide_ThrowsExceptionWhenDivideByZero() {
        assertThrows(IllegalArgumentException.class, () -> calculator.divide(10, 0));
    }
}
```

### 3. Let JAIPilot Fix Failing Tests

If any tests fail:
- JAIPilot automatically detects failures
- Analyzes error messages and stack traces
- Regenerates and fixes tests until they pass
- No manual intervention required

---

## 📚 Documentation

### Configuration

Configure JAIPilot settings in **Settings/Preferences** → **Tools** → **JAIPilot**:

- **License Key** — enter your license key from email or account page to activate the plugin
- **Test Root Directory** — specify where tests should be generated (default: `src/test/java`)
- **Test Framework** — choose between JUnit 4 and JUnit 5
- **AI Model Selection** — automatic (recommended) or manual model selection

> **Important:** You must activate your license key before generating tests. After signing up at [jaipilot.com](https://jaipilot.com), you'll receive a license key via email. Paste this key in the plugin settings to start using JAIPilot.

### Supported Features

| Feature | JUnit 4 | JUnit 5 |
|---------|---------|---------|
| Basic test generation | ✅ | ✅ |
| Parameterized tests | ✅ | ✅ |
| Exception testing | ✅ | ✅ |
| Mock dependencies | ✅ | ✅ |
| Test lifecycle hooks | ✅ | ✅ |

### Requirements

- **IntelliJ IDEA** 2023.1 or later (Community or Ultimate)
- **Java** 8 or later
- **JUnit** 4.x or 5.x in your project dependencies

### Troubleshooting

**Issue:** Tests not generating
**Solution:** Ensure your license key is activated in plugin settings and your project has JUnit dependencies

**Issue:** License key not working
**Solution:** Copy the full license key from your email or account page. Ensure there are no extra spaces.

**Issue:** "No credits remaining" error
**Solution:** Top up your credits at [jaipilot.com/account](https://jaipilot.com/account) with pay-as-you-go billing

**Issue:** Tests failing after generation
**Solution:** Wait for JAIPilot's automatic refinement process to complete

For more issues, check our [FAQ](docs/troubleshooting/faq.md) or [open an issue](https://github.com/skrcode/java-auto-unit-tests/issues).

---

## 🤝 Contributing

We welcome contributions from the community! Whether you're fixing bugs, adding features, or improving documentation, your help is appreciated.

### Ways to Contribute

- 🐛 [Report bugs](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
- 💡 [Suggest features](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=feature_request.md)
- 📖 Improve documentation
- 🔧 Submit pull requests
- ⭐ Star the project to show your support

See our [Contributing Guide](CONTRIBUTING.md) for detailed instructions.

---

## 📊 Project Stats

<div align="center">
  <img src="https://img.shields.io/github/stars/skrcode/java-auto-unit-tests?style=social" alt="GitHub stars">
  <img src="https://img.shields.io/github/forks/skrcode/java-auto-unit-tests?style=social" alt="GitHub forks">
  <img src="https://img.shields.io/github/watchers/skrcode/java-auto-unit-tests?style=social" alt="GitHub watchers">
  <img src="https://img.shields.io/github/issues/skrcode/java-auto-unit-tests" alt="GitHub issues">
  <img src="https://img.shields.io/github/issues-pr/skrcode/java-auto-unit-tests" alt="GitHub pull requests">
  <img src="https://img.shields.io/github/contributors/skrcode/java-auto-unit-tests" alt="GitHub contributors">
</div>

---

## 📝 License

This project is licensed under the **Mozilla Public License 2.0** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- Built with the [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- Powered by advanced AI models for intelligent test generation
- Inspired by the developer community's need for better testing tools

---

## 🔗 Links

- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/27706-jaipilot--one-click-ai-agent-for-java-unit-testing)
- [Documentation](https://github.com/skrcode/java-auto-unit-tests/wiki)
- [Changelog](CHANGELOG.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Issue Tracker](https://github.com/skrcode/java-auto-unit-tests/issues)
- [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)

---

<div align="center">
  <p><strong>Built with ❤️ by developers, for developers</strong></p>
  <p>If JAIPilot helps you ship faster, please consider giving it a ⭐ on GitHub!</p>

  <p>
    <a href="https://github.com/skrcode/java-auto-unit-tests">
      <img src="https://img.shields.io/badge/⭐_Star_on-GitHub-181717?style=for-the-badge&logo=github" alt="Star on GitHub">
    </a>
  </p>
</div>
