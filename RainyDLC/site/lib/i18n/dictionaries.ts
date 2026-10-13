export type Lang = "ru" | "en" | "uk";

export const LANGS: { code: Lang; label: string }[] = [
  { code: "ru", label: "Русский" },
  { code: "en", label: "English" },
  { code: "uk", label: "Українська" },
];

export interface Dict {
  nav: { mission: string; chapters: string; download: string; faq: string; login: string; account: string; admin: string };
  intro: { tagline: string; skip: string };
  hero: {
    meta: string[];
    line1: string;
    line2: string;
    statement: string;
    ctaMission: string;
    ctaDownload: string;
  };
  marquee: string[];
  mission: {
    kicker: string;
    title: string;
    text: string;
    stats: { value: string; label: string }[];
  };
  chapters: {
    kicker: string;
    title: string;
    subtitle: string;
    items: { n: string; tab: string; title: string; desc: string; points: string[] }[];
  };
  quiz: {
    kicker: string;
    title: string;
    subtitle: string;
    correct: string;
    wrong: string;
    retry: string;
    finalGood: string;
    finalBad: string;
    scenarios: { situation: string; optA: string; optB: string; verdict: string }[];
  };
  download: {
    kicker: string;
    title: string;
    subtitle: string;
    term: string[];
    button: string;
    note: string;
    noAccount: string;
  };
  faq: { kicker: string; title: string; subtitle: string; items: { q: string; a: string }[] };
  footer: {
    label: string;
    meta: string;
    columns: { title: string; links: { label: string; href: string }[] }[];
    backToTop: string;
  };
  auth: {
    title: string;
    tagline: string;
    loginTab: string;
    registerTab: string;
    email: string;
    emailPh: string;
    password: string;
    passwordPh: string;
    passwordHint: string;
    submitLogin: string;
    submitRegister: string;
    checkEmail: string;
    errorLogin: string;
    errorRegister: string;
    loading: string;
  };
  account: {
    title: string;
    welcome: string;
    email: string;
    memberSince: string;
    logout: string;
    clientTitle: string;
    clientDesc: string;
    clientButton: string;
    langTitle: string;
    langDesc: string;
  };
  admin: {
    title: string;
    subtitle: string;
    usersTitle: string;
    statsTotal: string;
    statsBanned: string;
    colEmail: string;
    colCreated: string;
    colStatus: string;
    colActions: string;
    active: string;
    banned: string;
    ban: string;
    unban: string;
    remove: string;
    confirmDelete: string;
    maintTitle: string;
    maintDesc: string;
    maintOn: string;
    maintOff: string;
    maintMsg: string;
    maintMsgPh: string;
    save: string;
    saved: string;
    you: string;
  };
  maintenance: { title: string; text: string; refresh: string };
  notFound: string;
}

