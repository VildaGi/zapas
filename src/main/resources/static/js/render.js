// Отрисовка вкладок
function render() {
    document.querySelectorAll('nav button').forEach(function (b) {
        b.setAttribute('aria-selected', b.dataset.t === tab ? 'true' : 'false');
    });

    var h = document.getElementById('h');
    var s = document.getElementById('s');
    var v = document.getElementById('v');

    document.querySelector('nav').hidden = !user;
    document.getElementById('u').innerHTML = user
        ? '<span>' + esc(user.email) + '</span><button data-a="logout">Выйти</button>'
        : '';

    if (!authChecked) {
        h.textContent = 'Запас';
        s.textContent = '';
        v.innerHTML = loadError
            ? '<div class="empty">Не удалось связаться с сервером:<br>' + esc(loadError) +
            '<br><br><button class="p" data-a="retry">Повторить</button></div>'
            : '<div class="empty">Загрузка…</div>';
        return;
    }

    if (!user) {
        renderAuth(h, s, v);
        return;
    }

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

    // Форма добавления: рамка бренда, вопрос, степпер, чипы, подсказка
    o += '<form class="termbox" data-f="add-item">' +
        '<input name="n" list="cat" placeholder="Новый товар, например «Кофе»" ' +
        'required autocomplete="off">' +
        '<div class="q" style="margin-top:10px">Срок: на сколько дней хватает упаковки?</div>' +
        '<div class="stepper">' +
        '<input name="d" type="number" min="1" max="365" value="30" aria-label="Срок в днях">' +
        '<span>дней</span>' +
        '<button class="p" style="margin-left:auto">Добавить</button>' +
        '</div>' +
        '<div class="chips">' +
        [7, 14, 30, 60, 90].map(function (v) {
            return '<button type="button" data-v="' + v + '" aria-pressed="' +
                (v === 30 ? 'true' : 'false') + '">' + v + ' дн.</button>';
        }).join('') +
        '</div>' +
        '<div class="hint">Через сколько дней товар закончится — напомним заранее.</div>' +
        '</form>';

    var items = S.items.slice().sort(function (a, b) { return daysLeft(a) - daysLeft(b); });
    if (!items.length) {
        o += '<div class="empty">Добавьте первый товар и укажите, на сколько дней его хватает.</div>';
    }

    items.forEach(function (i) {
        if (edit && edit.t === 's' && edit.id === i.id) {
            o += editStockForm(i);
            return;
        }
        var d = daysLeft(i), st = status(i);
        var pct = Math.max(0, Math.min(100, d / i.total * 100));
        o += '<div class="item ' + st + '">' +
            '<div class="row"><span class="name">' + esc(i.name) + '</span>' +
            '<span class="tr"><span class="left">' +
            (d <= 0 ? 'закончился' : 'ещё ' + d + ' ' + plural(d)) +
            '</span><button class="edit" data-a="edit" data-id="' + i.id + '">✎ Изменить</button></span></div>' +
            '<div class="bar"><i style="width:' + pct + '%"></i></div>' +
            '<div class="acts">' +
            '<button class="p" data-a="bought" data-id="' + i.id + '">Купил</button>' +
            '<button data-a="share" data-id="' + i.id + '">Поделиться</button>' +
            '<button class="d" data-a="sudden" data-id="' + i.id + '">Закончилось внезапно</button>' +
            '<button data-a="del" data-id="' + i.id + '" aria-label="Удалить">✕</button>' +
            '</div></div>';
    });

    o += '<p class="note"><b>Срок</b> — на сколько дней хватает одной упаковки. ' +
        'Напоминание придёт за несколько дней до конца срока.</p>' +
        '<p class="note">«Закончилось внезапно» — отметьте, если товар кончился раньше, ' +
        'чем вы ожидали. Это главный сигнал для проверки гипотезы.</p>';
    return o;
}

function editStockForm(i) {
    return '<form class="item ef" data-f="save-item" data-id="' + i.id + '">' +
        '<input name="n" value="' + esc(i.name) + '" required aria-label="Название">' +
        '<div class="q">Срок: на сколько дней хватает</div>' +
        '<div class="stepper">' +
        '<input name="d" type="number" min="1" max="365" value="' + i.total + '">' +
        '<span>дней</span>' +
        '</div>' +
        '<div class="acts">' +
        '<button class="p">Сохранить</button>' +
        '<button type="button" data-a="cancel">Отмена</button>' +
        '</div></form>';
}

