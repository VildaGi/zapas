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
    // Чистим meta для удалённых позиций, чтобы localStorage не пух
    var alive = {};
    st.list.forEach(function (l) { if (listMeta[l.id]) alive[l.id] = listMeta[l.id]; });
    listMeta = alive;
    saveMeta();
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
    if (!a) return false;
    var tag = a.tagName;
    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') return false;
    if (a.type === 'checkbox') return false;
    return document.getElementById('v').contains(a);
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
        if (!t) return;
        var q = f.q.value === '' ? '' : +f.q.value;
        var u = f.u.value;
        var c = f.c.value.trim();

        var before = {};
        S.list.forEach(function (l) { before[l.id] = true; });

        API.addList({ name: t }).then(function (st) {
            var added = st.list.filter(function (l) { return !before[l.id]; })[0];
            if (added) {
                listMeta[added.id] = { q: q, u: u, c: c };
                saveMeta();
            }
            applyState(st);
        }).catch(function (e) {
            toast('Ошибка: ' + e.message);
            render();
        });
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
        listMeta[id] = {
            q: f.q.value === '' ? '' : +f.q.value,
            u: f.u.value,
            c: f.c.value.trim()
        };
        saveMeta();
        act(API.patchList(id, { name: ln }), 'Сохранено', function () { edit = null; });
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
                    var m = metaOf(l);
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
            if (confirm('Удалить все данные?')) {
                listMeta = {};
                saveMeta();
                act(API.reset());
            }
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
            if (listMeta[id]) { delete listMeta[id]; saveMeta(); }
            act(API.delList(id));
            break;

        case 'retry':
            refresh();
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