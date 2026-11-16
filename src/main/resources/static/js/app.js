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

initCatalog();
render();   // экран «Загрузка…»
refresh();

// Раз в минуту обновляем данные (дни пересчитываются, срабатывают напоминания)
setInterval(function () {
    if (!isTyping()) refresh(true);
}, 60000);

// При возвращении на вкладку — сразу свежие данные
document.addEventListener('visibilitychange', function () {
    if (!document.hidden && !isTyping()) refresh(true);
});