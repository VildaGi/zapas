# Запас — backend

Spring Boot 3.3 · Java 21 · PostgreSQL (в том числе Supabase) · Flyway · JdbcClient.
REST-контракт совпадает с `api.js` фронтенда.

## Запуск локально

```bash
docker compose up -d db          # Postgres 16
mvn spring-boot:run              # API на http://localhost:8080
curl -i localhost:8080/api/state    # 401: нужен вход
```

При первом старте Flyway создаёт схему (`V2`), доработки под API (`V3`), авторизацию (`V4`) и заглушку
`auth.users` (`db/local/V1`). Откройте `http://localhost:8080/` и создайте аккаунт: для него
автоматически создаётся домохозяйство «Мой дом».

Всё сразу в Docker: `docker compose up --build`.

## Запуск против Supabase

```bash
export SPRING_PROFILES_ACTIVE=supabase
export DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require'
export DB_USER=postgres
export DB_PASSWORD=...
```

* Через пулер Supavisor в transaction-режиме добавьте к URL `&prepareThreshold=0`.
* Если `V2` уже выполнен руками в SQL Editor, один раз запустите с `FLYWAY_BASELINE_ON_MIGRATE=true`
  (baseline = 2, применится только `V3`). Если схемы ещё нет — ничего не нужно.
* Бэкенд подключается напрямую к БД и обходит RLS. Для таблиц в `public` включите RLS без политик,
  чтобы к ним нельзя было обратиться через PostgREST с anon-ключом (для `app_users` и `auth_sessions` это делает `V4`).
  Особенно важно закрыть `app_users`: там лежат хеши паролей.

## Авторизация

Email + пароль, серверные сессии. Supabase Auth не используется: Supabase здесь только Postgres.

* Пароль хранится как bcrypt-хеш (перед bcrypt пароль сворачивается SHA-256, поэтому лимита в 72 байта нет).
* После входа сервер ставит cookie `zapas_session`: `HttpOnly`, `SameSite=Strict`, `Secure` на HTTPS.
  В БД лежит только SHA-256 токена (`auth_sessions`), сам токен знает только браузер.
* Сессия живёт `APP_SESSION_DAYS` дней (по умолчанию 30). Выход удаляет её на сервере.
* Все `/api/**`, кроме `/api/auth/register|login|logout`, без сессии отвечают `401 {"message": "Требуется вход"}`.
* Ограничение попыток: 8 неудачных входов на пару IP+email и 30 на IP за 15 минут, 20 регистраций на IP за 15 минут
  (счётчики в памяти, при нескольких инстансах считаются на каждом отдельно).
* За прокси (Render, Railway, nginx) включён `server.forward-headers-strategy: native`: так cookie получает `Secure`,
  а лимиты видят реальный IP. Прокси должен передавать `X-Forwarded-For` и `X-Forwarded-Proto`.

| Метод и путь | Тело | Что делает |
|---|---|---|
| `POST /api/auth/register` | `{email, password}` | Создать аккаунт и сразу войти. 409, если email занят |
| `POST /api/auth/login` | `{email, password}` | Войти. 401 при неверных данных, 429 при частых попытках |
| `POST /api/auth/logout` | — | Выйти, 204 |
| `GET /api/auth/me` | — | `{id, email}` текущего пользователя |

### Изоляция данных

У каждого пользователя своё домохозяйство. `AuthInterceptor` по cookie находит пользователя и кладёт его
`userId` и `householdId` в `CurrentContext`; все репозитории фильтруют по `household_id`, а изменяющие операции
сначала находят запись внутри домохозяйства (`require(h, id)`). Чужой `id` даёт 404, как будто записи нет.

### Миграция `V4__auth.sql`

Создаёт `app_users` и `auth_sessions` (RLS включён: для Supabase они закрыты от anon-ключа) и переносит все внешние
ключи `public.* → auth.users` на `public.app_users`. Владельцы уже существующих данных, например тестовый
пользователь, копируются как «legacy»-аккаунты без пароля, войти в них нельзя. Их данные остаются в БД,
но новые аккаунты начинают с пустого состояния.

## Эндпоинты

Все, кроме `/api/auth/*`, требуют входа.

Каждый изменяющий запрос возвращает актуальное состояние целиком (то же, что `GET /api/state`).

| Метод и путь | Тело | Что делает |
|---|---|---|
| `GET /api/state` | — | Состояние: `offset`, `items`, `list`, `log`, `fresh` |
| `POST /api/items` | `{name, total?, thr?}` | Добавить товар (`total` по умолчанию 30) |
| `PATCH /api/items/{id}` | `{name?, total?, thr?}` | Изменить товар |
| `DELETE /api/items/{id}` | — | Убрать товар (мягкое удаление) |
| `POST /api/items/{id}/bought` | — | «Купил» |
| `POST /api/items/{id}/sudden` | — | «Закончилось внезапно» |
| `POST /api/list` | `{name}` | Добавить позицию |
| `PATCH /api/list/{id}` | `{name?, done?}` | Переименовать / отметить купленным |
| `DELETE /api/list/{id}` | — | Удалить позицию |
| `POST /api/list/clear` | — | Убрать купленное |
| `POST /api/time` | `{offset}` | Тестовый сдвиг времени, дней (абсолютное значение) |
| `POST /api/log` | `{type, itemId?, properties?}` | Клиентское событие (пока только `reminder_opened`), 204 |
| `POST /api/reset` | — | Сбросить данные домохозяйства |

