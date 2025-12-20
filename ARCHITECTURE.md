# Technical Blog Architecture Documentation

## Table of Contents
1. [System Overview](#system-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Design](#architecture-design)
4. [Bilingual Implementation](#bilingual-implementation)
5. [Content Management](#content-management)
6. [Deployment Pipeline](#deployment-pipeline)
7. [File Structure](#file-structure)
8. [Design Decisions](#design-decisions)

---

## System Overview

### Purpose
A bilingual technical blog for sharing knowledge about game servers, web servers, and open source projects (specifically Playhouse and ZeroMQ).

### Requirements Met
- Jekyll-based static site generator
- Chirpy theme integration
- Bilingual support (Korean default, English)
- Two main categories: playhouse, zeromq
- GitHub Pages native hosting
- Automated deployment
- SEO optimization
- Responsive design

---

## Technology Stack

### Core Technologies
- **Jekyll 4.3**: Static site generator
- **Chirpy Theme 7.0**: Modern Jekyll theme
- **GitHub Pages**: Hosting platform
- **GitHub Actions**: CI/CD pipeline
- **Ruby 3.x**: Runtime environment

### Jekyll Plugins
- `jekyll-feed`: RSS/Atom feed generation
- `jekyll-seo-tag`: SEO metadata
- `jekyll-sitemap`: XML sitemap
- `jekyll-archives`: Category and tag archives
- `jekyll-paginate`: Post pagination
- `jekyll-redirect-from`: URL redirects

---

## Architecture Design

### High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                   GitHub Repository                  │
│                 (ulala-x.github.io)                 │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ Push to main
                   ▼
┌─────────────────────────────────────────────────────┐
│              GitHub Actions Workflow                 │
│  ┌──────────────────────────────────────────────┐  │
│  │ 1. Checkout code                             │  │
│  │ 2. Setup Ruby 3.2 + Bundler                 │  │
│  │ 3. Install dependencies                      │  │
│  │ 4. Build Jekyll site                         │  │
│  │ 5. Test with htmlproofer                     │  │
│  │ 6. Upload artifact                           │  │
│  │ 7. Deploy to GitHub Pages                    │  │
│  └──────────────────────────────────────────────┘  │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│              GitHub Pages CDN                        │
│         https://ulala-x.github.io                   │
└─────────────────────────────────────────────────────┘
```

### Component Architecture

```
┌───────────────────────────────────────────────────────────┐
│                    Jekyll Site                            │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────────┐    ┌──────────────────────┐    │
│  │  Configuration      │    │  Content             │    │
│  │  ─────────────      │    │  ───────             │    │
│  │  _config.yml (KO)   │    │  _posts/ (Korean)    │    │
│  │  _config.en.yml (EN)│    │  _posts_en/ (English)│    │
│  └─────────────────────┘    └──────────────────────┘    │
│                                                           │
│  ┌─────────────────────┐    ┌──────────────────────┐    │
│  │  Localization       │    │  Theme & Layout      │    │
│  │  ────────────       │    │  ──────────────      │    │
│  │  _data/locales/     │    │  Chirpy Theme        │    │
│  │  - ko.yml           │    │  _layouts/           │    │
│  │  - en.yml           │    │  _includes/          │    │
│  └─────────────────────┘    └──────────────────────┘    │
│                                                           │
│  ┌─────────────────────┐    ┌──────────────────────┐    │
│  │  Navigation         │    │  Categories/Tags     │    │
│  │  ──────────         │    │  ───────────────     │    │
│  │  _tabs/             │    │  categories/         │    │
│  │  - categories.md    │    │  - playhouse.html    │    │
│  │  - tags.md          │    │  - zeromq.html       │    │
│  │  - archives.md      │    │  en/categories/      │    │
│  │  - about.md         │    │  - playhouse.html    │    │
│  └─────────────────────┘    │  - zeromq.html       │    │
│                              └──────────────────────┘    │
└───────────────────────────────────────────────────────────┘
```

---

## Bilingual Implementation

### Strategy: Path-Based Language Separation

The blog implements bilingual support using a path-based approach where each language has its own URL structure.

#### URL Structure

**Korean (Default Language)**
```
https://ulala-x.github.io/
https://ulala-x.github.io/posts/playhouse/welcome-to-playhouse/
https://ulala-x.github.io/categories/playhouse/
```

**English (Alternative Language)**
```
https://ulala-x.github.io/en/
https://ulala-x.github.io/en/posts/playhouse/welcome-to-playhouse/
https://ulala-x.github.io/en/categories/playhouse/
```

### Configuration Design

#### Main Configuration (_config.yml)
- Default language: Korean (ko-KR)
- Timezone: Asia/Seoul
- Base URL: "" (root)
- Defines Korean site metadata

#### English Override (_config.en.yml)
- Overrides language to English (en-US)
- Sets base URL to "/en"
- Overrides English metadata

### Content Organization

```
_posts/                    # Korean content
├── playhouse/
│   └── 2025-12-20-welcome-to-playhouse.md
└── zeromq/
    └── 2025-12-20-zeromq-introduction.md

_posts_en/                 # English content
├── playhouse/
│   └── 2025-12-20-welcome-to-playhouse.md
└── zeromq/
    └── 2025-12-20-zeromq-introduction.md
```

### Translation Linking

Posts are linked using the `lang_ref` front matter:

**Korean Post:**
```yaml
---
title: Playhouse 게임 서버 프레임워크 소개
lang: ko
lang_ref: welcome-to-playhouse
---
```

**English Post:**
```yaml
---
title: Introduction to Playhouse Game Server Framework
lang: en
lang_ref: welcome-to-playhouse
---
```

The language switcher component uses `lang_ref` to create links between translations.

### Localization Data

Locale files (`_data/locales/ko.yml` and `_data/locales/en.yml`) provide translated UI strings:
- Navigation labels
- Post metadata labels
- Search interface
- Footer content
- Error messages

### Language Switcher

The `_includes/language-switcher.html` component:
1. Detects current page language
2. Checks for `lang_ref` to find translation
3. Generates link to translated version
4. Falls back to language homepage if no translation exists

---

## Content Management

### Post Structure

Every post follows this structure:

```yaml
---
title: Post Title
date: YYYY-MM-DD HH:MM:SS +TIMEZONE
categories: [category-name]
tags: [tag1, tag2, tag3]
lang: ko or en
lang_ref: unique-identifier
author: ulala-x
---

# Post Content in Markdown

## Supports all markdown features
- Lists
- Code blocks
- Images
- Tables
```

### Category System

Two main categories:
1. **playhouse**: Game server framework content
2. **zeromq**: Server communication library content

Each category has dedicated pages in both languages:
- `/categories/playhouse.html` (Korean)
- `/en/categories/playhouse.html` (English)

### Permalink Strategy

Configured in `_config.yml`:

```yaml
defaults:
  - scope:
      path: ""
      type: posts
    values:
      permalink: /posts/:categories/:title/
```

This creates clean URLs like:
- `/posts/playhouse/welcome-to-playhouse/`
- `/en/posts/zeromq/zeromq-introduction/`

---

## Deployment Pipeline

### GitHub Actions Workflow

File: `.github/workflows/pages-deploy.yml`

#### Trigger Conditions
- Push to `main` branch
- Excludes changes to: .gitignore, README.md, LICENSE
- Manual workflow dispatch available

#### Build Process

1. **Checkout Stage**
   - Fetch repository code
   - Full history fetch for proper git data

2. **Environment Setup**
   - Install Ruby 3.2
   - Cache Bundler dependencies
   - Configure GitHub Pages

3. **Build Stage**
   ```bash
   bundle exec jekyll build --baseurl "${{ steps.pages.outputs.base_path }}"
   ```
   - Production environment
   - Optimized assets
   - Compressed HTML

4. **Test Stage**
   - HTML proofer validation
   - Link checking (internal only)
   - Non-blocking (errors don't fail deployment)

5. **Deploy Stage**
   - Upload built site as artifact
   - Deploy to GitHub Pages
   - Automatic CDN distribution

#### Permissions

```yaml
permissions:
  contents: read
  pages: write
  id-token: write
```

### Deployment Flow

```
Developer Push
      ↓
GitHub Detects Push
      ↓
Trigger Workflow
      ↓
┌─────────────────┐
│  Build Job      │
│  ──────────     │
│  - Setup Ruby   │
│  - Install deps │
│  - Build Jekyll │
│  - Test site    │
│  - Upload       │
└────────┬────────┘
         ↓
┌─────────────────┐
│  Deploy Job     │
│  ───────────    │
│  - Get artifact │
│  - Deploy Pages │
└────────┬────────┘
         ↓
    Live Site
```

---

## File Structure

### Complete Directory Tree

```
ulala-x.github.io/
│
├── .github/
│   └── workflows/
│       └── pages-deploy.yml          # GitHub Actions workflow
│
├── _data/
│   └── locales/
│       ├── ko.yml                    # Korean UI strings
│       └── en.yml                    # English UI strings
│
├── _includes/
│   └── language-switcher.html        # Language toggle component
│
├── _layouts/                         # (Future custom layouts)
│
├── _posts/                           # Korean posts
│   ├── playhouse/
│   │   └── 2025-12-20-welcome-to-playhouse.md
│   └── zeromq/
│       └── 2025-12-20-zeromq-introduction.md
│
├── _posts_en/                        # English posts
│   ├── playhouse/
│   │   └── 2025-12-20-welcome-to-playhouse.md
│   └── zeromq/
│       └── 2025-12-20-zeromq-introduction.md
│
├── _tabs/                            # Navigation pages
│   ├── about.md                      # About page
│   ├── archives.md                   # Archive page
│   ├── categories.md                 # Categories page
│   └── tags.md                       # Tags page
│
├── assets/
│   └── img/                          # Images
│
├── categories/                       # Korean category pages
│   ├── playhouse.html
│   └── zeromq.html
│
├── en/                               # English site root
│   ├── index.html                    # English homepage
│   └── categories/                   # English category pages
│       ├── playhouse.html
│       └── zeromq.html
│
├── _config.yml                       # Main Jekyll config (Korean)
├── _config.en.yml                    # English config override
├── Gemfile                           # Ruby dependencies
├── .gitignore                        # Git ignore rules
├── index.html                        # Korean homepage
├── robots.txt                        # SEO robots file
├── ARCHITECTURE.md                   # This document
└── README.md                         # Project documentation
```

---

## Design Decisions

### 1. Path-Based vs Subdomain Language Strategy

**Decision**: Path-based (`/en/`) instead of subdomain (`en.ulala-x.github.io`)

**Rationale**:
- Simpler GitHub Pages configuration
- Single repository management
- Better for SEO (centralized domain authority)
- Easier content synchronization
- No DNS configuration required

**Trade-offs**:
- Slightly longer URLs for English content
- More complex routing configuration

### 2. Separate Post Directories vs Single Directory

**Decision**: Separate `_posts/` and `_posts_en/` directories

**Rationale**:
- Clear content separation
- Easier content management
- Prevents accidental language mixing
- Simpler Jekyll configuration
- Better for content authors

**Trade-offs**:
- Duplicate directory structure
- Manual synchronization of bilingual content

### 3. Collection vs Categories for Organization

**Decision**: Categories (playhouse, zeromq) instead of Jekyll Collections

**Rationale**:
- Native Jekyll feature, better theme support
- Automatic archive generation with `jekyll-archives`
- SEO-friendly URLs
- Standard blog organization pattern
- Chirpy theme optimized for categories

**Trade-offs**:
- Less flexible than collections for non-blog content
- Limited to blog post organization

### 4. GitHub Actions vs GitHub Pages Default Build

**Decision**: Custom GitHub Actions workflow

**Rationale**:
- Full control over build process
- Can use latest Jekyll version
- Custom plugins support
- Testing integration (htmlproofer)
- Better error handling and debugging

**Trade-offs**:
- More complex setup
- Requires workflow maintenance
- Longer initial deployment time

### 5. lang_ref Translation Linking

**Decision**: Manual `lang_ref` front matter for linking translations

**Rationale**:
- Explicit translation relationships
- No assumptions about file naming
- Flexible for posts with different titles
- Clear for content authors
- Easy to implement in language switcher

**Trade-offs**:
- Manual maintenance required
- Possible human error in linking
- No automatic validation

### 6. Configuration Override Pattern

**Decision**: `_config.en.yml` overrides instead of conditional configuration

**Rationale**:
- Clean separation of language-specific settings
- Easy to maintain
- Clear which settings apply to each language
- Jekyll native pattern
- Scalable to more languages

**Trade-offs**:
- Duplication of some configuration
- Must remember to update both files for shared settings

### 7. Theme Choice: Chirpy

**Decision**: Chirpy theme over other Jekyll themes

**Rationale**:
- Modern, clean design
- Excellent technical blog features
- Active development and support
- Good documentation
- SEO optimized out of the box
- Responsive design
- Code highlighting
- Archive system
- Category/tag support

**Trade-offs**:
- Learning curve for customization
- Dependency on theme maintainer
- Some features may be opinionated

---

## Performance Considerations

### Build Optimization
- Asset compression enabled
- HTML minification
- Sass compilation with compression
- Bundler caching in CI/CD

### SEO Optimization
- Semantic HTML structure
- Open Graph tags
- Twitter Card support
- XML sitemap generation
- RSS/Atom feeds
- hreflang tags for bilingual content
- robots.txt configuration

### User Experience
- Responsive design (mobile-first)
- Fast page load (static site)
- Clean URLs (no file extensions)
- Breadcrumb navigation
- Table of contents for posts
- Syntax highlighting for code
- Dark/light theme support

---

## Security Considerations

### GitHub Pages Security
- HTTPS enforced
- No server-side code execution
- Static file serving only
- GitHub's CDN and DDoS protection

### Content Security
- No user input handling
- No database or backend
- Version controlled content
-审计 trail via Git history

### Dependency Management
- Gemfile.lock for reproducible builds
- Regular dependency updates
- GitHub Dependabot alerts

---

## Scalability

### Content Scalability
- Static site scales infinitely on CDN
- No database bottlenecks
- Fast build times even with hundreds of posts
- Jekyll's incremental build support

### Language Scalability
- Pattern supports additional languages
- Just add new locale file and config override
- Duplicate directory structure for new language

### Category Scalability
- Easy to add new categories
- Just create category page in both language directories
- Jekyll-archives automatically generates archives

---

## Future Enhancements

### Potential Improvements
1. **Search Functionality**: Add Algolia or Lunr.js search
2. **Comments**: Integrate Disqus, utterances, or giscus
3. **Analytics**: Google Analytics or Plausible
4. **Newsletter**: RSS to email subscription
5. **Series Posts**: Link related posts in series
6. **Reading Time**: Calculate and display estimated reading time
7. **Related Posts**: Automatic related content suggestions
8. **Author Pages**: Multiple author support
9. **Table of Contents**: Automatic TOC generation
10. **Image Optimization**: Automatic image compression and responsive images

### Technical Debt
- Avatar placeholder needs actual image
- English navigation tabs not yet created
- Theme customization CSS not yet added
- Additional test coverage for links
- Performance monitoring setup

---

## Conclusion

This architecture provides a solid foundation for a bilingual technical blog with:
- Clean separation of concerns
- Maintainable codebase
- Automated deployment
- SEO optimization
- Scalable content organization
- Professional appearance

The design decisions prioritize simplicity, maintainability, and user experience while leveraging GitHub Pages' native capabilities and the Jekyll ecosystem.
