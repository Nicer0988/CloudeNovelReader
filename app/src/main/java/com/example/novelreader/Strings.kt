package com.example.novelreader

object L {
    val names = listOf("English", "Русский", "Українська", "Қазақша", "Беларуская", "Oʻzbekcha")

    private fun r(vararg s: String) = s.toList()

    // порядок: en, ru, uk, kk, be, uz
    private val m: Map<String, List<String>> = mapOf(
        "library" to r("Library", "Библиотека", "Бібліотека", "Кітапхана", "Бібліятэка", "Kutubxona"),
        "sites" to r("Sites", "Сайты", "Сайти", "Сайттар", "Сайты", "Saytlar"),
        "settings" to r("Settings", "Настройки", "Налаштування", "Баптаулар", "Налады", "Sozlamalar"),
        "reading" to r("Reading", "Читаю", "Читаю", "Оқып жатырмын", "Чытаю", "Oʻqiyapman"),
        "dropped" to r("Dropped", "Заброшено", "Закинуто", "Тасталған", "Закінута", "Tashlab qoʻyilgan"),
        "planned" to r("Plan to read", "Буду читать", "Буду читати", "Оқимын", "Буду чытаць", "Oʻqiyman"),
        "downloaded" to r("Downloaded", "Скачанные", "Завантажені", "Жүктелгендер", "Спампаваныя", "Yuklab olingan"),
        "new_tab" to r("New tab", "Новая вкладка", "Нова вкладка", "Жаңа қойынды", "Новая ўкладка", "Yangi boʻlim"),
        "back" to r("Back", "Назад", "Назад", "Артқа", "Назад", "Orqaga"),
        "next" to r("Next", "Вперёд", "Далі", "Алға", "Далей", "Keyingi"),
        "name" to r("Name", "Название", "Назва", "Атауы", "Назва", "Nomi"),
        "add" to r("Add", "Добавить", "Додати", "Қосу", "Дадаць", "Qoʻshish"),
        "cancel" to r("Cancel", "Отмена", "Скасувати", "Бас тарту", "Адмена", "Bekor qilish"),
        "delete" to r("Delete", "Удалить", "Видалити", "Жою", "Выдаліць", "Oʻchirish"),
        "add_to" to r("Add to", "Добавить в", "Додати до", "Бөлімге қосу", "Дадаць у", "Boʻlimga qoʻshish"),
        "change_cover" to r("Change cover", "Поменять обложку", "Змінити обкладинку", "Мұқабаны ауыстыру", "Змяніць вокладку", "Muqovani almashtirish"),
        "delete_novel" to r("Delete novel", "Удалить новеллу", "Видалити новелу", "Новелланы жою", "Выдаліць навэлу", "Romanni oʻchirish"),
        "theme" to r("Theme", "Тема", "Тема", "Тақырып", "Тэма", "Mavzu"),
        "language" to r("Language", "Язык", "Мова", "Тіл", "Мова", "Til"),
        "account" to r("Account", "Аккаунт", "Обліковий запис", "Аккаунт", "Уліковы запіс", "Hisob"),
        "sign_in" to r("Sign in with Google", "Войти через Google", "Увійти через Google", "Google арқылы кіру", "Увайсці праз Google", "Google orqali kirish"),
        "sign_out" to r("Sign out", "Выйти", "Вийти", "Шығу", "Выйсці", "Chiqish"),
        "sync_now" to r("Sync now", "Синхронизировать", "Синхронізувати", "Синхрондау", "Сінхранізаваць", "Sinxronlash"),
        "signed_as" to r("Signed in as", "Вы вошли как", "Ви увійшли як", "Сіз кірдіңіз", "Вы ўвайшлі як", "Hisob"),
        "reader" to r("Text settings", "Настройки текста", "Налаштування тексту", "Мәтін баптаулары", "Налады тэксту", "Matn sozlamalari"),
        "font" to r("Font", "Шрифт", "Шрифт", "Қаріп", "Шрыфт", "Shrift"),
        "size" to r("Size", "Размер", "Розмір", "Өлшемі", "Памер", "Oʻlcham"),
        "letter" to r("Letter spacing", "Межбуквенный интервал", "Міжлітерний інтервал", "Әріп аралығы", "Міжлітарны інтэрвал", "Harflar oraligʻi"),
        "line" to r("Line spacing", "Межстрочный интервал", "Міжрядковий інтервал", "Жол аралығы", "Міжрадковы інтэрвал", "Qatorlar oraligʻi"),
        "margin" to r("Side margins", "Отступы по бокам", "Поля з боків", "Жан жақ шеттері", "Палі па баках", "Yon chetlar"),
        "paragraph" to r("Paragraph", "Абзац", "Абзац", "Абзац", "Абзац", "Xatboshi"),
        "page" to r("Page", "Страница", "Сторінка", "Бет", "Старонка", "Sahifa"),
        "add_site" to r("Add site", "Добавить сайт", "Додати сайт", "Сайт қосу", "Дадаць сайт", "Sayt qoʻshish"),
        "url" to r("Address (URL)", "Адрес (URL)", "Адреса (URL)", "Мекенжай (URL)", "Адрас (URL)", "Manzil (URL)"),
        "downloading" to r("Downloading…", "Скачивание…", "Завантаження…", "Жүктелуде…", "Спампоўка…", "Yuklanmoqda…"),
        "added" to r("Added", "Добавлено", "Додано", "Қосылды", "Дададзена", "Qoʻshildi"),
        "error" to r("Error", "Ошибка", "Помилка", "Қате", "Памылка", "Xato"),
        "synced" to r("Synced", "Синхронизировано", "Синхронізовано", "Синхрондалды", "Сінхранізавана", "Sinxronlandi"),
        "about_cloud" to r(
            "Sign in to keep your library even after reinstalling the app.",
            "Войдите, чтобы библиотека сохранялась даже после переустановки приложения.",
            "Увійдіть, щоб бібліотека зберігалася навіть після перевстановлення застосунку.",
            "Қолданбаны қайта орнатқаннан кейін де кітапхана сақталуы үшін кіріңіз.",
            "Увайдзіце, каб бібліятэка захоўвалася нават пасля пераўсталёўкі праграмы.",
            "Ilovani qayta oʻrnatgandan keyin ham kutubxona saqlanishi uchun kiring."
        ),
        "th0" to r("Dark", "Тёмная", "Темна", "Қараңғы", "Цёмная", "Qorongʻi"),
        "th1" to r("Light", "Светлая", "Світла", "Жарық", "Светлая", "Yorugʻ"),
        "th2" to r("Purple", "Фиолетовая", "Фіолетова", "Күлгін", "Фіялетавая", "Binafsha"),
        "th3" to r("Ocean", "Океан", "Океан", "Мұхит", "Акіян", "Okean"),
        "th4" to r("Sunset", "Закат", "Захід", "Күн батуы", "Захад", "Quyosh botishi")
    )

    fun t(k: String, lang: Int): String = m[k]?.getOrNull(lang) ?: m[k]?.get(0) ?: k
}