const ru: Dict = {
  nav: { mission: "Миссия", chapters: "Главы", download: "Скачать", faq: "FAQ", login: "Войти", account: "Аккаунт", admin: "Админ" },
  intro: { tagline: "чит-клиент нового поколения", skip: "Пропустить" },
  hero: {
    meta: ["HatekClient", "v2.4", "Minecraft 1.21.11", "Fabric", "Бесплатно"],
    line1: "Сервер видит читера.",
    line2: "RainyDLC видит игрока.",
    statement:
      "Чит-клиент нового поколения. Нейро-ротации учатся на твоей игре, AttackAura бьёт по таймингу — а античит видит обычного игрока.",
    ctaMission: "Начать миссию",
    ctaDownload: "Скачать",
  },
  marquee: ["Neuro Rotations", "AttackAura", "Anti-Detect", "HUD-конструктор", "NoRender", "TargetHUD", "Без подписок"],
  mission: {
    kicker: "// твоя миссия",
    title: "Доминируй. Останься невидимкой.",
    text: "Каждый сервер охотится за читерами и смотрит на каждое твоё движение. RainyDLC играет как ты: учится на твоей игре, бьёт по-человечески и не оставляет машинных следов.",
    stats: [
      { value: "100%", label: "бесплатно — без подписок и ключей" },
      { value: "1.21.11", label: "актуальная версия Minecraft" },
      { value: "16+", label: "модулей и переключателей NoRender" },
      { value: "0", label: "машинных паттернов в ротациях" },
    ],
  },
  chapters: {
    kicker: "// три главы",
    title: "Три главы миссии",
    subtitle: "Каждая глава закрывает свою часть игры. Все три — твою победу.",
    items: [
      {
        n: "01",
        tab: "ВИДЕТЬ",
        title: "Видь всё",
        desc: "Нейро-ротации, TargetHUD и ArmorHUD. Каждый противник — как на ладони: дистанция, здоровье, броня.",
        points: ["Neuro Rotations", "TargetHUD", "ArmorHUD"],
      },
      {
        n: "02",
        tab: "БИТЬ",
        title: "Бей точно",
        desc: "AttackAura: криты по таймингу падения, доводка цели и планирование хитов. Ни одного лишнего движения.",
        points: ["AttackAura", "Крит-тайминги", "Доводка цели"],
      },
      {
        n: "03",
        tab: "ИСЧЕЗНУТЬ",
        title: "Исчезни",
        desc: "Anti-Detect и NoRender: микроджиттер, паузы и скорость из твоих собственных записей. Для античита ты — обычный игрок.",
        points: ["Anti-Detect", "NoRender", "GCD-джиттер"],
      },
    ],
  },
  quiz: {
    kicker: "// проверка",
    title: "Поймай бота",
    subtitle: "Три ситуации с сервера. Угадай, где палится читер, — так же рассуждает античит.",
    correct: "Верно",
    wrong: "Мимо",
    retry: "Ещё раз",
    finalGood: "Идеально. Ты думаешь как античит — а RainyDLC думает на шаг вперёд.",
    finalBad: "Античит заметил бы больше. Хорошо, что RainyDLC прячется за тебя.",
    scenarios: [
      {
        situation: "Игрок держит идеальный прицел три минуты подряд.",
        optA: "Игрок",
        optB: "Бот",
        verdict: "Бот. Живые так не умеют. Поэтому RainyDLC добавляет микроджиттер и паузы из твоих записей.",
      },
      {
        situation: "Аура бьёт ровно каждые 625 мс. Все удары — криты.",
        optA: "Легит",
        optB: "Палёво",
        verdict: "Палёво. Метроном плюс 100% критов — это бан. RainyDLC ломает ритм и разбрасывает моменты ударов.",
      },
      {
        situation: "Прицел доводится в центр хитбокса и замирает там.",
        optA: "Норма",
        optB: "Палёво",
        verdict: "Палёво. Идеальный центр — подпись бота. Нейро-ротации целятся туда, куда целишься ты.",
      },
    ],
  },
  download: {
    kicker: "// install",
    title: "Три шага — и ты в игре",
    subtitle: "Без регистрации. Без ключей.",
    term: [
      "# 01 — поставь Fabric Loader для Minecraft 1.21.11",
      "# 02 — закинь rainydlc.jar в папку mods",
      "# 03 — жми Right Shift в игре",
      "$ ./dominate",
    ],
    button: "Скачать с GitHub",
    note: "v2.4 · Minecraft 1.21.11 · Открытый код",
    noAccount: "Аккаунт для скачивания не нужен — он хранит твои настройки и язык.",
  },
  faq: {
    kicker: "// faq",
    title: "Вопросы и ответы",
    subtitle: "Честно о главном.",
    items: [
      {
        q: "Это правда бесплатно?",
        a: "Да. RainyDLC полностью бесплатен — без подписок, ключей и «про»-версий. Скачивание открыто для всех.",
      },
      {
        q: "Меня забанят?",
        a: "Чит — это всегда риск, и честно обещать стопроцентную безопасность не может никто. Но RainyDLC копирует поведение живого игрока: нейро-ротации учатся на твоей игре, а anti-detect убирает машинные паттерны.",
      },
      {
        q: "На каких версиях работает?",
        a: "Minecraft 1.21.11 на Fabric Loader. Актуальный список изменений всегда в релизах на GitHub.",
      },
      {
        q: "Зачем нужен аккаунт?",
        a: "Аккаунт не обязателен для скачивания. Он сохраняет твои настройки и язык на всех устройствах и открывает доступ к будущим функциям — например, облачным конфигам.",
      },
    ],
  },
  footer: {
    label: "RainyDLC",
    meta: "© 2026 · Сделано для игроков",
    columns: [
      {
        title: "Навигация",
        links: [
          { label: "Миссия", href: "/#mission" },
          { label: "Главы", href: "/#chapters" },
          { label: "Скачать", href: "/#download" },
          { label: "FAQ", href: "/#faq" },
        ],
      },
      {
        title: "Клиент",
        links: [
          { label: "GitHub", href: "https://github.com/RainyDLC/FrostixDLC" },
          { label: "Релизы", href: "https://github.com/RainyDLC/FrostixDLC/releases" },
          { label: "Исходный код", href: "https://github.com/RainyDLC/FrostixDLC" },
        ],
      },
      {
        title: "Аккаунт",
        links: [
          { label: "Войти", href: "/auth" },
          { label: "Регистрация", href: "/auth" },
          { label: "Мой аккаунт", href: "/account" },
        ],
      },
    ],
    backToTop: "Наверх ↑",
  },
  auth: {
    title: "Аккаунт RainyDLC",
    tagline: "Настройки, язык и будущие функции — на всех устройствах.",
    loginTab: "Вход",
    registerTab: "Регистрация",
    email: "Email",
    emailPh: "you@example.com",
    password: "Пароль",
    passwordPh: "••••••••",
    passwordHint: "Минимум 8 символов",
    submitLogin: "Войти",
    submitRegister: "Создать аккаунт",
    checkEmail: "Проверь почту — мы отправили ссылку для подтверждения.",
    errorLogin: "Неверный email или пароль.",
    errorRegister: "Не удалось создать аккаунт. Возможно, такой email уже занят.",
    loading: "Секунду…",
  },
  account: {
    title: "Аккаунт",
    welcome: "Привет",
    email: "Email",
    memberSince: "С нами с",
    logout: "Выйти",
    clientTitle: "Твой клиент",
    clientDesc: "Актуальная сборка RainyDLC для Minecraft 1.21.11.",
    clientButton: "Скачать RainyDLC",
    langTitle: "Язык интерфейса",
    langDesc: "Выбери язык — он сохранится на всех устройствах.",
  },
  admin: {
    title: "Админ-панель",
    subtitle: "Доступ есть только у владельца проекта.",
    usersTitle: "Пользователи",
    statsTotal: "Всего",
    statsBanned: "Забанено",
    colEmail: "Email",
    colCreated: "Регистрация",
    colStatus: "Статус",
    colActions: "Действия",
    active: "Активен",
    banned: "Забанен",
    ban: "Забанить",
    unban: "Разбанить",
    remove: "Удалить",
    confirmDelete: "Точно удалить пользователя? Это необратимо.",
    maintTitle: "Технические работы",
    maintDesc: "Когда включено, сайт показывает заглушку всем, кроме админов.",
    maintOn: "Сайт закрыт на техработы",
    maintOff: "Сайт открыт",
    maintMsg: "Сообщение для посетителей",
    maintMsgPh: "Мы что-то улучшаем — скоро вернёмся",
    save: "Сохранить",
    saved: "Сохранено",
    you: "это ты",
  },
  maintenance: {
    title: "Технические работы",
    text: "Мы что-то улучшаем. Скоро вернёмся — обещаем, будет ещё лучше.",
    refresh: "Обновить",
  },
  notFound: "Страница не найдена",
};

