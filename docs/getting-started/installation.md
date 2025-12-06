# Installation Guide

This guide walks you through installing JAIPilot in IntelliJ IDEA.

## Prerequisites

Before installing JAIPilot, ensure you have:

- **IntelliJ IDEA** 2023.1 or later (Community or Ultimate Edition)
- **Java** 8 or later installed
- **JUnit** 4.x or 5.x in your project dependencies

## Installation Methods

### Method 1: Install from JetBrains Marketplace (Recommended)

This is the easiest and recommended way to install JAIPilot.

1. **Open IntelliJ IDEA**

2. **Navigate to Plugins Settings**
   - On Windows/Linux: `File` → `Settings` → `Plugins`
   - On macOS: `IntelliJ IDEA` → `Preferences` → `Plugins`
   - Or use keyboard shortcut: `Ctrl+Alt+S` (Windows/Linux) or `Cmd+,` (macOS)

3. **Search for JAIPilot**
   - Click on the `Marketplace` tab
   - Type "JAIPilot" or "java-auto-unit-tests" in the search bar
   - Find "JAIPilot - One-Click AI Agent for Java Unit Testing"

4. **Install the Plugin**
   - Click the `Install` button
   - Wait for the download to complete

5. **Restart IntelliJ IDEA**
   - Click `Restart IDE` when prompted
   - The plugin will be active after restart

### Method 2: Install from Disk

Use this method if you want to install a specific version or the marketplace is unavailable.

1. **Download the Plugin**
   - Visit the [Releases page](https://github.com/skrcode/java-auto-unit-tests/releases)
   - Download the latest `.zip` file (e.g., `jaipilot-1.0.9.zip`)

2. **Open Plugin Settings**
   - Navigate to `Settings/Preferences` → `Plugins`

3. **Install from Disk**
   - Click the gear icon (⚙️) in the plugins window
   - Select `Install Plugin from Disk...`
   - Navigate to and select the downloaded `.zip` file
   - Click `OK`

4. **Restart IntelliJ IDEA**
   - Click `Restart IDE` when prompted

### Method 3: Build from Source

For developers who want to build the latest version from source.

1. **Clone the Repository**
   ```bash
   git clone https://github.com/skrcode/java-auto-unit-tests.git
   cd java-auto-unit-tests
   ```

2. **Build the Plugin**
   ```bash
   ./gradlew buildPlugin
   ```

3. **Locate the Built Plugin**
   - The plugin will be in `build/distributions/`
   - File name: `java-auto-unit-tests-VERSION.zip`

4. **Install Using Method 2** (Install from Disk)

## Verifying Installation

After installation, verify JAIPilot is working:

1. **Check Plugin is Enabled**
   - Go to `Settings/Preferences` → `Plugins` → `Installed`
   - Verify "JAIPilot" is listed and enabled

2. **Test Plugin Functionality**
   - Open any Java file in your project
   - Right-click in the editor
   - Look for "Generate Tests with JAIPilot" in the context menu

3. **Check Plugin Settings**
   - Go to `Settings/Preferences` → `Tools` → `JAIPilot`
   - Verify the settings page loads correctly

## Initial Configuration

After installation, you may want to configure JAIPilot:

1. **Navigate to Settings**
   - `Settings/Preferences` → `Tools` → `JAIPilot`

2. **Configure Test Root Directory** (Optional)
   - Default: `src/test/java`
   - Change if your project uses a different structure

3. **Select Test Framework** (Optional)
   - Choose between JUnit 4 and JUnit 5
   - JAIPilot auto-detects your project's framework by default

4. **Sign Up for API Access**
   - First time usage will prompt for authentication
   - Sign up for free credits (no credit card required)

## Troubleshooting Installation

### Plugin Not Appearing in Marketplace

**Problem**: Cannot find JAIPilot in the marketplace.

**Solutions**:
- Ensure you're using IntelliJ IDEA 2023.1 or later
- Check your internet connection
- Try refreshing the marketplace: Click the refresh icon
- Use Method 2 (Install from Disk) instead

### Installation Fails

**Problem**: Error during installation process.

**Solutions**:
- Check you have write permissions in the IDE's plugin directory
- Ensure sufficient disk space (at least 100MB free)
- Try restarting IntelliJ IDEA and installing again
- Check the IDE logs: `Help` → `Show Log in Explorer/Finder`

### Plugin Not Loading After Restart

**Problem**: Plugin installed but not appearing in menus.

**Solutions**:
- Verify plugin is enabled: `Settings` → `Plugins` → Check "JAIPilot" is checked
- Check for plugin conflicts: Disable other testing-related plugins temporarily
- Invalidate caches: `File` → `Invalidate Caches` → `Invalidate and Restart`

### Compatibility Issues

**Problem**: Plugin incompatible with your IDE version.

**Solutions**:
- Update IntelliJ IDEA to the latest version
- Check [compatibility matrix](../troubleshooting/faq.md#compatibility)
- Download a compatible version from [Releases](https://github.com/skrcode/java-auto-unit-tests/releases)

## Updating JAIPilot

### Automatic Updates (Recommended)

IntelliJ IDEA checks for updates automatically:

1. When an update is available, you'll see a notification
2. Click `Update` in the notification
3. Restart IDE when prompted

### Manual Update

To check for updates manually:

1. Go to `Settings/Preferences` → `Plugins`
2. Click `Check for Updates` (bottom of the window)
3. If an update is available, click `Update`
4. Restart IDE

### Update from Disk

1. Download the latest version using Method 2
2. Uninstall the current version (optional but recommended)
3. Install the new version from disk

## Uninstalling JAIPilot

If you need to uninstall JAIPilot:

1. Go to `Settings/Preferences` → `Plugins` → `Installed`
2. Find "JAIPilot" in the list
3. Click the gear icon next to the plugin
4. Select `Uninstall`
5. Restart IntelliJ IDEA

**Note**: Uninstalling will not delete:
- Your API credentials (stored in IDE's credential manager)
- Generated test files in your project
- Plugin settings (can be manually cleared if needed)

## Next Steps

Now that JAIPilot is installed:

- 📖 Follow the [Quick Start Guide](quick-start.md)
- ⚙️ Review [Configuration Options](configuration.md)
- 🚀 Learn about [Generating Tests](../guides/generating-tests.md)

## Need Help?

- 📚 Check the [FAQ](../troubleshooting/faq.md)
- 🐛 [Report installation issues](https://github.com/skrcode/java-auto-unit-tests/issues/new?template=bug_report.md)
- 💬 Ask in [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
