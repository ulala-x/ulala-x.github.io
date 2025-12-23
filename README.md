# Ulala-X Blog

🔗 **Blog**: [https://ulala-x.github.io](https://ulala-x.github.io)

Personal technical blog built with [Astro](https://astro.build) and [AstroPaper](https://github.com/satnaing/astro-paper) theme.

## Features

- Bilingual support (Korean 🇰🇷 / English 🇺🇸)
- Fast, modern static site generator
- SEO optimized
- Dark/Light mode
- RSS feed
- Sitemap
- Search functionality

## Tech Stack

- **Framework**: Astro 5.x
- **Styling**: Tailwind CSS 4.x
- **Package Manager**: pnpm
- **Deployment**: GitHub Pages

## Quick Start

### Prerequisites

- Node.js 20+
- pnpm 10.11.1+

### Installation

```bash
# Install dependencies
pnpm install

# Run development server
pnpm dev

# Build for production
pnpm build

# Preview production build
pnpm preview
```

## Project Structure

```
/
├── public/           # Static assets
├── src/
│   ├── components/   # Astro components
│   ├── data/
│   │   └── blog/     # Blog posts (Markdown)
│   │       ├── *.md      # Korean posts
│   │       └── en/       # English posts
│   ├── layouts/      # Page layouts
│   ├── pages/        # Astro pages
│   ├── styles/       # Global styles
│   └── utils/        # Utilities
├── astro.config.ts   # Astro configuration
└── package.json
```

## Writing Posts

### Create a New Post

- Korean post: `src/data/blog/your-post.md`
- English post: `src/data/blog/en/your-post.md`

### Frontmatter Template

```yaml
---
author: Ulala-X
pubDatetime: YYYY-MM-DDTHH:mm:ss+09:00
title: Your Post Title
slug: your-post-slug
featured: true
draft: false
tags:
  - tag1
  - tag2
description: Brief description of your post
---
```

## Deployment

Automatically deployed to GitHub Pages when pushing to `main` branch.

## Author

- GitHub: [@ulala-x](https://github.com/ulala-x)
- Email: ulala.the.great@gmail.com

## License

MIT