const en: Dict = {
  nav: { mission: "Mission", chapters: "Chapters", download: "Download", faq: "FAQ", login: "Log in", account: "Account", admin: "Admin" },
  intro: { tagline: "next-gen cheat client", skip: "Skip" },
  hero: {
    meta: ["HatekClient", "v2.4", "Minecraft 1.21.11", "Fabric", "Free"],
    line1: "The server sees a cheater.",
    line2: "RainyDLC sees a player.",
    statement:
      "A next-gen cheat client. Neuro-rotations learn from your game, AttackAura hits on timing — and the anticheat sees an ordinary player.",
    ctaMission: "Start the mission",
    ctaDownload: "Download",
  },
  marquee: ["Neuro Rotations", "AttackAura", "Anti-Detect", "HUD builder", "NoRender", "TargetHUD", "No subscriptions"],
  mission: {
    kicker: "// your mission",
    title: "Dominate. Stay invisible.",
    text: "Every server hunts cheaters and watches your every move. RainyDLC plays like you: it learns from your game, hits like a human and leaves no machine traces.",
    stats: [
      { value: "100%", label: "free — no subscriptions, no keys" },
      { value: "1.21.11", label: "latest Minecraft version" },
      { value: "16+", label: "modules and NoRender toggles" },
      { value: "0", label: "machine patterns in rotations" },
    ],
  },
  chapters: {
    kicker: "// three chapters",
    title: "Three mission chapters",
    subtitle: "Each chapter covers its part of the game. All three — your victory.",
    items: [
      {
        n: "01",
        tab: "SEE",
        title: "See everything",
        desc: "Neuro-rotations, TargetHUD and ArmorHUD. Every opponent laid bare: distance, health, armor.",
        points: ["Neuro Rotations", "TargetHUD", "ArmorHUD"],
      },
      {
        n: "02",
        tab: "STRIKE",
        title: "Strike precisely",
        desc: "AttackAura: fall-timed crits, target leading and hit planning. Not a single wasted move.",
        points: ["AttackAura", "Crit timings", "Target leading"],
      },
      {
        n: "03",
        tab: "VANISH",
        title: "Vanish",
        desc: "Anti-Detect and NoRender: micro-jitter, pauses and speeds from your own recordings. To the anticheat you're just a player.",
        points: ["Anti-Detect", "NoRender", "GCD jitter"],
      },
    ],
  },
  quiz: {
    kicker: "// check",
    title: "Catch the bot",
    subtitle: "Three situations from a server. Guess where the cheater slips — that's how anticheat thinks.",
    correct: "Correct",
    wrong: "Miss",
    retry: "Retry",
    finalGood: "Perfect. You think like anticheat — and RainyDLC thinks one step ahead.",
    finalBad: "Anticheat would have caught more. Good thing RainyDLC hides for you.",
    scenarios: [
      {
        situation: "A player holds a pixel-perfect aim for three minutes straight.",
        optA: "Player",
        optB: "Bot",
        verdict: "Bot. Humans can't do that. That's why RainyDLC adds micro-jitter and pauses from your recordings.",
      },
      {
        situation: "An aura hits exactly every 625 ms. Every hit is a crit.",
        optA: "Legit",
        optB: "Flagged",
        verdict: "Flagged. A metronome plus 100% crits means a ban. RainyDLC breaks the rhythm and spreads hit timings.",
      },
      {
        situation: "The crosshair snaps to the hitbox center and freezes there.",
        optA: "Fine",
        optB: "Flagged",
        verdict: "Flagged. A perfect center is a bot's signature. Neuro-rotations aim where you aim.",
      },
    ],
  },
  download: {
    kicker: "// install",
    title: "Three steps and you're in",
    subtitle: "No sign-up. No keys.",
    term: [
      "# 01 — install Fabric Loader for Minecraft 1.21.11",
      "# 02 — drop rainydlc.jar into the mods folder",
      "# 03 — press Right Shift in game",
      "$ ./dominate",
    ],
    button: "Download from GitHub",
    note: "v2.4 · Minecraft 1.21.11 · Open source",
    noAccount: "No account needed to download — it keeps your settings and language.",
  },
  faq: {
    kicker: "// faq",
    title: "Questions & answers",
    subtitle: "Straight answers.",
    items: [
      {
        q: "Is it really free?",
        a: "Yes. RainyDLC is completely free — no subscriptions, no keys, no “pro” versions. Downloads are open to everyone.",
      },
      {
        q: "Will I get banned?",
        a: "Cheating always carries risk, and nobody can honestly promise 100% safety. But RainyDLC mimics a living player: neuro-rotations learn from your game and anti-detect removes machine patterns.",
      },
      {
        q: "Which versions are supported?",
        a: "Minecraft 1.21.11 on Fabric Loader. The changelog always lives in the GitHub releases.",
      },
      {
        q: "Why do I need an account?",
        a: "An account isn't required to download. It syncs your settings and language across devices and unlocks future features — like cloud configs.",
      },
    ],
  },
  footer: {
    label: "RainyDLC",
    meta: "© 2026 · Made for players",
    columns: [
      {
        title: "Navigate",
        links: [
          { label: "Mission", href: "/#mission" },
          { label: "Chapters", href: "/#chapters" },
          { label: "Download", href: "/#download" },
          { label: "FAQ", href: "/#faq" },
        ],
      },
      {
        title: "Client",
        links: [
          { label: "GitHub", href: "https://github.com/RainyDLC/FrostixDLC" },
          { label: "Releases", href: "https://github.com/RainyDLC/FrostixDLC/releases" },
          { label: "Source code", href: "https://github.com/RainyDLC/FrostixDLC" },
        ],
      },
      {
        title: "Account",
        links: [
          { label: "Log in", href: "/auth" },
          { label: "Sign up", href: "/auth" },
          { label: "My account", href: "/account" },
        ],
      },
    ],
    backToTop: "Back to top ↑",
  },
  auth: {
    title: "RainyDLC account",
    tagline: "Settings, language and upcoming features — on all your devices.",
    loginTab: "Log in",
    registerTab: "Sign up",
    email: "Email",
    emailPh: "you@example.com",
    password: "Password",
    passwordPh: "••••••••",
    passwordHint: "At least 8 characters",
    submitLogin: "Log in",
    submitRegister: "Create account",
    checkEmail: "Check your inbox — we sent a confirmation link.",
    errorLogin: "Wrong email or password.",
    errorRegister: "Couldn't create the account. This email may already be taken.",
    loading: "One sec…",
  },
  account: {
    title: "Account",
    welcome: "Hey",
    email: "Email",
    memberSince: "With us since",
    logout: "Log out",
    clientTitle: "Your client",
    clientDesc: "The latest RainyDLC build for Minecraft 1.21.11.",
    clientButton: "Download RainyDLC",
    langTitle: "Interface language",
    langDesc: "Pick a language — it syncs across your devices.",
  },
  admin: {
    title: "Admin panel",
    subtitle: "Only the project owner has access.",
    usersTitle: "Users",
    statsTotal: "Total",
    statsBanned: "Banned",
    colEmail: "Email",
    colCreated: "Joined",
    colStatus: "Status",
    colActions: "Actions",
    active: "Active",
    banned: "Banned",
    ban: "Ban",
    unban: "Unban",
    remove: "Delete",
    confirmDelete: "Really delete this user? This cannot be undone.",
    maintTitle: "Maintenance mode",
    maintDesc: "When enabled, everyone except admins sees a placeholder page.",
    maintOn: "Site closed for maintenance",
    maintOff: "Site open",
    maintMsg: "Message for visitors",
    maintMsgPh: "We're improving things — back soon",
    save: "Save",
    saved: "Saved",
    you: "that's you",
  },
  maintenance: {
    title: "Maintenance",
    text: "We're improving something. Back soon — promise it'll be even better.",
    refresh: "Refresh",
  },
  notFound: "Page not found",
};

