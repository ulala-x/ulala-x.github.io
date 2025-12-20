# Astro vs Jekyll Blog Comparison

## Overview

This document compares the Astro prototype (`/astro-prototype`) with the existing Jekyll blog for ulala-x.github.io.

## Quick Start Commands

### Jekyll (Current)

```bash
# Development
bundle exec jekyll serve

# Build
bundle exec jekyll build
```

### Astro (Prototype)

```bash
cd astro-prototype

# Development
npm run dev

# Build
npm run build

# Preview
npm run preview
```

## URL Structure Comparison

### Jekyll

**Korean (Default):**
- Home: `/`
- Posts: `/posts/{category}/{title}/`
- Categories: `/categories/{name}/`
- Tags: `/tags/{name}/`

**English:**
- Home: `/en/`
- Posts: `/en/posts/{category}/{title}/`
- Categories: `/en/categories/{name}/`
- Tags: `/en/tags/{name}/`

### Astro Prototype

**Korean (Default):**
- Home: `/`
- Posts: `/posts/ko/{category}/{title}/`
- All Posts: `/posts/`

**English:**
- Home: `/en/`
- Posts: `/en/posts/en/{category}/{title}/`
- All Posts: `/en/posts/`

> Note: Astro URLs can be customized to match Jekyll exactly if needed.

## Content Organization

### Jekyll

```
_posts/
├── playhouse/
│   └── 2025-12-20-welcome-to-playhouse.md
└── zeromq/
    └── 2025-12-20-zeromq-introduction.md

_posts_en/
├── playhouse/
│   └── 2025-12-20-welcome-to-playhouse.md
└── zeromq/
    └── 2025-12-20-zeromq-introduction.md
```

### Astro

```
src/data/blog/
├── ko/
│   ├── playhouse/
│   │   └── welcome-to-playhouse.md
│   └── zeromq/
│       └── zeromq-introduction.md
└── en/
    ├── playhouse/
    │   └── welcome-to-playhouse.md
    └── zeromq/
        └── zeromq-introduction.md
```

## Frontmatter Comparison

### Jekyll

```yaml
---
title: ZeroMQ 서버 통신 라이브러리 소개
date: 2025-12-20 15:00:00 +0900
categories: [zeromq]
tags: [zeromq, 메시징, 통신, 분산시스템]
lang: ko
lang_ref: zeromq-introduction
author: ulala-x
---
```

### Astro

```yaml
---
title: ZeroMQ 서버 통신 라이브러리 소개
pubDatetime: 2025-12-20T15:00:00+09:00
description: ZeroMQ는 고성능 비동기 메시징 라이브러리...
categories: [zeromq]
tags: [zeromq, 메시징, 통신, 분산시스템]
lang: ko
lang_ref: zeromq-introduction
author: Ulala-X
draft: false
---
```

**Key Differences:**
- `date` → `pubDatetime` (ISO 8601 format)
- Added `description` (required for SEO)
- Added `draft` flag

## Build Performance

### Jekyll

```
Build time: ~5-10 seconds (for small site)
Development server: ~2-3 seconds to start
Incremental builds: Yes (with --incremental flag)
Hot reload: Yes
```

### Astro

```
Build time: ~3-5 seconds (for small site)
Development server: ~1-2 seconds to start (Vite)
Incremental builds: Yes (automatic)
Hot reload: Yes (instant with HMR)
```

**Winner:** Astro (faster builds, instant HMR)

## Developer Experience

| Feature | Jekyll | Astro |
|---------|--------|-------|
| **Language** | Ruby | JavaScript/TypeScript |
| **Type Safety** | No | Yes (TypeScript) |
| **Component Model** | Liquid includes | `.astro` components |
| **CSS** | Sass | Tailwind CSS (built-in) |
| **Image Optimization** | Manual | Automatic |
| **Code Splitting** | No | Yes |
| **IDE Support** | Basic | Excellent (VS Code) |
| **Error Messages** | Basic | Detailed with stack traces |

**Winner:** Astro (modern tooling, TypeScript, better DX)

## Features Comparison

| Feature | Jekyll | Astro Prototype | Notes |
|---------|--------|-----------------|-------|
| **Bilingual Support** | ✅ Yes | ✅ Yes | Both support i18n |
| **Dark/Light Theme** | ✅ Yes | ✅ Yes | Both implemented |
| **Categories** | ✅ Yes | ✅ Yes | Frontmatter-based |
| **Tags** | ✅ Yes | ⚠️ Partial | Can be added easily |
| **Search** | ❌ No | ✅ Yes | Pagefind integration |
| **RSS Feed** | ✅ Yes | ✅ Yes | Built-in |
| **Sitemap** | ✅ Yes | ✅ Yes | Automatic |
| **Code Highlighting** | ✅ Yes | ✅ Yes | Shiki (better) |
| **Table of Contents** | ✅ Yes | ✅ Yes | Both supported |
| **Comments** | ⚠️ Partial | ⚠️ Partial | Can add Giscus |
| **Analytics** | ✅ Ready | ✅ Ready | Both configurable |

## Performance Metrics

### Jekyll (Chirpy Theme)

- **Lighthouse Performance:** 95-100
- **JavaScript Size:** ~150KB (Chirpy includes)
- **First Contentful Paint:** ~1.2s
- **Time to Interactive:** ~1.5s
- **Bundle Size:** Moderate (includes jQuery, Bootstrap)

### Astro (AstroPaper Theme)

