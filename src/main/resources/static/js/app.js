// Точка входа: навешиваем обработчики и загружаем состояние с сервера
document.addEventListener('click', handleClick);
document.addEventListener('submit', handleSubmit);
document.addEventListener('input', handleInput);
document.addEventListener('click', handleChips);

// клик по чипу не должен отправлять форму
document.addEventListener('click', function (e) {
    var b = e.target.closest('.chips button');
    if (b) e.preventDefault();
});

document.querySelectorAll('nav button').forEach(function (b) {
    b.onclick = function () {
        tab = b.dataset.t;
        edit = null;
        render();
    };
});

// Узнаём, есть ли действующая сессия: да → грузим данные, нет → форма входа
function boot() {
    loadError = null;
    render();   // «Загрузка…»
    API.me().then(startSession).catch(function (e) {
        if (e.status === 401) {
            authChecked = true;
            user = null;
            loadError = null;
        } else {
            loadError = e.message;
        }
        render();
    });
}

initCatalog();
boot();

// Раз в минуту обновляем данные (дни пересчитываются, срабатывают напоминания)
setInterval(function () {
    if (user && !isTyping()) refresh(true);
}, 60000);

// При возвращении на вкладку — сразу свежие данные
document.addEventListener('visibilitychange', function () {
    if (user && !document.hidden && !isTyping()) refresh(true);
});