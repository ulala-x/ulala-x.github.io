# Ulala-X Technical Blog

A bilingual (Korean/English) technical blog built with Jekyll and Chirpy theme, hosted on GitHub Pages.

## Overview

This blog covers technical content about:
- **Playhouse**: Game server framework
- **ZeroMQ**: Server communication libraries
- Game servers, web servers, and open source projects

## Features

- Bilingual support (Korean as default, English)
- Jekyll + Chirpy theme
- Categories: playhouse, zeromq
- Tag system
- Code syntax highlighting
- SEO optimized
- Responsive design
- GitHub Pages automated deployment

## Structure

```
ulala-x.github.io/
├── _config.yml              # Main configuration (Korean)
├── _config.en.yml           # English configuration override
├── Gemfile                  # Ruby dependencies
├── index.html               # Korean homepage
├── en/
│   └── index.html          # English homepage
├── _posts/                  # Korean posts
│   ├── playhouse/
│   └── zeromq/
├── _posts_en/               # English posts
│   ├── playhouse/
│   └── zeromq/
├── _data/
│   └── locales/            # Language files (ko.yml, en.yml)
├── _tabs/                   # Navigation pages
├── _includes/
│   └── language-switcher.html
├── categories/              # Category pages
└── .github/
    └── workflows/
        └── pages-deploy.yml # GitHub Actions workflow
```

## URL Structure

### Korean (Default)
- Homepage: `https://ulala-x.github.io/`
- Posts: `/posts/playhouse/...`, `/posts/zeromq/...`
- Categories: `/categories/playhouse/`, `/categories/zeromq/`

### English
- Homepage: `https://ulala-x.github.io/en/`
- Posts: `/en/posts/playhouse/...`, `/en/posts/zeromq/...`
- Categories: `/en/categories/playhouse/`, `/en/categories/zeromq/`

## Writing Posts

### Korean Post

Create a file in `_posts/{category}/YYYY-MM-DD-title.md`:

```markdown
---
title: 포스트 제목
date: 2025-12-20 14:00:00 +0900
categories: [playhouse]
tags: [게임서버, 프레임워크]
lang: ko
lang_ref: unique-post-id
author: ulala-x
---

포스트 내용...
```

### English Post

Create a corresponding file in `_posts_en/{category}/YYYY-MM-DD-title.md`:

```markdown
---
title: Post Title
date: 2025-12-20 14:00:00 +0900
categories: [playhouse]
tags: [game-server, framework]
lang: en
lang_ref: unique-post-id
author: ulala-x
---

Post content...
```

**Important**: Use the same `lang_ref` value for Korean and English versions of the same post to link them together.

## Local Development

### Prerequisites

- Ruby 3.x
- Bundler

### Setup

```bash
# Install dependencies
bundle install

# Serve locally (Korean)
bundle exec jekyll serve

# Serve locally (English)
bundle exec jekyll serve --config _config.yml,_config.en.yml

# Build for production
JEKYLL_ENV=production bundle exec jekyll build
```

### Local URLs

- Korean: `http://localhost:4000/`
- English: `http://localhost:4000/en/`

## Deployment

The site is automatically deployed to GitHub Pages when you push to the `main` branch.

### GitHub Pages Setup

1. Go to repository Settings → Pages
2. Source: GitHub Actions
3. The workflow will automatically build and deploy

### Manual Deployment

```bash
# Add changes
git add .

# Commit
git commit -m "Add new post"

# Push to main branch
git push origin main
```

The GitHub Actions workflow (`.github/workflows/pages-deploy.yml`) will automatically build and deploy your site.

## Categories

- **playhouse**: Game server framework posts
- **zeromq**: Server communication library posts

To add a new category, create category pages in both `categories/` and `en/categories/`.

## Customization

### Site Information

Edit `_config.yml` (Korean) and `_config.en.yml` (English):
- `title`: Site title
- `tagline`: Site tagline
- `description`: Site description
- `url`: Site URL
- `social`: Social media links

### Navigation

Edit files in `_tabs/` to customize navigation menu.

### Theme

The site uses the Chirpy theme. Customize by:
- Editing `_config.yml` settings
- Overriding layouts in `_layouts/`
- Adding custom CSS in `assets/css/`

## Analytics and Comments

To enable analytics or comments:

1. Edit `_config.yml`
2. Uncomment and configure:
   - `google_analytics.id` for Google Analytics
   - `comments.active` for comments system (e.g., Disqus)

## Author

- GitHub: [@ulala-x](https://github.com/ulala-x)
- Email: ulala.the.great@gmail.com

## License

This blog is powered by [Jekyll](https://jekyllrb.com/) and [Chirpy](https://github.com/cotes2020/jekyll-theme-chirpy) theme.