function renderList(h, s) {
    var open = S.list.filter(function (l) { return !l.done; }).length;
    h.textContent = 'Список покупок';
    s.textContent = open ? 'Осталось купить: ' + open : 'Всё куплено';
    var o = '';

    o += '<form class="ef termbox" style="border-width:1px" data-f="add-list">' +
        '<input name="n" list="cat" placeholder="Что купить?" required autocomplete="off">' +
        '<div class="r">' +
        '<input name="q" type="number" min="0" step="any" placeholder="Кол-во" style="flex:0 0 90px">' +
        '<select name="u" aria-label="Единица">' + unitOpts('') + '</select>' +
        '<input name="c" placeholder="Заметка, например «зелёные»">' +
        '</div>' +
        '<button class="p">Добавить в список</button>' +
        '</form>';

    if (!S.list.length) {
        o += '<div class="empty">Список пуст. Товары, которые заканчиваются, появятся здесь сами.</div>';
    }

    S.list.slice().sort(function (a, b) { return a.done - b.done; }).forEach(function (l) {
        if (edit && edit.t === 'l' && edit.id === l.id) {
            o += editListForm(l);
            return;
        }
        var m = metaOf(l);
        o += '<div class="li' + (l.done ? ' done' : '') + '">' +
            '<input type="checkbox" data-a="tick" data-id="' + l.id + '"' +
            (l.done ? ' checked' : '') + ' aria-label="Куплено">' +
            '<span class="t">' + esc(l.name) +
            (m.q ? ' — ' + m.q + ' ' + esc(m.u || '') : '') +
            (m.c ? '<br><span class="tag">' + esc(m.c) + '</span>' : '') +
            (l.from === 'reminder' ? '<br><span class="tag">добавлено по напоминанию</span>' : '') +
            '</span>' +
            '<button class="edit" data-a="editl" data-id="' + l.id + '">✎ Изменить</button>' +
            '</div>';
    });

    if (S.list.length) {
        o += '<div class="acts">' +
            '<button class="p" data-a="sharelist">Отправить список</button>' +
            '<button data-a="clear">Убрать купленное</button>' +
            '</div>';
    }

    o += '<p class="note">Совместный список в реальном времени — следующий шаг после ' +
        'проверки гипотезы. Пока список отправляется текстом в любой мессенджер.</p>';
    return o;
}

function editListForm(l) {
    var m = metaOf(l);
    return '<form class="item ef" data-f="save-list" data-id="' + l.id + '">' +
        '<input name="n" value="' + esc(l.name) + '" required aria-label="Название">' +
        '<div class="r">' +
        '<input name="q" type="number" min="0" step="any" value="' + (m.q || '') +
        '" placeholder="Кол-во" style="flex:0 0 90px">' +
        '<select name="u">' + unitOpts(m.u || '') + '</select>' +
        '<input name="c" value="' + esc(m.c || '') + '" placeholder="Заметка">' +
        '</div>' +
        '<div class="acts">' +
        '<button class="p">Сохранить</button>' +
        '<button type="button" data-a="cancel">Отмена</button>' +
        '<button type="button" class="d" data-a="dell" data-id="' + l.id + '">Удалить</button>' +
        '</div></form>';
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
        '</div><div class="left">Доля товаров, купленных по напоминанию, среди всех нехваток ' +
        '(заранее + внезапно).</div></div>';

    if (n) {
        o += '<div class="item"><div class="name">Что заканчивалось внезапно</div>' +
            S.log.surprise.map(function (x) {
                return '<div class="left">' + esc(x.name) + '</div>';
            }).join('') +
            '</div>';
    }

    o += '<div class="item"><div class="name">Тест на 1–2 недели</div>' +
        '<div class="left">Пользуйтесь приложением в быту. Если внезапных нехваток много, ' +
        'гипотеза подтверждается. Если после напоминаний их почти нет, решение работает.</div></div>';

    o += '<div class="acts">' +
        '<button data-a="notif">Включить уведомления</button>' +
        '<button data-a="day">Сдвинуть время на +1 день (тест)</button>' +
        '<button class="d" data-a="reset">Сбросить данные</button>' +
        '</div>';
    return o;
}

function unitOpts(sel) {
    return ['шт', 'уп.', 'кг', 'г', 'л', 'мл'].map(function (u) {
        return '<option' + (u === sel ? ' selected' : '') + '>' + u + '</option>';
    }).join('');
}

// Форма входа / регистрации
function renderAuth(h, s, v) {
    var reg = authMode === 'register';
    h.textContent = 'Запас';
    s.textContent = reg
        ? 'Создайте аккаунт — у каждого будут свои запасы и список'
        : 'Войдите, чтобы увидеть свои запасы';
    v.innerHTML = '<form class="termbox ef" data-f="auth">' +
        (authError ? '<div class="err" role="alert">' + esc(authError) + '</div>' : '') +
        '<input name="email" type="email" placeholder="Email" value="' + esc(authEmail) + '" ' +
        'required maxlength="254" autocomplete="username">' +
        '<input name="password" type="password" placeholder="Пароль" required ' +
        (reg ? 'minlength="8" ' : '') + 'maxlength="200" ' +
        'autocomplete="' + (reg ? 'new-password' : 'current-password') + '">' +
        (reg ? '<div class="hint">Не короче 8 символов.</div>' : '') +
        '<button class="p"' + (authBusy ? ' disabled' : '') + '>' +
        (reg ? 'Создать аккаунт' : 'Войти') + '</button>' +
        '</form>' +
        '<p class="note" style="text-align:center">' +
        (reg ? 'Уже есть аккаунт? ' : 'Нет аккаунта? ') +
        '<button class="edit" data-a="authmode">' + (reg ? 'Войти' : 'Зарегистрироваться') + '</button></p>';
}
