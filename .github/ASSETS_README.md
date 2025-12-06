# JAIPilot Assets Guide

This directory contains visual assets for JAIPilot documentation and marketing.

## Screenshots

Add screenshots showing:

### 1. Plugin in Action
- `plugin-context-menu.png` - Right-click menu showing "Generate Tests with JAIPilot"
- `plugin-settings.png` - JAIPilot settings page in IntelliJ
- `test-generation-progress.png` - Progress notification during test generation
- `generated-tests.png` - Example of generated test file

### 2. Test Results
- `tests-passing.png` - Green checkmarks showing all tests passed
- `test-refinement.png` - Automatic test refinement in action
- `coverage-report.png` - Code coverage metrics

### 3. IDE Integration
- `marketplace-listing.png` - JAIPilot on JetBrains Marketplace
- `plugin-toolbar.png` - Plugin icons in IntelliJ toolbar
- `quick-actions.png` - Quick action menu with JAIPilot

## Demo GIFs

Create animated GIFs showing:

### 1. Quick Start (30 seconds)
`demo-quick-start.gif`
1. Right-click on Java class
2. Select "Generate Tests with JAIPilot"
3. Watch tests being generated
4. See tests passing

### 2. Test Refinement (20 seconds)
`demo-refinement.gif`
1. Generated test fails
2. JAIPilot automatically detects failure
3. Test is regenerated and fixed
4. Test passes on retry

### 3. Batch Generation (15 seconds)
`demo-batch-generation.gif`
1. Select multiple Java files
2. Generate tests for all
3. Watch progress for each file

## Logo and Branding

- `jaipilot-logo.png` - Main logo (512x512)
- `jaipilot-logo-small.png` - Small version for README (128x128)
- `jaipilot-banner.png` - Wide banner for social media (1200x630)
- `jaipilot-icon.svg` - Vector icon for various uses

## Social Media Preview

### Open Graph Image
- File: `og-image.png`
- Dimensions: 1200x630px
- Shows:
  - JAIPilot logo
  - Tagline: "One-Click AI Agent for Java Unit Testing"
  - Key features list
  - Screenshot or code example

To set as repository social preview:
1. Go to repository Settings
2. Scroll to Social preview
3. Edit → Upload `og-image.png`

## Creating Screenshots

### Tools Recommended
- **macOS**: Shift+Cmd+4 (built-in), CleanShot X
- **Windows**: Snipping Tool, ShareX
- **Linux**: Flameshot, Shutter

### Screenshot Guidelines

1. **Resolution**: Use HiDPI/Retina displays for crisp screenshots
2. **Window Size**: Keep IDE window at reasonable size (not full screen)
3. **Theme**: Use default IntelliJ theme (light or Darcula)
4. **Content**: Use generic example code (Calculator, StringUtils, etc.)
5. **Annotations**: Add arrows or highlights using tools like:
   - Skitch
   - Monosnap
   - Annotate (macOS)

### Example Code for Screenshots

Use these generic examples for consistency:

```java
// Calculator.java
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

## Creating GIFs

### Tools Recommended
- **macOS**: Kap, Gifski, LICEcap
- **Windows**: ScreenToGif, LICEcap
- **Linux**: Peek, SimpleScreenRecorder + ffmpeg

### GIF Guidelines

1. **Length**: Keep under 30 seconds
2. **Size**: Optimize to < 5MB (use tools like gifski or gifsicle)
3. **FPS**: 10-15 FPS is sufficient
4. **Resolution**: 1280x720 or 1920x1080
5. **Focus**: Show only relevant parts of screen
6. **Speed**: Record at normal speed, optionally speed up by 1.25-1.5x
7. **Cursor**: Make cursor visible and movements deliberate

### GIF Optimization

```bash
# Using gifsicle
gifsicle -O3 --lossy=80 input.gif -o output.gif

# Using ffmpeg to reduce size
ffmpeg -i input.gif -vf "fps=10,scale=720:-1:flags=lanczos" output.gif
```

## File Naming Convention

Use descriptive, kebab-case names:
- ✅ `plugin-context-menu.png`
- ✅ `demo-quick-start.gif`
- ❌ `screenshot1.png`
- ❌ `Demo.gif`

## Image Optimization

Before committing, optimize images:

### PNG Optimization
```bash
# Using pngquant
pngquant --quality=65-80 input.png -o output.png

# Using OptiPNG
optipng -o7 image.png
```

### JPG Optimization
```bash
# Using jpegoptim
jpegoptim --max=85 image.jpg
```

## Adding to README

Reference assets in README:

```markdown
<div align="center">
  <img src="assets/demo-quick-start.gif" alt="JAIPilot Quick Start Demo" width="800">
</div>

## Screenshots

<table>
  <tr>
    <td><img src="assets/plugin-context-menu.png" alt="Context Menu"></td>
    <td><img src="assets/generated-tests.png" alt="Generated Tests"></td>
  </tr>
  <tr>
    <td align="center">Generate from context menu</td>
    <td align="center">Comprehensive tests in seconds</td>
  </tr>
</table>
```

## Placeholder Images

Until real screenshots are added, you can use placeholders:

```markdown
<!-- Placeholder for demo -->
![Demo Coming Soon](https://via.placeholder.com/800x450/4A90E2/FFFFFF?text=Demo+Coming+Soon)
```

## Contributing Assets

When contributing screenshots or GIFs:

1. Follow the guidelines above
2. Add to appropriate subdirectory
3. Update this README with description
4. Ensure files are optimized
5. Submit PR with assets

## License

All assets in this directory are part of JAIPilot and are licensed under the same license as the project (Mozilla Public License 2.0).

For questions about assets, open an issue or contact the maintainers.
