# Setting Up GitHub Social Preview

A great social preview image makes your repository stand out when shared on social media, Slack, Discord, and other platforms.

## What is a Social Preview?

When you share your GitHub repository link, platforms like Twitter, LinkedIn, and Slack display a preview card with:
- Repository name
- Description
- Preview image (Open Graph image)

## Current Setup

The repository currently uses GitHub's default preview. To create a custom one:

## Creating the Preview Image

### Specifications

- **Dimensions**: 1200 x 630 pixels (required)
- **Format**: PNG or JPG
- **Max file size**: 1 MB
- **Aspect ratio**: 1.91:1

### Design Guidelines

Include in your preview:

1. **JAIPilot Logo** - Top left or center
2. **Tagline** - "One-Click AI Agent for Java Unit Testing"
3. **Key Value Props**:
   - Generate tests in seconds
   - Automatically fixes failing tests
   - JUnit 4 & 5 support
4. **Visual Element** - Code snippet or screenshot
5. **Branding** - Clean, professional design

### Color Scheme

Suggested colors:
- **Primary**: #4A90E2 (Blue - trust, technology)
- **Accent**: #50C878 (Green - success, testing)
- **Background**: #FFFFFF or #F5F5F5 (Light, clean)
- **Text**: #333333 (Dark gray, readable)

### Design Tools

**Free tools:**
- [Canva](https://www.canva.com/) - Easy templates
- [Figma](https://www.figma.com/) - Professional design
- [GIMP](https://www.gimp.org/) - Open source image editor

**Templates:**
- Search "GitHub social preview template 1200x630" on Canva
- Use existing templates and customize with JAIPilot branding

### Example Layout

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│  [Logo]  JAIPilot                                     │
│          One-Click AI Agent for Java Unit Testing     │
│                                                        │
│  ✓ Generate comprehensive tests in seconds           │
│  ✓ Automatically fixes failing tests                 │
│  ✓ JUnit 4 & 5 support                              │
│  ✓ Free to start                                     │
│                                                        │
│  [Code Screenshot or Visual]                          │
│                                                        │
│  github.com/skrcode/java-auto-unit-tests             │
└────────────────────────────────────────────────────────┘
```

## Uploading to GitHub

### Steps

1. **Create or find your image** (`og-image.png`)
2. **Go to repository Settings**
   - Click `Settings` tab in your repository
3. **Scroll to Social preview**
   - Find "Social preview" section
4. **Click Edit**
5. **Upload image**
   - Click "Upload an image"
   - Select your `og-image.png` file
   - Ensure it's 1200x630px
6. **Save**

### Verification

Test your social preview:

1. **Twitter Card Validator**
   - https://cards-dev.twitter.com/validator
   - Enter: `https://github.com/skrcode/java-auto-unit-tests`

2. **LinkedIn Post Inspector**
   - https://www.linkedin.com/post-inspector/
   - Enter repository URL

3. **Facebook Sharing Debugger**
   - https://developers.facebook.com/tools/debug/
   - Enter repository URL

4. **Slack**
   - Paste the GitHub URL in any Slack channel
   - Check the preview appears correctly

## Example Preview Images

### Option 1: Code-Focused

Shows before/after of test generation:
- Left side: Java class without tests
- Right side: Generated comprehensive test suite
- Arrow in middle showing "JAIPilot" transformation

### Option 2: Feature-Focused

Clean design highlighting:
- JAIPilot logo prominently
- 3-4 key features with icons
- JetBrains Marketplace badge
- Download statistics

### Option 3: Demo-Focused

Screenshot of:
- IntelliJ IDE with JAIPilot in action
- Context menu showing "Generate Tests with JAIPilot"
- Terminal showing all tests passing ✓

## Best Practices

### Do ✅

- Use high-contrast colors for readability
- Keep text large and legible (minimum 24px)
- Show the value proposition clearly
- Include your GitHub URL
- Use professional fonts
- Test on multiple platforms
- Optimize file size (compress to < 1 MB)

### Don't ❌

- Use images smaller than 1200x630
- Include too much text (keep it scannable)
- Use low-resolution logos or screenshots
- Forget to test the preview
- Use copyrighted images without permission
- Make it too busy or cluttered

## Alternative: Using GitHub's Default

If you prefer GitHub's default preview:
- It shows your README's first image
- Or the repository description with GitHub branding
- No setup required but less distinctive

## Updating the Preview

### When to Update

Update your social preview when:
- Major version releases
- Rebranding or new logo
- Adding significant new features
- Improving design based on feedback

### Process

1. Create new version
2. Upload following same steps
3. Clear cache on social platforms (use validators)
4. Test the new preview appears correctly

## Tools for Creating Previews

### Online Tools

- **Canva** - https://www.canva.com/
  - Templates: Search "social media preview"
  - Free tier available
  - Easy drag-and-drop

- **Figma** - https://www.figma.com/
  - Professional design tool
  - Free for individuals
  - Collaboration features

- **Bannerbear** - https://www.bannerbear.com/
  - API for dynamic images
  - Templates available

### Desktop Tools

- **Photoshop** - Industry standard
- **GIMP** - Free alternative
- **Affinity Designer** - One-time purchase
- **Sketch** - Mac only

### Code-Based

Generate programmatically:

```javascript
// Using node-canvas or similar
const { createCanvas } = require('canvas');

const canvas = createCanvas(1200, 630);
const ctx = canvas.getContext('2d');

// Draw background
ctx.fillStyle = '#4A90E2';
ctx.fillRect(0, 0, 1200, 630);

// Add text, logo, etc.
// ... your design code

// Save
const buffer = canvas.toBuffer('image/png');
fs.writeFileSync('og-image.png', buffer);
```

## Storage

Store your preview image:
1. **In repository**: `assets/og-image.png`
2. **In GitHub**: Via Settings > Social preview
3. **External**: CDN or image hosting (not recommended)

## Analytics

Track preview performance:
- Monitor click-through rates from social media
- Use UTM parameters: `?utm_source=twitter&utm_medium=social`
- Check GitHub traffic sources in Insights

## Resources

- [GitHub Docs - Social Preview](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview)
- [Open Graph Protocol](https://ogp.me/)
- [Twitter Card Documentation](https://developer.twitter.com/en/docs/twitter-for-websites/cards/overview/abouts-cards)

## Need Help?

- Check examples from popular repositories
- Ask in [Discussions](https://github.com/skrcode/java-auto-unit-tests/discussions)
- Open an issue for design feedback

---

**Pro Tip**: Look at social previews from successful open-source projects for inspiration:
- https://github.com/spring-projects/spring-boot
- https://github.com/junit-team/junit5
- https://github.com/mockito/mockito
