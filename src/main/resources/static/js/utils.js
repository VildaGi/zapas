// Утилиты: экранирование, склонение, тост, шаринг
function esc(x) {
    return String(x).replace(/[&<>"]/g, function (c) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
}

function plural(n) {
    var a = Math.abs(n) % 100, b = a % 10;
    return a > 10 && a < 20 ? 'дней' : b == 1 ? 'день' : b > 1 && b < 5 ? 'дня' : 'дней';
}

function toast(m) {
    var e = document.createElement('div');
    e.className = 'toast';
    e.textContent = m;
    document.body.appendChild(e);
    setTimeout(function () { e.remove(); }, 2200);
}

function share(text) {
    if (navigator.share) {
        navigator.share({ text: text }).catch(function () {});
    } else {
        try {
            navigator.clipboard.writeText(text);
            toast('Скопировано — отправьте в мессенджер');
        } catch (e) {
            toast(text);
        }
    }
}