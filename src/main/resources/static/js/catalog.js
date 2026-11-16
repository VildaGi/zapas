// Справочник типичных сроков. Ключ — название товара, значение — дней.
// 0 — скоропортящийся товар: подсказка есть, но срок не подставляем.
var CAT = {
    'Шампунь': 30, 'Гель для душа': 25, 'Порошок для стирки': 21,
    'Средство для посуды': 20, 'Туалетная бумага': 14, 'Зубная паста': 45,
    'Мыло': 30, 'Кондиционер для белья': 30, 'Бумажные полотенца': 14,
    'Мешки для мусора': 30, 'Молоко': 0, 'Хлеб': 0, 'Яйца': 0, 'Яблоки': 0
};

function initCatalog() {
    var dl = document.getElementById('cat');
    if (!dl) return;
    dl.innerHTML = Object.keys(CAT).map(function (k) {
        return '<option value="' + esc(k) + '">';
    }).join('');
}

// Подставляет срок из справочника и показывает подсказку под полем.
function suggest(form, input) {
    var d = CAT[input.value.trim()];
    var hint = form.querySelector('.hint');
    if (d > 0) {
        form.d.value = d;
        syncChips(form);
        if (hint) hint.textContent = 'Обычно хватает на ~' + d + ' ' + plural(d) + '. Можно изменить.';
    } else if (d === 0) {
        if (hint) hint.textContent = 'Скоропортящийся товар — срок лучше указать вручную.';
    } else {
        if (hint) hint.textContent = 'Через сколько дней товар закончится — напомним заранее.';
    }
}

// Синхронизирует подсветку чипов с текущим значением срока.
function syncChips(form) {
    var v = +form.d.value;
    form.querySelectorAll('.chips button').forEach(function (b) {
        b.setAttribute('aria-pressed', +b.dataset.v === v ? 'true' : 'false');
    });
}

function setDays(form, v) {
    form.d.value = v;
    syncChips(form);
}