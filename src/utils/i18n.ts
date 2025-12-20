export type Lang = "ko" | "en";

export const translations = {
  ko: {
    home: "홈",
    posts: "글",
    tags: "태그",
    categories: "카테고리",
    about: "소개",
    search: "검색",
    archives: "아카이브",
    recentPosts: "최근 글",
    featuredPosts: "추천 글",
    allPosts: "모든 글",
    readMore: "더 읽기",
    readingTime: "읽는 시간",
    min: "분",
    published: "발행일",
    updated: "수정일",
    taggedWith: "태그:",
    categoryWith: "카테고리:",
    noPostsFound: "글을 찾을 수 없습니다",
    searchPlaceholder: "글 검색...",
    siteTitle: "ULALA-X",
    siteDesc: "게임 서버와 서버간 통신에 대한 개발 경험과 오픈소스 프로젝트",
    greeting: "안녕하세요",
    intro1: "게임 서버와 서버간 통신에 대한 개발 경험과 오픈소스 프로젝트 (playhouse, zeromq)를 기록하는 공간입니다.",
    intro2: "고성능 게임 서버 아키텍처와 분산 시스템에 대한 실전 경험을 공유합니다.",
  },
  en: {
    home: "Home",
    posts: "Posts",
    tags: "Tags",
    categories: "Categories",
    about: "About",
    search: "Search",
    archives: "Archives",
    recentPosts: "Recent Posts",
    featuredPosts: "Featured Posts",
    allPosts: "All Posts",
    readMore: "Read more",
    readingTime: "Reading time",
    min: "min",
    published: "Published",
    updated: "Updated",
    taggedWith: "Tagged with:",
    categoryWith: "Category:",
    noPostsFound: "No posts found",
    searchPlaceholder: "Search posts...",
    siteTitle: "ULALA-X",
    siteDesc: "Game Servers & Server Communication - Development experiences and open source projects",
    greeting: "Hello",
    intro1: "This space documents development experiences and open source projects (playhouse, zeromq) related to game servers and server communication.",
    intro2: "Sharing practical experiences on high-performance game server architecture and distributed systems.",
  },
};

export function getTranslation(lang: Lang) {
  return translations[lang];
}

export function getAlternateLang(lang: Lang): Lang {
  return lang === "ko" ? "en" : "ko";
}

export function getLangPrefix(lang: Lang): string {
  return lang === "ko" ? "" : "/en";
}

export function getAlternateUrl(currentPath: string, currentLang: Lang): string {
  if (currentLang === "ko") {
    // Korean to English
    // Handle /ko/posts/{slug} -> /en/posts/{slug}
    if (currentPath.startsWith("/ko/posts/")) {
      const slug = currentPath.replace(/^\/ko\/posts\//, '').replace(/-ko$/, '');
      return `/en/posts/${slug}`;
    }
    // Handle /ko/{page} -> /en/{page}
    if (currentPath.startsWith("/ko/")) {
      return currentPath.replace(/^\/ko/, '/en');
    }
    // Handle legacy /{page} -> /en/{page}
    return `/en${currentPath}`;
  } else {
    // English to Korean
    // Handle /en/posts/{slug} -> /ko/posts/{slug}
    if (currentPath.startsWith("/en/posts/")) {
      const slug = currentPath.replace(/^\/en\/posts\//, '');
      return `/ko/posts/${slug}-ko`;
    }
    // Handle /en/{page} -> /ko/{page} or /{page}
    if (currentPath.startsWith("/en/")) {
      return currentPath.replace(/^\/en/, '/ko');
    }
    return currentPath || "/";
  }
}
