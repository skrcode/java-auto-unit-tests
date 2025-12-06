# Changelog

All notable changes to JAIPilot will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Support for Mockito integration
- Test coverage metrics display
- Batch test generation for entire packages
- Custom AI model configuration
- Integration with popular testing frameworks (TestNG, Spock)

## [1.0.9] - 2024-12-06

### Added
- Enhanced test generation accuracy for complex methods
- Improved error handling and user feedback
- Better support for parameterized tests

### Fixed
- Issue with test generation for classes with inner classes
- Authentication token refresh mechanism
- Test file path resolution on Windows

### Changed
- Updated AI model selection algorithm for better performance
- Improved test assertion generation logic

## [1.0.8] - 2024-11-28

### Added
- Support for JUnit 5 lifecycle annotations
- Automatic mock generation for dependencies
- Enhanced edge case detection

### Fixed
- NPE when generating tests for abstract classes
- Test execution errors in multi-module projects

## [1.0.7] - 2024-11-15

### Added
- Custom test root directory selection
- Batch test regeneration for failed tests
- Progress indicators for long-running operations

### Changed
- Improved test naming conventions
- Enhanced code context analysis

## [1.0.6] - 2024-11-01

### Added
- Support for JUnit 4 and JUnit 5
- Automatic test execution and refinement
- Context-aware test generation

### Fixed
- Issues with generic type handling
- Test generation for methods with varargs

## [1.0.5] - 2024-10-15

### Added
- Initial public release on JetBrains Marketplace
- AI-powered test generation
- One-click test creation from context menu
- Automatic test fixing on failures

### Changed
- Migrated from internal beta to public release
- Updated plugin branding to JAIPilot

## [0.0.2] - 2024-06-21

### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Basic plugin structure
- Project configuration

---

## Release Notes

### Version 1.0.9 Highlights
This release focuses on stability improvements and enhanced test generation accuracy. We've addressed several edge cases in test creation and improved the overall user experience.

### Version 1.0.5 Highlights
Our first public release! JAIPilot is now available to all Java developers on the JetBrains Marketplace. This release includes all core features for AI-powered unit test generation.

---

[Unreleased]: https://github.com/skrcode/java-auto-unit-tests/compare/v1.0.9...HEAD
[1.0.9]: https://github.com/skrcode/java-auto-unit-tests/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/skrcode/java-auto-unit-tests/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/skrcode/java-auto-unit-tests/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/skrcode/java-auto-unit-tests/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/skrcode/java-auto-unit-tests/compare/v0.0.2...v1.0.5
[0.0.2]: https://github.com/skrcode/java-auto-unit-tests/commits/v0.0.2
