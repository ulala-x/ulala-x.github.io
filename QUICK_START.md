# Quick Start Guide

## Adding a New Blog Post

### 1. Create Korean Post

Create a file in `_posts/{category}/YYYY-MM-DD-title.md`:

```bash
# Example: Create a new Playhouse post
touch _posts/playhouse/2025-12-21-playhouse-architecture.md
```

Add front matter and content:

```markdown
---
title: Playhouse 아키텍처 심층 분석
date: 2025-12-21 10:00:00 +0900
categories: [playhouse]
tags: [아키텍처, 설계, 게임서버, 성능]
lang: ko
lang_ref: playhouse-architecture
author: ulala-x
---

# Playhouse 아키텍처

여기에 내용을 작성합니다...

## 섹션 1

내용...

## 섹션 2

내용...
```

### 2. Create English Post

Create corresponding file in `_posts_en/{category}/YYYY-MM-DD-title.md`:

```bash
# Same category and date
touch _posts_en/playhouse/2025-12-21-playhouse-architecture.md
```

Add front matter and content:

```markdown
---
title: In-Depth Playhouse Architecture Analysis
date: 2025-12-21 10:00:00 +0900
categories: [playhouse]
tags: [architecture, design, game-server, performance]
lang: en
lang_ref: playhouse-architecture
author: ulala-x
---

# Playhouse Architecture

Write your content here...

## Section 1

Content...

## Section 2

Content...
```

**Important**: Use the same `lang_ref` value in both files to link translations!

### 3. Commit and Push

```bash
# Add the new files
git add _posts/ _posts_en/

# Commit with a descriptive message
git commit -m "Add new post: Playhouse architecture analysis"

# Push to trigger automatic deployment
git push origin main
```

### 4. Verify Deployment

1. Go to: https://github.com/ulala-x/ulala-x.github.io/actions
2. Wait for the "Deploy Jekyll site to Pages" workflow to complete (2-3 minutes)
3. Visit your site:
   - Korean: https://ulala-x.github.io/posts/playhouse/playhouse-architecture/
   - English: https://ulala-x.github.io/en/posts/playhouse/playhouse-architecture/

---

## File Naming Convention

```
YYYY-MM-DD-post-title.md
```

- `YYYY-MM-DD`: Publication date
- `post-title`: URL-friendly title (lowercase, hyphens for spaces)
- `.md`: Markdown extension

---

## Front Matter Fields

### Required Fields

```yaml
title: Post Title              # Post title (appears on page)
date: 2025-12-21 10:00:00 +0900  # Publication date and time
categories: [category]         # Category (playhouse or zeromq)
tags: [tag1, tag2]            # Tags (for discovery)
lang: ko                      # Language (ko or en)
lang_ref: unique-id           # Translation link ID
```

### Optional Fields

```yaml
author: ulala-x               # Author name (defaults to site author)
pin: true                     # Pin to top of homepage
toc: true                     # Show table of contents (default: true)
comments: true                # Enable comments (default: true)
math: true                    # Enable math rendering
mermaid: true                 # Enable Mermaid diagrams
image:                        # Post preview image
  path: /assets/img/post.jpg
  alt: Image description
```

---

## Markdown Features

### Code Blocks

```python
def hello_world():
    print("Hello, World!")
```

Specify language for syntax highlighting:
- `python`, `java`, `javascript`, `bash`, `yaml`, etc.

### Images

```markdown
![Image description](/assets/img/image.png)
```

Or with more control:

```markdown
![Desktop View](/assets/img/sample/mockup.png){: width="700" height="400" }
_Image Caption_
```

### Links

```markdown
[Link text](https://example.com)
```

### Lists

```markdown
- Item 1
- Item 2
  - Nested item
  - Another nested item
```

Numbered lists:

```markdown
1. First item
2. Second item
3. Third item
```

### Tables

```markdown
| Header 1 | Header 2 | Header 3 |
|----------|----------|----------|
| Cell 1   | Cell 2   | Cell 3   |
| Cell 4   | Cell 5   | Cell 6   |
```

### Blockquotes

```markdown
> This is a blockquote
>
> It can span multiple lines
```

### Alerts

```markdown
> This is a tip.
{: .prompt-tip }

> This is information.
{: .prompt-info }

> This is a warning.
{: .prompt-warning }

> This is dangerous.
{: .prompt-danger }
```

---

## Categories

Currently available:
- `playhouse`: Game server framework
- `zeromq`: Server communication libraries

### Adding a New Category

1. Create Korean category page: `categories/new-category.html`

```html
---
layout: category
title: New Category
category: new-category
lang: ko
---
```

2. Create English category page: `en/categories/new-category.html`

```html
---
layout: category
title: New Category
category: new-category
lang: en
---
```

3. Use the new category in posts:

```yaml
categories: [new-category]
```

---

## Workflow Checklist

- [ ] Create Korean post in `_posts/{category}/`
- [ ] Create English post in `_posts_en/{category}/`
- [ ] Verify `lang_ref` matches in both files
- [ ] Use same date in both files
- [ ] Use same category in both files
- [ ] Add appropriate tags (translated)
- [ ] Review content for formatting
- [ ] Commit changes
- [ ] Push to main branch
- [ ] Check GitHub Actions deployment
- [ ] Verify on live site (both languages)

---

## Tips

1. **Preview Locally**: If you have Ruby installed, run `bundle exec jekyll serve` to preview locally before pushing

2. **Draft Posts**: Store work-in-progress posts outside `_posts/` and `_posts_en/` directories

3. **SEO**: Include descriptive titles, tags, and content for better search engine visibility

4. **Code Formatting**: Always specify language for code blocks for proper syntax highlighting

5. **Translation Timing**: You don't have to publish both languages simultaneously. You can create Korean first and add English later (or vice versa)

6. **Images**: Store images in `assets/img/` and reference with `/assets/img/filename.png`

7. **URL Stability**: Once published, avoid changing the filename as it changes the URL

---

## Common Issues

### Post Not Showing Up

- Check date is not in the future
- Verify file is in correct directory (`_posts/` or `_posts_en/`)
- Ensure filename follows `YYYY-MM-DD-title.md` format
- Check front matter YAML is valid

### Language Switcher Not Working

- Verify both posts have same `lang_ref` value
- Check that both files exist
- Ensure `lang` field is set correctly (ko or en)

### Build Failing

- Check GitHub Actions logs
- Verify all YAML front matter is valid
- Ensure no special characters in filenames
- Check for broken links or images

---

## Next Steps

1. Write your first post following this guide
2. Explore the sample posts for more examples
3. Customize the About page in `_tabs/about.md`
4. Add your avatar image to `assets/img/avatar.png`
5. Configure analytics (if desired) in `_config.yml`

Happy blogging!
