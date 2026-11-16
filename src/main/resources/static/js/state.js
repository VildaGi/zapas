// Состояние приходит с сервера (GET /api/state); здесь только копия для отрисовки.
var DAY = 864e5;

var tab = 'stock';
var loaded = false;      // первое состояние получено
var loadError = null;    // текст ошибки первой загрузки
var S = { offset: 0, items: [], list: [], log: { surprise: [], reminders: 0, prevented: 0 }, fresh: [] };

// Режим редактирования: {t:'s'|'l', id} или null. Не теряется при refresh.
var edit = null;

// Клиентские дополнения к позициям списка: { [listItemId]: {q, u, c} }.
// Бэкенд пока не хранит quantity/unit/note в list_items через API —
// держим их локально, чтобы UI и share работали как в артефакте.
var META_KEY = 'zapas-list-meta-v1';
var listMeta = {};
try { listMeta = JSON.parse(localStorage.getItem(META_KEY) || '{}'); } catch (e) { listMeta = {}; }

function saveMeta() {
    try { localStorage.setItem(META_KEY, JSON.stringify(listMeta)); } catch (e) {}
}

function metaOf(l) { return listMeta[l.id] || {}; }

// Время «как на сервере»: реальное + тестовое смещение в днях
function now() {
    return Date.now() + S.offset * DAY;
}

function daysLeft(i) {
    return Math.ceil(i.total - (now() - i.start) / DAY);
}

function status(i) {
    var d = daysLeft(i);
    return d <= 0 ? 'out' : d <= i.thr ? 'soon' : 'ok';
}

function byId(id) {
    return S.items.filter(function (i) { return i.id === id; })[0];
}

function listById(id) {
    return S.list.filter(function (l) { return l.id === id; })[0];
}