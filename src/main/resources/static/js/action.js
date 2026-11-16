// Системное уведомление по товарам, у которых напоминание сработало в этом запросе
function notifyFresh(names) {
  if (!names || !names.length) return;
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('Запас: заканчивается', { body: names.join(', ') });
    }
  } catch (e) {}
}

function applyState(st) {
  S = st;
  loaded = true;
  loadError = null;
  notifyFresh(st.fresh);
  render();
}

// Загрузка состояния. silent — не показывать тост при ошибке (фоновое обновление).
function refresh(silent) {
  return API.getState().then(applyState).catch(function (e) {
    if (!loaded) {
      loadError = e.message;
      render();
    } else if (!silent) {
      toast('Нет связи с сервером');
    }
  });
}

// Выполнить запрос, который возвращает состояние; onOk вызывается после успеха.
function act(promise, msg, onOk) {
  return promise.then(function (st) {
    if (onOk) onOk();
    applyState(st);
    if (msg) toast(msg);
  }).catch(function (e) {
    toast('Ошибка: ' + e.message);
    render();
  });
}

// Фоновое обновление не должно стирать то, что человек сейчас печатает
function isTyping() {
  var a = document.activeElement;
  return !!a && a.tagName === 'INPUT' && a.type !== 'checkbox' &&
    document.getElementById('v').contains(a);
}

// ---------- формы (data-f на <form>) ----------
function handleSubmit(e) {
  var f = e.target;
  var kind = f.dataset && f.dataset.f;
  if (!kind) return;
  e.preventDefault();
  var id = f.dataset.id;

  if (kind === 'add-item') {
    var n = f.n.value.trim();
    if (n) act(API.addItem({ name: n, total: +f.d.value || 30 }));
  }

  if (kind === 'add-list') {
    var t = f.n.value.trim();
    if (t) act(API.addList({ name: t }));
  }

  if (kind === 'save-item') {
    var it = byId(id);
    var name = f.name.value.trim();
    if (!it || !name) return;
    act(API.patchItem(id, {
      name: name,
      total: +f.total.value || it.total,
      thr: f.thr.value === '' ? it.thr : +f.thr.value
    }), 'Сохранено', function () { delete editingItems[id]; });
  }

  if (kind === 'save-list') {
    var ln = f.name.value.trim();
    if (!ln) return;
    act(API.patchList(id, { name: ln }), 'Сохранено', function () { delete editingList[id]; });
  }
}

// ---------- клики по data-a ----------
function handleClick(e) {
  var b = e.target.closest('[data-a]');
  if (!b) return;
  var a = b.dataset.a, id = b.dataset.id;

  switch (a) {
    case 'bought':
      act(API.bought(id), 'Запас обновлён');
      break;

    case 'sudden':
      act(API.sudden(id), 'Записано. Добавили в список');
      break;

    case 'del':
      act(API.delItem(id));
      break;

    case 'share':
      var i = byId(id);
      if (!i) return;
      var d = daysLeft(i);
      share('Нужно купить: ' + i.name + (d > 0 ? ' (хватит ещё на ' + d + ' ' + plural(d) + ')' : ' (закончился)'));
      break;

    case 'tick':
      act(API.patchList(id, { done: b.checked }));
      break;

    case 'sharelist':
      var text = S.list.filter(function (l) { return !l.done; })
        .map(function (l) { return '• ' + l.name; }).join('\n');
      share('Список покупок:\n' + (text || 'всё куплено'));
      break;

    case 'clear':
      act(API.clearList());
      break;

    case 'day':
      act(API.setOffset(S.offset + 1), 'Прошёл ещё 1 день');
      break;

    case 'notif':
      try {
        Notification.requestPermission().then(function (p) {
          toast(p === 'granted' ? 'Уведомления включены' : 'Уведомления недоступны');
        });
      } catch (x) {
        toast('Уведомления недоступны в этом браузере');
      }
      break;

    case 'reset':
      if (confirm('Удалить все данные?')) act(API.reset());
      break;

    case 'edit-item':
      editingItems[id] = true;
      render();
      break;
    case 'cancel-item-edit':
      delete editingItems[id];
      render();
      break;

    case 'edit-list':
      editingList[id] = true;
      render();
      break;
    case 'cancel-list-edit':
      delete editingList[id];
      render();
      break;
    case 'del-list':
      act(API.delList(id));
      break;

    case 'retry':
      refresh();
      break;
  }
}
