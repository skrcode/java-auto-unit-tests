# Security Policy

## Supported Versions

We take security seriously at JAIPilot. The following versions are currently supported with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We appreciate the security community's efforts in responsibly disclosing vulnerabilities. If you discover a security issue in JAIPilot, please follow these guidelines:

### How to Report

**Please DO NOT report security vulnerabilities through public GitHub issues.**

Instead, please report security vulnerabilities by:

1. **Email:** Send details to [security@skrcode.com](mailto:security@skrcode.com) (if this email is not available, use the repository owner's email)
2. **GitHub Security Advisory:** Use the [GitHub Security Advisory](https://github.com/skrcode/java-auto-unit-tests/security/advisories/new) feature

### What to Include

To help us triage and address the issue quickly, please include:

- **Type of vulnerability** (e.g., code injection, XSS, authentication bypass, etc.)
- **Full paths of source file(s)** related to the vulnerability
- **Location of the affected source code** (tag/branch/commit or direct URL)
- **Step-by-step instructions** to reproduce the issue
- **Proof-of-concept or exploit code** (if possible)
- **Impact assessment** - how an attacker might exploit the issue
- **Your assessment of severity** (Critical/High/Medium/Low)

### What to Expect

After you submit a report, here's what you can expect:

1. **Acknowledgment:** We'll acknowledge receipt of your report within **48 hours**
2. **Initial Assessment:** We'll provide an initial assessment within **5 business days**
3. **Status Updates:** We'll keep you informed about our progress
4. **Resolution Timeline:** We aim to address critical vulnerabilities within **90 days**
5. **Public Disclosure:** We'll coordinate with you on public disclosure timing

### Security Update Process

When we receive a security report:

1. We confirm the vulnerability and determine its impact
2. We develop and test a fix
3. We prepare a security advisory
4. We release a patched version
5. We publish the security advisory

### Bug Bounty Program

At this time, we do not offer a paid bug bounty program. However, we deeply appreciate security researchers' contributions and will:

- Publicly acknowledge your responsible disclosure (with your permission)
- Credit you in our security advisories
- Add you to our security hall of fame in this document

## Security Best Practices for Users

To keep your JAIPilot installation secure:

### 1. Keep JAIPilot Updated

Always use the latest version of JAIPilot to benefit from security patches:
- Enable automatic updates in IntelliJ IDEA
- Check for updates regularly via **Settings** → **Plugins**

### 2. API Key Security

- **Never commit API keys** to version control
- Store API keys securely in your IDE's credential store
- Rotate API keys periodically
- Revoke keys immediately if compromised

### 3. Network Security

- Use JAIPilot only on trusted networks
- Be cautious when generating tests on public Wi-Fi
- Consider using a VPN for additional security

### 4. Code Review

- Review generated tests before committing
- Don't blindly trust AI-generated code
- Ensure tests don't expose sensitive information

### 5. Permissions

- Only grant JAIPilot necessary permissions
- Review what data is sent to AI models
- Be mindful of proprietary code in test generation

## Known Security Considerations

### Data Transmission

JAIPilot sends code snippets to AI models for test generation:
- Code is transmitted over **encrypted HTTPS** connections
- Data is **not stored** permanently on our servers
- Code is used **only** for test generation purposes

### API Key Storage

- API keys are stored in IntelliJ IDEA's secure credential storage
- Keys are **never logged** or transmitted to third parties
- Keys are **encrypted** at rest

### Test Generation

- Generated tests may include sensitive data from your source code
- Review tests before committing to ensure no secrets are exposed
- Use `.gitignore` to prevent accidental exposure of sensitive test data

## Security Hall of Fame

We'd like to thank the following security researchers for responsibly disclosing vulnerabilities:

<!-- Will be updated as vulnerabilities are reported and fixed -->

*No vulnerabilities have been reported yet. Be the first to help us improve JAIPilot's security!*

## Compliance

JAIPilot is committed to:

- **GDPR compliance** for European users
- **SOC 2** compliance standards
- **OWASP Top 10** security best practices
- Regular security audits and assessments

## Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [IntelliJ Plugin Security Guidelines](https://plugins.jetbrains.com/docs/intellij/security.html)

## Questions?

If you have questions about this security policy, please contact:
- **Email:** security@skrcode.com
- **GitHub Discussions:** [Security Category](https://github.com/skrcode/java-auto-unit-tests/discussions/categories/security)

---

**Last Updated:** December 6, 2024

Thank you for helping keep JAIPilot and its users safe!