Формат ответа:

```json
{
  "offset": 0,
  "items": [{"id": "uuid", "name": "Шампунь", "total": 30, "start": 1767000000000, "thr": 3, "notified": false}],
  "list":  [{"id": "uuid", "name": "Шампунь", "done": false, "itemId": "uuid", "from": "reminder"}],
  "log":   {"surprise": [{"name": "Кофе", "at": 1767000000000}], "reminders": 2, "prevented": 1},
  "fresh": ["Шампунь"]
}
```

Ошибки: `{"message": "..."}` с кодом 400 или 404 (`apiFetch` читает именно это поле).

## Как данные ложатся на схему

| Фронт | БД |
|---|---|
| item.name | `products.name` (личный товар: `products.household_id`) |
| item.total / thr / start | `stock_items.expected_duration_days` / `reminder_before_days` / `last_restocked_at` |
| item.notified | `stock_items.notified_at` |
| удаление товара | `stock_items.is_active = false` |
| list[] | `list_items` (`done` = `status = 'purchased'`, `from` = `source`) |
| «Купил» / галочка | `purchases` (валюта из `APP_CURRENCY`, по умолчанию EUR) |
| log.reminders / prevented / surprise | `events`: `reminder_shown` / `bought_early` / `ran_out_suddenly` |
| offset | `households.time_offset_days` |

Миграция `V3` добавляет: `households.time_offset_days`, `stock_items.notified_at`,
`products.household_id` и значение `sudden` в check у `list_items.source`.

## Фронтенд

Доработанный фронт лежит в `src/main/resources/static` и отдаётся самим Spring Boot.
Открывайте `http://localhost:8080/`, `API_BASE = '/api'` работает без CORS.

Что изменилось относительно версии с localStorage:

* `storage.js` удалён, `state.js` больше не создаёт тестовые данные: состояние приходит из `GET /api/state`.
  Первая загрузка показывает «Загрузка…», при ошибке есть кнопка «Повторить».
* Каждое действие в `action.js` — вызов API, а ответ (новое состояние целиком) заменяет `S` и перерисовывает экран.
  Функции `check()`, `save()`, `seed()` не нужны: напоминания считает сервер.
* Формы работают через `data-f` и событие `submit`, а не через inline-`onsubmit`.
  Поэтому id-UUID не ломают разметку.
* Режим редактирования хранится отдельно (`editingItems`, `editingList`) и не теряется при обновлении состояния.
* Состояние обновляется раз в минуту и при возвращении на вкладку. Пока вы печатаете в поле, обновление пропускается.
* Если сервер вернул `fresh`, показывается системное уведомление (после разрешения на вкладке «Итоги»).
* Добавлен `API.reset` для кнопки «Сбросить данные».
* Вход и регистрация: форма в `render.js` (`renderAuth`), логика в `action.js` (`startSession` / `endSession`).
  При старте `app.js` спрашивает `GET /api/auth/me`: сессия есть — грузятся данные, нет — форма входа.
  Если сервер ответил 401 на обычный запрос (сессия истекла), фронт возвращается на форму входа.
  При выходе состояние на экране очищается. Заметки к позициям списка (`listMeta`, количество и единицы в localStorage)
  хранятся отдельно для каждого пользователя.
* В `index.html` пути `.idea/css` и `.idea/js` заменены на `css/` и `js/`.

Вкладка «Итоги» по-прежнему закомментирована в `index.html`, как у коллеги. Кнопки «Сбросить данные»
и «+1 день» находятся на ней, так что чтобы ими пользоваться, раскомментируйте `<button data-t="stats">`.

Фронт нужно отдавать с того же origin, что и API: cookie сессии с `SameSite=Strict` на другой хост не уйдёт.
Если понадобится отдельный хост, потребуются `SameSite=None; Secure`, `allowCredentials` в CORS и защита от CSRF.

## Ограничения

* Сброса и смены пароля, подтверждения email нет: регистрация открытая, email не проверяется.
* Одно домохозяйство на пользователя. Таблицы `household_members` и `household_invitations` готовы под совместный
  доступ, но API для приглашений пока нет.
* Количество, единицы и заметки позиций списка по-прежнему хранятся в localStorage браузера, а не на сервере.
* Таблицы `stock_movements`, `stores`, `product_offers`, `shares` пока не используются API.