const uk: Dict = {
  nav: { mission: "Місія", chapters: "Глави", download: "Завантажити", faq: "FAQ", login: "Увійти", account: "Акаунт", admin: "Адмін" },
  intro: { tagline: "чит-клієнт нового покоління", skip: "Пропустити" },
  hero: {
    meta: ["HatekClient", "v2.4", "Minecraft 1.21.11", "Fabric", "Безкоштовно"],
    line1: "Сервер бачить читера.",
    line2: "RainyDLC бачить гравця.",
    statement:
      "Чит-клієнт нового покоління. Нейро-ротації вчаться на твоїй грі, AttackAura б'є за таймінгом — а античит бачить звичайного гравця.",
    ctaMission: "Почати місію",
    ctaDownload: "Завантажити",
  },
  marquee: ["Neuro Rotations", "AttackAura", "Anti-Detect", "HUD-конструктор", "NoRender", "TargetHUD", "Без підписок"],
  mission: {
    kicker: "// твоя місія",
    title: "Домінуй. Лишайся невидимкою.",
    text: "Кожен сервер полює на читерів і стежить за кожним твоїм рухом. RainyDLC грає як ти: вчиться на твоїй грі, б'є по-людськи й не лишає машинних слідів.",
    stats: [
      { value: "100%", label: "безкоштовно — без підписок і ключів" },
      { value: "1.21.11", label: "актуальна версія Minecraft" },
      { value: "16+", label: "модулів і перемикачів NoRender" },
      { value: "0", label: "машинних патернів у ротаціях" },
    ],
  },
  chapters: {
    kicker: "// три глави",
    title: "Три глави місії",
    subtitle: "Кожна глава закриває свою частину гри. Усі три — твою перемогу.",
    items: [
      {
        n: "01",
        tab: "БАЧИТИ",
        title: "Бач усе",
        desc: "Нейро-ротації, TargetHUD і ArmorHUD. Кожен противник — як на долоні: дистанція, здоров'я, броня.",
        points: ["Neuro Rotations", "TargetHUD", "ArmorHUD"],
      },
      {
        n: "02",
        tab: "БИТИ",
        title: "Бий точно",
        desc: "AttackAura: крити за таймінгом падіння, доведення цілі та планування хітів. Жодного зайвого руху.",
        points: ["AttackAura", "Крит-таймінги", "Доведення цілі"],
      },
      {
        n: "03",
        tab: "ЗНИКНУТИ",
        title: "Зникни",
        desc: "Anti-Detect і NoRender: мікроджитер, паузи та швидкість із твоїх власних записів. Для античита ти — звичайний гравець.",
        points: ["Anti-Detect", "NoRender", "GCD-джитер"],
      },
    ],
  },
  quiz: {
    kicker: "// перевірка",
    title: "Спіймай бота",
    subtitle: "Три ситуації з сервера. Вгадай, де палиться читер, — так само міркує античит.",
    correct: "Вірно",
    wrong: "Мимо",
    retry: "Ще раз",
    finalGood: "Ідеально. Ти думаєш як античит — а RainyDLC думає на крок уперед.",
    finalBad: "Античит помітив би більше. Добре, що RainyDLC ховається за тебе.",
    scenarios: [
      {
        situation: "Гравець тримає ідеальний приціл три хвилини поспіль.",
        optA: "Гравець",
        optB: "Бот",
        verdict: "Бот. Живі так не вміють. Тому RainyDLC додає мікроджитер і паузи з твоїх записів.",
      },
      {
        situation: "Аура б'є рівно кожні 625 мс. Усі удари — крити.",
        optA: "Легітимно",
        optB: "Палєво",
        verdict: "Палєво. Метроном плюс 100% критів — це бан. RainyDLC ламає ритм і розкидає моменти ударів.",
      },
      {
        situation: "Приціл доводиться в центр хітбокса й завмирає там.",
        optA: "Норма",
        optB: "Палєво",
        verdict: "Палєво. Ідеальний центр — підпис бота. Нейро-ротації ціляться туди, куди цілишся ти.",
      },
    ],
  },
  download: {
    kicker: "// install",
    title: "Три кроки — і ти в грі",
    subtitle: "Без реєстрації. Без ключів.",
    term: [
      "# 01 — встанови Fabric Loader для Minecraft 1.21.11",
      "# 02 — закинь rainydlc.jar у папку mods",
      "# 03 — тисни Right Shift у грі",
      "$ ./dominate",
    ],
    button: "Завантажити з GitHub",
    note: "v2.4 · Minecraft 1.21.11 · Відкритий код",
    noAccount: "Акаунт для завантаження не потрібен — він зберігає твої налаштування і мову.",
  },
  faq: {
    kicker: "// faq",
    title: "Питання та відповіді",
    subtitle: "Чесно про головне.",
    items: [
      {
        q: "Це справді безкоштовно?",
        a: "Так. RainyDLC повністю безкоштовний — без підписок, ключів і «про»-версій. Завантаження відкрите для всіх.",
      },
      {
        q: "Мене забанять?",
        a: "Чит — це завжди ризик, і чесно обіцяти стовідсоткову безпеку не може ніхто. Але RainyDLC копіює поведінку живого гравця: нейро-ротації вчаться на твоїй грі, а anti-detect прибирає машинні патерни.",
      },
      {
        q: "Які версії підтримуються?",
        a: "Minecraft 1.21.11 на Fabric Loader. Актуальний список змін завжди в релізах на GitHub.",
      },
      {
        q: "Навіщо потрібен акаунт?",
        a: "Акаунт не обов'язковий для завантаження. Він зберігає твої налаштування і мову на всіх пристроях і відкриває доступ до майбутніх функцій — наприклад, хмарних конфігів.",
      },
    ],
  },
  footer: {
    label: "RainyDLC",
    meta: "© 2026 · Зроблено для гравців",
    columns: [
      {
        title: "Навігація",
        links: [
          { label: "Місія", href: "/#mission" },
          { label: "Глави", href: "/#chapters" },
          { label: "Завантажити", href: "/#download" },
          { label: "FAQ", href: "/#faq" },
        ],
      },
      {
        title: "Клієнт",
        links: [
          { label: "GitHub", href: "https://github.com/RainyDLC/FrostixDLC" },
          { label: "Релізи", href: "https://github.com/RainyDLC/FrostixDLC/releases" },
          { label: "Вихідний код", href: "https://github.com/RainyDLC/FrostixDLC" },
        ],
      },
      {
        title: "Акаунт",
        links: [
          { label: "Увійти", href: "/auth" },
          { label: "Реєстрація", href: "/auth" },
          { label: "Мій акаунт", href: "/account" },
        ],
      },
    ],
    backToTop: "Догори ↑",
  },
  auth: {
    title: "Акаунт RainyDLC",
    tagline: "Налаштування, мова та майбутні функції — на всіх пристроях.",
    loginTab: "Вхід",
    registerTab: "Реєстрація",
    email: "Email",
    emailPh: "you@example.com",
    password: "Пароль",
    passwordPh: "••••••••",
    passwordHint: "Мінімум 8 символів",
    submitLogin: "Увійти",
    submitRegister: "Створити акаунт",
    checkEmail: "Перевір пошту — ми надіслали посилання для підтвердження.",
    errorLogin: "Невірний email або пароль.",
    errorRegister: "Не вдалося створити акаунт. Можливо, такий email уже зайнято.",
    loading: "Секунду…",
  },
  account: {
    title: "Акаунт",
    welcome: "Привіт",
    email: "Email",
    memberSince: "З нами з",
    logout: "Вийти",
    clientTitle: "Твій клієнт",
    clientDesc: "Актуальна збірка RainyDLC для Minecraft 1.21.11.",
    clientButton: "Завантажити RainyDLC",
    langTitle: "Мова інтерфейсу",
    langDesc: "Обери мову — вона збережеться на всіх пристроях.",
  },
  admin: {
    title: "Адмін-панель",
    subtitle: "Доступ є лише у власника проєкту.",
    usersTitle: "Користувачі",
    statsTotal: "Всього",
    statsBanned: "Забанено",
    colEmail: "Email",
    colCreated: "Реєстрація",
    colStatus: "Статус",
    colActions: "Дії",
    active: "Активний",
    banned: "Забанений",
    ban: "Забанити",
    unban: "Розбанити",
    remove: "Видалити",
    confirmDelete: "Точно видалити користувача? Це незворотно.",
    maintTitle: "Технічні роботи",
    maintDesc: "Коли ввімкнено, сайт показує заглушку всім, крім адмінів.",
    maintOn: "Сайт закрито на техроботи",
    maintOff: "Сайт відкрито",
    maintMsg: "Повідомлення для відвідувачів",
    maintMsgPh: "Ми щось покращуємо — скоро повернемось",
    save: "Зберегти",
    saved: "Збережено",
    you: "це ти",
  },
  maintenance: {
    title: "Технічні роботи",
    text: "Ми щось покращуємо. Скоро повернемось — обіцяємо, буде ще краще.",
    refresh: "Оновити",
  },
  notFound: "Сторінку не знайдено",
};

export const dictionaries: Record<Lang, Dict> = { ru, en, uk };

export function isLang(v: unknown): v is Lang {
  return v === "ru" || v === "en" || v === "uk";
}
