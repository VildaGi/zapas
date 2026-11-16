// Состояние приходит с сервера (GET /api/state); здесь только копия для отрисовки.
var DAY = 864e5;

var tab = 'stock';
var loaded = false;      // первое состояние получено
var loadError = null;    // текст ошибки первой загрузки
var S = { offset: 0, items: [], list: [], log: { surprise: [], reminders: 0, prevented: 0 }, fresh: [] };

// Какие карточки сейчас в режиме редактирования (только на клиенте): { id: true }
var editingItems = {};
var editingList = {};

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
