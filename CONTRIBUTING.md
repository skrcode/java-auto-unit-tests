# Contributing to JAIPilot

First off, thank you for considering contributing to JAIPilot! It's people like you that make JAIPilot such a great tool for the Java development community.

## 🌟 Ways to Contribute

There are many ways you can contribute to JAIPilot:

- 🐛 **Report bugs** and issues
- 💡 **Suggest new features** or enhancements
- 📖 **Improve documentation**
- 🔧 **Submit bug fixes** or new features
- 💬 **Help others** in GitHub Discussions
- ⭐ **Star the project** to increase visibility

## 🐛 Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

### Bug Report Template

```markdown
**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '...'
3. See error

**Expected behavior**
What you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Environment:**
- IntelliJ IDEA version: [e.g., 2024.2.3]
- JAIPilot version: [e.g., 1.0.9]
- Java version: [e.g., Java 17]
- OS: [e.g., macOS 14.0]

**Additional context**
Any other context about the problem.
```

## 💡 Suggesting Features

Feature suggestions are welcome! Please provide:

- **Clear description** of the feature
- **Use cases** — why is this feature needed?
- **Proposed solution** — how should it work?
- **Alternatives considered** — what other solutions did you think about?

## 🔧 Pull Request Process

### Before You Start

1. **Check existing issues** — is someone already working on this?
2. **Open an issue first** for major changes to discuss the approach
3. **Fork the repository** and create a branch from `main`

### Development Setup

1. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/java-auto-unit-tests.git
   cd java-auto-unit-tests
   ```

2. **Set up the development environment**
   ```bash
   ./gradlew build
   ```

3. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

### Code Guidelines

- **Follow Kotlin conventions** — Use standard Kotlin coding style
- **Write clear commit messages** — Use conventional commit format
  ```
  feat: add support for JUnit 5 parameterized tests
  fix: resolve NPE when generating tests for abstract classes
  docs: update installation instructions
  ```
- **Add tests** — Include unit tests for new functionality
- **Update documentation** — Update README.md if needed
- **Keep PRs focused** — One feature/fix per PR

### Testing Your Changes

Run the full test suite:
```bash
./gradlew test
```

Test the plugin locally in IntelliJ:
```bash
./gradlew runIde
```

### Submitting Your PR

1. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open a Pull Request** from your fork to `main`

3. **Fill out the PR template** with:
   - Description of changes
   - Related issue number (if applicable)
   - Testing performed
   - Screenshots (if UI changes)

4. **Wait for review** — maintainers will review and provide feedback

### PR Review Process

- Maintainers will review your PR within a few days
- Address review comments by pushing new commits
- Once approved, a maintainer will merge your PR
- Your contribution will be included in the next release

## 📝 Code Style

### Kotlin Style

Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// Good
class TestGenerator {
    fun generateTests(className: String): List<Test> {
        return emptyList()
    }
}

// Bad
class testGenerator {
    fun GenerateTests(className:String):List<Test>{
        return emptyList()
    }
}
```

### Naming Conventions

- **Classes**: PascalCase (e.g., `TestGenerator`)
- **Functions**: camelCase (e.g., `generateTests`)
- **Constants**: SCREAMING_SNAKE_CASE (e.g., `MAX_RETRIES`)
- **Variables**: camelCase (e.g., `testCount`)

## 🧪 Testing

- Write unit tests for all new features
- Ensure existing tests pass
- Aim for high code coverage
- Test edge cases and error scenarios

## 📖 Documentation

- Update README.md for user-facing changes
- Add KDoc comments for public APIs
- Update CHANGELOG.md with your changes
- Include code examples for new features

## 🤝 Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for everyone. Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

### Our Standards

- **Be respectful** — Treat everyone with respect
- **Be constructive** — Provide helpful feedback
- **Be patient** — Everyone has different experience levels
- **Be inclusive** — Welcome newcomers

## 📄 License

By contributing to JAIPilot, you agree that your contributions will be licensed under the [Mozilla Public License 2.0](LICENSE).

## 🎉 Recognition

Contributors will be:
- Listed in our Contributors section
- Mentioned in release notes
- Part of an amazing community

## 💬 Questions?

- 📖 Check the [Documentation](https://github.com/skrcode/java-auto-unit-tests/wiki)
- 💬 Ask in [GitHub Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- 📧 Email the maintainers

---

## 🙏 Thank You!

Your contributions make JAIPilot better for everyone. We appreciate your time and effort!

**Happy coding!** 🚀
