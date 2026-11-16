// Отрисовка вкладок
function render() {
  document.querySelectorAll('nav button').forEach(function (b) {
    b.setAttribute('aria-selected', b.dataset.t === tab);
  });

  var h = document.getElementById('h');
  var s = document.getElementById('s');
  var v = document.getElementById('v');

  if (!loaded) {
    h.textContent = 'Запас';
    s.textContent = '';
    v.innerHTML = loadError
      ? '<div class="empty">Не удалось загрузить данные:<br>' + esc(loadError) +
        '<br><br><button class="p" data-a="retry">Повторить</button></div>'
      : '<div class="empty">Загрузка…</div>';
    return;
  }

  var o = '';
  if (tab === 'stock') o = renderStock(h, s);
  if (tab === 'list') o = renderList(h, s);
  if (tab === 'stats') o = renderStats(h, s);
  v.innerHTML = o;
}

function renderStock(h, s) {
  h.textContent = 'Запасы дома';
  s.textContent = 'Что скоро закончится — узнаете заранее';
  var o = '';

  var soon = S.items.filter(function (i) { return status(i) !== 'ok'; });
  if (soon.length) {
    o += '<div class="banner"><b>Пора пополнить:</b> ' +
      soon.map(function (i) { return esc(i.name); }).join(', ') +
      '. Уже в списке покупок.</div>';
  }

  o += '<form class="add" data-f="add-item">' +
    '<input name="n" placeholder="Новый товар, например «Кофе»" required>' +
    '<label class="term" title="На сколько дней хватает одной упаковки">' +
    '<span>Срок, дней</span>' +
    '<input class="n" name="d" type="number" min="1" max="365" value="30">' +
    '</label>' +
    '<button class="p">Добавить</button>' +
    '</form>';

  var items = S.items.slice().sort(function (a, b) { return daysLeft(a) - daysLeft(b); });
  if (!items.length) {
    o += '<div class="empty">Добавьте первый товар и укажите, на сколько дней его хватает.</div>';
  }

  items.forEach(function (i) {
    var d = daysLeft(i), st = status(i);
    var pct = Math.max(0, Math.min(100, d / i.total * 100));

    if (editingItems[i.id]) {
      o += '<form class="item ' + st + '" data-f="save-item" data-id="' + i.id + '">' +
        '<div class="row"><span class="name">Редактирование</span></div>' +
        '<div class="add" style="margin-top:8px">' +
        '<input name="name" value="' + esc(i.name) + '" required>' +
        '<label class="term"><span>Срок, дней</span><input class="n" name="total" type="number" min="1" max="3650" value="' + i.total + '"></label>' +
        '<label class="term"><span>Порог, дней</span><input class="n" name="thr" type="number" min="0" max="3650" value="' + i.thr + '"></label>' +
        '</div>' +
        '<div class="acts" style="margin-top:8px">' +
        '<button class="p">Сохранить</button>' +
        '<button type="button" data-a="cancel-item-edit" data-id="' + i.id + '">Отмена</button>' +
        '</div></form>';
      return;
    }

    o += '<div class="item ' + st + '">' +
      '<div class="row"><span class="name">' + esc(i.name) + '</span>' +
      '<span class="left">' + (d <= 0 ? 'закончился' : 'ещё ' + d + ' ' + plural(d)) + '</span></div>' +
      '<div class="bar"><i style="width:' + pct + '%"></i></div>' +
      '<div class="acts">' +
      '<button class="p" data-a="bought" data-id="' + i.id + '">Купил</button>' +
      '<button data-a="edit-item" data-id="' + i.id + '">Изменить</button>' +
      '<button data-a="share" data-id="' + i.id + '">Поделиться</button>' +
      '<button class="d" data-a="sudden" data-id="' + i.id + '">Закончилось внезапно</button>' +
      '<button data-a="del" data-id="' + i.id + '" aria-label="Удалить">✕</button>' +
      '</div></div>';
  });

  o += '<p class="note"><b>Срок</b> — на сколько дней хватает одной упаковки. Напоминание придёт за несколько дней до конца срока.</p>' +
    '<p class="note">«Закончилось внезапно» — отметьте, если товар кончился раньше, чем вы ожидали. Это главный сигнал для проверки гипотезы.</p>';
  return o;
}

