# Визуальный форк лаунчера EXBO: zone-редизайн на JavaFX

![Главный экран STALZONE](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/04_main.png)

Прошло уже много лет с момента перехода игры с Gravit на свой собственный лаунчер. Время шло — игра росла, сайт менялся, зона обрастала новым контентом — а **дизайн лаунчера стремительно стоял на месте**. В каком-то смысле он стал капсулой времени: те же пропорции окна, та же плотность кнопок, тот же визуальный язык эпохи, когда JavaFX-лаунчер только вставал на ноги после Gravit. Это не плохо само по себе — капсула честно хранит память о прошлом. Но вокруг уже другой продукт: другой сайт, другая типографика, другие ожидания от входа, профиля и настроек. Лаунчер при этом по-прежнему встречает игрока первым.

Отсюда и этот проект. Это не «новый официальный лаунчер» и не клиент с серверами. Это **визуальный форк**: только UI поверх локальных моков. Каркас EXBO / STALCRAFT узнаваем — два окна, список игр, ИГРАТЬ — а оболочка собрана заново под язык сайта STALZONE: тёмная зона, красный акцент `#e91932`, Bebas Neue + Roboto, нормальный ресайз, один стиль на входе, регистрации и 2FA.

Репозиторий: [Nyrokume/exbo-launcher-fork](https://github.com/Nyrokume/exbo-launcher-fork)

---

## Зачем трогать то, что «и так работает»

Оригинал сидит на раскладке своего поколения: главное окно около **886×550**, окна входа компактные, почти без воздуха. Для своего времени это было нормально. Сегодня рядом с сайтом и обновлённым брендом лаунчер выглядит как гость из другой эпохи — не сломанный, а **застывший**.

Хочется не выкинуть привычный сценарий, а дать ему современную оболочку:

- нормальный ресайз главного окна (1024×640, минимум 900×580);
- один визуальный язык на обновлении, входе, возрасте, регистрации и TOTP;
- настройки на один экран без прокрутки;
- меню профиля / сообществ / действий с игрой «⋯» как у живого продукта;
- обои и контраст текста, которые не разваливаются при растягивании.

Всё это можно крутить без сети: любой логин/пароль, скачивание и «игра запущена» — фейки. Так удобнее спорить про UI, а не про бэкенд.

---

## Как устроены окна

Два окна, как в оригинале:

1. **Pre-main** — обновление (**460×316**), вход, возраст, регистрация, код из почты / TOTP. Без рамки, ширина 460, высота по панели, потолок **700**.
2. **Main** — список игр, баннер, настройки, прогресс. Без рамки, ресайз, **1024×640** (минимум 900×580).

После входа окно авторизации закрывается, открывается главное.

![Обновление](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/01_update_window.png)

![Вход](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/02_auth_window.png)

---

## Регистрация и 2FA

Цепочка как на сайте: возраст → форма → снова вход с ником → при необходимости второй фактор.

![Возрастной гейт](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/14_age_gate.png)

![Регистрация](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/16_register.png)

![Ошибка почты](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/17_register_error.png)

Ошибки — компактный тост в потоке формы (красная полоска слева), а не всплывашка поверх кнопки.

![Аккаунт создан](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/18_registered_login.png)

![TOTP с ошибкой](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/19_totp_error.png)

Демо: логин `totp` всегда ведёт на аутентификатор; код `000000` показывает ошибку. Обычный вход — любой пароль; второй фактор мок выбирает случайно (почта / TOTP), если не задать `-Dlauncher.mock2fa=…`.

---

## Главный экран

Слева — игры и настройки. Справа сверху — сообщества / форум / поддержка и профиль. По центру — обои + описание STALZONE. Снизу — статус и **ИГРАТЬ**.

![Профиль](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/05_profile_menu.png)

![Сообщества](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/05c_community_menu.png)

![Подтверждение выхода](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/05b_logout_confirm.png)

Меню «⋯» у кнопки запуска: проверить файлы, папки, логи, настройки игры, переустановка, удаление.

![Действия с игрой](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/08_game_actions.png)

![Удаление](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/08b_game_actions_delete_confirm.png)

Состояния клиента тоже сняты автотуром:

![Запущена](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/09_running.png)

![Загрузка](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/10b_downloading.png)

![Пауза](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/10c_paused.png)

---

## Настройки

Один экран: RAM сегментами по 1 ГБ, поведение, лимит скорости, язык. Без внутренней прокрутки на минимальном размере окна.

![Настройки](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/06_settings.png)

![Минимальный размер](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/07_settings_min_size.png)

![EN](https://raw.githubusercontent.com/Nyrokume/exbo-launcher-fork/main/docs/screenshots/zone/12_settings_en.png)

---

## Три темы на одной раскладке

| Тема | Что это |
|------|---------|
| **zone** (по умолчанию) | Красный STALZONE, Bebas Neue + Roboto |
| **exbo** | Ближе к исходным цветам EXBO |
| **shadcn** | Тёмный neutral в духе shadcn/ui |

Переключение: `-Dlauncher.theme=zone|exbo|shadcn`.

Скриншоты всех трёх лежат в репозитории:

- [zone](https://github.com/Nyrokume/exbo-launcher-fork/tree/main/docs/screenshots/zone)
- [exbo](https://github.com/Nyrokume/exbo-launcher-fork/tree/main/docs/screenshots) (корень `docs/screenshots/`)
- [shadcn](https://github.com/Nyrokume/exbo-launcher-fork/tree/main/docs/screenshots/shadcn)

---

## Стек и запуск

**Kotlin 2.0 + OpenJFX 21 + JDK 21**, Gradle wrapper.

```bash
./gradlew run
./gradlew run -Dlauncher.lang=en
./gradlew run -Dlauncher.theme=shadcn
```

Живой тур по экранам с анимациями:

```bash
./gradlew run -Dlauncher.showcase=true
```

Пересъём PNG (Scene snapshot, не запись монитора):

```bash
./gradlew run -Dlauncher.screenshots=docs/screenshots/zone -Dlauncher.animations=off
```

Подробности — в [README](https://github.com/Nyrokume/exbo-launcher-fork/blob/main/README.md) и [docs/SHOWCASE.md](https://github.com/Nyrokume/exbo-launcher-fork/blob/main/docs/SHOWCASE.md).

---

## Важно

- Нет реальной авторизации EXBO, загрузки клиента и античита — только UI + моки.
- Это учебный / дизайн-форк для итераций по интерфейсу, не замена официального лаунчера.
- Иконки, логотипы и атмосфера STALZONE / EXBO используются в контексте визуального прототипа.

Если интересно потыкать или покритиковать раскладку/тосты/меню — пишите. Скриншоты выше можно листать прямо в GitHub без сборки.