- **Lighthouse Performance:** 100
- **JavaScript Size:** ~15-30KB (minimal)
- **First Contentful Paint:** ~0.8s
- **Time to Interactive:** ~1.0s
- **Bundle Size:** Small (zero JS by default)

**Winner:** Astro (superior performance, less JavaScript)

## Deployment

### Jekyll

```yaml
# GitHub Pages (automatic)
# .github/workflows/pages.yml exists
- Builds on every push to main
- Deploys to gh-pages branch
- Custom domain support: ✅
```

### Astro

```yaml
# Options:
1. GitHub Pages (needs workflow)
2. Vercel (automatic)
3. Netlify (automatic)
4. Cloudflare Pages (automatic)

# All support:
- Custom domains
- HTTPS
- CDN
- Preview deployments
```

**Winner:** Tie (Jekyll easier on GitHub Pages, Astro more options)

## Ecosystem & Community

### Jekyll

- **Age:** 13+ years (mature)
- **Plugins:** 1000+ plugins
- **Themes:** Hundreds of themes
- **Community:** Large Ruby community
- **Documentation:** Excellent
- **Maintenance:** Active (GitHub maintained)

### Astro

- **Age:** 3+ years (newer, growing)
- **Integrations:** 100+ integrations
- **Themes:** Growing theme ecosystem
- **Community:** Rapidly growing
- **Documentation:** Excellent
- **Maintenance:** Very active development

**Winner:** Jekyll (maturity) vs Astro (modern, momentum)

## Content Migration

### Effort Required

**From Jekyll to Astro:**

1. ✅ **Markdown files:** 95% compatible as-is
2. ⚠️ **Frontmatter:** Minor changes needed (date format)
3. ⚠️ **URLs:** May need redirects if structure changes
4. ❌ **Liquid templates:** Need conversion to Astro components
5. ⚠️ **Plugins:** Check for Astro equivalents

**Estimated time:** 4-8 hours for complete migration

### Migration Steps

```bash
# 1. Copy all posts
cp -r _posts/* astro-prototype/src/data/blog/ko/
cp -r _posts_en/* astro-prototype/src/data/blog/en/

# 2. Update frontmatter (script or manual)
# - date → pubDatetime
# - Add description field

# 3. Test build
cd astro-prototype
npm run build

# 4. Review and fix any errors

# 5. Deploy
```

## Costs

| Aspect | Jekyll | Astro |
|--------|--------|-------|
| **Hosting** | Free (GH Pages) | Free (multiple options) |
| **Build Minutes** | Free (unlimited) | Free (generous limits) |
| **Bandwidth** | Free | Free |
| **Custom Domain** | Free | Free |
| **Total** | $0/month | $0/month |

**Winner:** Tie (both free)

## Maintenance & Updates

### Jekyll

```bash
# Update dependencies
bundle update

# Update theme
# Manual process - check theme repo
```

### Astro

```bash
# Update dependencies
npm update

# Update to latest Astro
npx @astrojs/upgrade

# Auto-fix breaking changes
```

**Winner:** Astro (easier updates, migration tools)

## Pros & Cons Summary

### Jekyll Pros

✅ Mature and battle-tested
✅ Native GitHub Pages support
✅ Huge theme ecosystem
✅ No build step needed locally
✅ Simple liquid templating
✅ Works well for current needs

### Jekyll Cons

❌ Slower build times
❌ Ruby dependency management
❌ Limited modern features
❌ No type safety
❌ Larger JavaScript bundles
❌ Older tooling

### Astro Pros

✅ Superior performance (100 Lighthouse)
✅ Modern developer experience
✅ TypeScript support
✅ Instant HMR
✅ Zero JS by default
✅ Built-in optimizations
✅ Active development
✅ Better search (Pagefind)

### Astro Cons

❌ Newer (less mature)
❌ Smaller theme ecosystem
❌ Requires Node.js
❌ Migration effort needed
❌ Learning curve

## Recommendation

### Stay with Jekyll If:

- ✅ Current setup is working well
- ✅ No performance issues
- ✅ Team is comfortable with Ruby/Liquid
- ✅ Don't want migration effort
- ✅ Happy with GitHub Pages workflow

### Switch to Astro If:

- ✅ Want better performance
- ✅ Prefer modern JavaScript/TypeScript
- ✅ Need faster builds
- ✅ Want better developer experience
- ✅ Plan to add interactive features
- ✅ Want built-in search
- ✅ Willing to invest migration time

## Conclusion

**For ulala-x.github.io:**

Both solutions are excellent. The choice depends on priorities:

- **Keep Jekyll:** If the blog is stable and you prefer minimal changes
- **Switch to Astro:** If you value performance, modern tooling, and developer experience

The Astro prototype demonstrates that migration is feasible and the result would be a faster, more maintainable blog with better developer experience. However, Jekyll is working well currently.

**My recommendation:** Try the Astro prototype locally, add a few more posts, and see if the benefits justify the migration effort for your specific use case.

## Next Steps to Test Astro

1. **Run the dev server:**
   ```bash
   cd astro-prototype
   npm run dev
   ```

2. **Compare the experience:**
   - Browse both Korean and English sites
   - Check build times
   - Test hot reload speed
   - Review the code organization

3. **Add more content:**
   - Try adding 2-3 more posts
   - Test category pages
   - Try customizing the theme

4. **Measure what matters:**
   - Which is faster to work with?
   - Which produces better results?
   - Which is easier to maintain?

Then make an informed decision based on your experience!