function renderList(h, s) {
  var open = S.list.filter(function (l) { return !l.done; }).length;
  h.textContent = 'Список покупок';
  s.textContent = open ? 'Осталось купить: ' + open : 'Всё куплено';
  var o = '';

  o += '<form class="add" data-f="add-list">' +
    '<input name="n" placeholder="Что купить?" required>' +
    '<button class="p">Добавить</button>' +
    '</form>';

  if (!S.list.length) {
    o += '<div class="empty">Список пуст. Товары, которые заканчиваются, появятся здесь сами.</div>';
  }

  S.list.slice().sort(function (a, b) { return a.done - b.done; }).forEach(function (l) {
    if (editingList[l.id]) {
      o += '<form class="li" data-f="save-list" data-id="' + l.id + '">' +
        '<input name="name" value="' + esc(l.name) + '" required>' +
        '<button class="p">OK</button>' +
        '<button type="button" data-a="cancel-list-edit" data-id="' + l.id + '">✕</button>' +
        '</form>';
      return;
    }
    o += '<label class="li' + (l.done ? ' done' : '') + '">' +
      '<input type="checkbox" data-a="tick" data-id="' + l.id + '"' + (l.done ? ' checked' : '') + '>' +
      '<span class="t">' + esc(l.name) +
      (l.from === 'reminder' ? '<br><span class="tag">добавлено по напоминанию</span>' : '') +
      '</span>' +
      '<button type="button" data-a="edit-list" data-id="' + l.id + '" aria-label="Изменить">✎</button>' +
      '<button type="button" data-a="del-list" data-id="' + l.id + '" aria-label="Удалить">✕</button>' +
      '</label>';
  });

  if (S.list.length) {
    o += '<div class="acts">' +
      '<button class="p" data-a="sharelist">Отправить список</button>' +
      '<button data-a="clear">Убрать купленное</button>' +
      '</div>';
  }

  o += '<p class="note">Совместный список в реальном времени — следующий шаг после проверки гипотезы. Пока список отправляется текстом в любой мессенджер.</p>';
  return o;
}

function renderStats(h, s) {
  h.textContent = 'Итоги проверки';
  s.textContent = 'Гипотеза: нужные товары заканчиваются в неподходящий момент';
  var o = '';

  var n = S.log.surprise.length;
  o += '<div class="stat">' +
    '<div><b>' + n + '</b><span>внезапно закончилось</span></div>' +
    '<div><b>' + S.log.reminders + '</b><span>напоминаний показано</span></div>' +
    '<div><b>' + S.log.prevented + '</b><span>куплено заранее</span></div>' +
    '</div>';

  var tot = S.log.prevented + n;
  o += '<div class="item"><div class="name">' +
    (tot ? Math.round(S.log.prevented / tot * 100) + '% нехваток предотвращено' : 'Пока мало данных') +
    '</div><div class="left">Доля товаров, купленных по напоминанию, среди всех нехваток (заранее + внезапно).</div></div>';

  if (n) {
    o += '<div class="item"><div class="name">Что заканчивалось внезапно</div>' +
      S.log.surprise.map(function (x) { return '<div class="left">' + esc(x.name) + '</div>'; }).join('') +
      '</div>';
  }

  o += '<div class="item"><div class="name">Тест на 1–2 недели</div><div class="left">Пользуйтесь приложением в быту. Если внезапных нехваток много, гипотеза подтверждается. Если после напоминаний их почти нет, решение работает.</div></div>';

  o += '<div class="acts">' +
    '<button data-a="notif">Включить уведомления</button>' +
    '<button data-a="day">Сдвинуть время на +1 день (тест)</button>' +
    '<button class="d" data-a="reset">Сбросить данные</button>' +
    '</div>';
  return o;
}
