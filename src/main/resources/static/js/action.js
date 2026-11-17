// Системное уведомление по товарам, у которых напоминание сработало в этом запросе
function notifyFresh(names) {
    if (!names || !names.length) return;
    try {
        if ('Notification' in window && Notification.permission === 'granted') {
            new Notification('Запас: заканчивается', { body: names.join(', ') });
        }
    } catch (e) {}
}

// Вход выполнен (или сессия найдена при загрузке страницы)
function startSession(u) {
    user = u;
    authChecked = true;
    authError = '';
    legacyChecked = false;
    tab = 'stock';
    edit = null;
    S = emptyState();
    loaded = false;
    loadError = null;
    render();
    refresh();
}

// Выход или истёкшая сессия: ничего от прошлого пользователя на экране не остаётся
function endSession() {
    user = null;
    authChecked = true;
    S = emptyState();
    loaded = false;
    loadError = null;
    edit = null;
    render();
}

onUnauthorized = function () {
    if (!user) return;
    endSession();
    toast('Сессия истекла — войдите снова');
};

function applyState(st) {
    S = st;
    loaded = true;
    loadError = null;
    notifyFresh(st.fresh);
    render();
    if (!legacyChecked) {
        legacyChecked = true;
        migrateLegacyDetails();
    }
}

// Однократный перенос: раньше количество, единица и заметка позиций списка хранились в localStorage
// этого браузера. Отправляем их на сервер и удаляем ключ. Когда у всех данные уже перенесены,
// эту функцию вместе с вызовом в applyState можно удалить.
function migrateLegacyDetails() {
    var key = 'zapas-list-meta-v2:' + user.id, raw, meta;
    try { raw = localStorage.getItem(key); } catch (e) { return; }
    if (!raw) return;
    try { meta = JSON.parse(raw) || {}; } catch (e) { meta = {}; }

    var todo = S.list.filter(function (l) {
        var m = meta[l.id];
        return m && (m.q || m.c) && l.quantity == null && !l.note;
    });
    var chain = Promise.resolve();
    todo.forEach(function (l) {
        var m = meta[l.id];
        chain = chain.then(function () {
            return API.patchList(l.id, { details: {
                quantity: +m.q > 0 ? +m.q : null,
                unit: m.u || 'шт',
                note: m.c || null
            } });
        });
    });
    chain.then(function () {
        try { localStorage.removeItem(key); } catch (e) {}
        if (todo.length) return refresh(true);
    }).catch(function () { /* ключ остаётся, попробуем при следующем входе */ });
}

// Загрузка состояния. silent — не показывать тост при ошибке (фоновое обновление).
function refresh(silent) {
    if (!user) return Promise.resolve();
    return API.getState().then(applyState).catch(function (e) {
        if (e.status === 401) return;   // onUnauthorized уже показал форму входа
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
        if (e.status === 401) return;
        toast('Ошибка: ' + e.message);
        render();
    });
}

// Фоновое обновление не должно стирать то, что человек сейчас печатает
function isTyping() {
    var a = document.activeElement;
    if (!a) return false;
    var tag = a.tagName;
    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') return false;
    if (a.type === 'checkbox') return false;
    return document.getElementById('v').contains(a);
}

// Количество, единица и заметка из формы позиции списка. Пустое или нулевое количество = «не указано».
function formDetails(f) {
    var q = parseFloat(f.q.value);
    return {
        quantity: q > 0 ? q : null,
        unit: f.u.value,
        note: f.c.value.trim() || null
    };
}

// ---------- формы (data-f на <form>) ----------
function handleSubmit(e) {
    var f = e.target;
    var kind = f.dataset && f.dataset.f;
    if (!kind) return;
    e.preventDefault();
    var id = f.dataset.id;

    if (kind === 'auth') {
        if (authBusy) return;
        var em = f.elements['email'].value.trim();
        var pw = f.elements['password'].value;
        authEmail = em;
        authError = '';
        authBusy = true;
        render();
        var call = authMode === 'register' ? API.register(em, pw) : API.login(em, pw);
        call.then(function (u) {
            authBusy = false;
            authEmail = '';
            startSession(u);
        }).catch(function (err) {
            authBusy = false;
            authError = err.message;
            render();
        });
        return;
    }

    if (kind === 'add-item') {
        var n = f.n.value.trim();
        if (n) act(API.addItem({ name: n, total: +f.d.value || 30 }));
    }

    if (kind === 'add-list') {
        var t = f.n.value.trim();
        if (!t) return;
        act(API.addList({ name: t, details: formDetails(f) }));
    }

    if (kind === 'save-item') {
        var it = byId(id);
        var name = f.n.value.trim();
        if (!it || !name) return;
        act(API.patchItem(id, {
            name: name,
            total: +f.d.value || it.total
        }), 'Сохранено', function () { edit = null; });
    }

    if (kind === 'save-list') {
        var l = listById(id);
        var ln = f.n.value.trim();
        if (!l || !ln) return;
        act(API.patchList(id, { name: ln, details: formDetails(f) }), 'Сохранено', function () { edit = null; });
    }
}

// ---------- клики по data-a ----------
function handleClick(e) {
    var b = e.target.closest('[data-a]');
    if (!b) return;
    var a = b.dataset.a;
    var id = b.dataset.id;

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
            share('Нужно купить: ' + i.name +
                (d > 0 ? ' (хватит ещё на ' + d + ' ' + plural(d) + ')' : ' (закончился)'));
            break;

        case 'tick':
            act(API.patchList(id, { done: b.checked }));
            break;

        case 'sharelist':
            var text = S.list.filter(function (l) { return !l.done; })
                .map(function (l) {
                    var m = detailsOf(l);
                    var s = '• ' + l.name;
                    if (m.q) s += ' — ' + m.q + ' ' + (m.u || '');
                    if (m.c) s += ' (' + m.c + ')';
                    return s;
                }).join('\n');
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

        case 'edit':
            edit = { t: 's', id: id };
            render();
            break;
        case 'editl':
            edit = { t: 'l', id: id };
            render();
            break;
        case 'cancel':
            edit = null;
            render();
            break;

        case 'dell':
            act(API.delList(id));
            break;

        case 'retry':
            if (!authChecked) boot(); else refresh();
            break;

        case 'authmode':
            authMode = authMode === 'login' ? 'register' : 'login';
            authError = '';
            render();
            break;

        case 'logout':
            API.logout().catch(function () {}).then(endSession);
            break;
    }
}

// datalist: подстановка срока при выборе подсказки в форме добавления товара
function handleInput(e) {
    var el = e.target;
    if (!el || el.tagName !== 'INPUT' || el.name !== 'n') return;
    var f = el.form;
    if (!f || !f.dataset || f.dataset.f !== 'add-item') return;
    if (typeof suggest === 'function') suggest(f, el);
}

// чипы 7/14/30/60/90
function handleChips(e) {
    var b = e.target.closest('.chips button');
    if (!b) return;
    var f = b.form;
    if (!f || !f.d) return;
    setDays(f, +b.dataset.v);
}