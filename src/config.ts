export const SITE = {
  website: "https://ulala-x.github.io/", // replace this with your deployed domain
  author: "Ulala-X",
  profile: "https://github.com/ulala-x",
  desc: "Game Servers & Server Communication - 게임 서버와 서버간 통신에 대한 개발 경험과 오픈소스 프로젝트",
  title: "ULALA-X",
  ogImage: "astropaper-og.jpg",
  lightAndDarkMode: true,
  postPerIndex: 10,
  postPerPage: 10,
  scheduledPostMargin: 15 * 60 * 1000, // 15 minutes
  showArchives: true,
  showBackButton: true, // show back button in post detail
  editPost: {
    enabled: false,
    text: "Edit page",
    url: "https://github.com/ulala-x/ulala-x.github.io/edit/main/",
  },
  dynamicOgImage: true,
  dir: "ltr", // "rtl" | "auto"
  lang: "ko", // html lang code. Set this empty and default will be "en"
  timezone: "Asia/Seoul", // Default global timezone (IANA format) https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
} as const;
