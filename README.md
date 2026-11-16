# Запас — backend

Spring Boot 3.3 · Java 21 · PostgreSQL (в том числе Supabase) · Flyway · JdbcClient.
REST-контракт совпадает с `api.js` фронтенда.

## Запуск локально

```bash
docker compose up -d db          # Postgres 16
mvn spring-boot:run              # API на http://localhost:8080
curl localhost:8080/api/state
```

При первом старте Flyway создаёт схему (`V2`), доработки под API (`V3`) и заглушку `auth.users`
с тестовым пользователем (`db/local/V1`). Потом создаётся домохозяйство «Мой дом».

Всё сразу в Docker: `docker compose up --build`.

## Запуск против Supabase

```bash
export SPRING_PROFILES_ACTIVE=supabase
export DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require'
export DB_USER=postgres
export DB_PASSWORD=...
export APP_USER_ID=<uuid существующего пользователя из auth.users>
```

* Через пулер Supavisor в transaction-режиме добавьте к URL `&prepareThreshold=0`.
* Если `V2` уже выполнен руками в SQL Editor, один раз запустите с `FLYWAY_BASELINE_ON_MIGRATE=true`
  (baseline = 2, применится только `V3`). Если схемы ещё нет — ничего не нужно.
* Бэкенд подключается напрямую к БД и обходит RLS. Для таблиц в `public` включите RLS без политик,
  чтобы к ним нельзя было обратиться через PostgREST с anon-ключом.

## Эндпоинты

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
* В `index.html` пути `.idea/css` и `.idea/js` заменены на `css/` и `js/`.

Вкладка «Итоги» по-прежнему закомментирована в `index.html`, как у коллеги. Кнопки «Сбросить данные»
и «+1 день» находятся на ней, так что чтобы ими пользоваться, раскомментируйте `<button data-t="stats">`.

Если фронт будет на отдельном хосте, задайте `APP_CORS_ORIGINS=https://...` и поменяйте `API_BASE` в `config.js`.

## Ограничения

* Авторизации нет: все запросы идут от `APP_USER_ID`. Точка расширения — `CurrentContext`.
  Для мультипользовательского режима нужны Supabase JWT и `householdId` из `household_members`.
* Таблицы `stock_movements`, `stores`, `product_offers`, `shares`, `household_invitations` пока
  не используются API: фронту они не нужны, схема готова под следующие этапы.
