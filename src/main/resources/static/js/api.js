function apiFetch(path, opts) {
    opts = opts || {};
    opts.headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
    if (opts.body && typeof opts.body !== 'string') opts.body = JSON.stringify(opts.body);
    return fetch(API_BASE + path, opts).then(function (r) {
        if (r.status === 204) return null;
        var ct = r.headers.get('Content-Type') || '';
        var parse = ct.indexOf('json') >= 0 ? r.json() : r.text();
        return parse.then(function (data) {
            if (!r.ok) {
                var msg = (data && data.message) || (typeof data === 'string' && data) || 'HTTP ' + r.status;
                var err = new Error(msg);
                err.status = r.status;
                err.body = data;
                throw err;
            }
            return data;
        });
    });
}

// Все методы, кроме log, возвращают актуальное состояние целиком.
var API = {
    getState:  function ()         { return apiFetch('/state'); },
    addItem:   function (body)     { return apiFetch('/items', { method: 'POST', body: body }); },
    patchItem: function (id, body) { return apiFetch('/items/' + id, { method: 'PATCH', body: body }); },
    delItem:   function (id)       { return apiFetch('/items/' + id, { method: 'DELETE' }); },
    bought:    function (id)       { return apiFetch('/items/' + id + '/bought', { method: 'POST' }); },
    sudden:    function (id)       { return apiFetch('/items/' + id + '/sudden', { method: 'POST' }); },
    addList:   function (body)     { return apiFetch('/list', { method: 'POST', body: body }); },
    patchList: function (id, body) { return apiFetch('/list/' + id, { method: 'PATCH', body: body }); },
    delList:   function (id)       { return apiFetch('/list/' + id, { method: 'DELETE' }); },
    clearList: function ()         { return apiFetch('/list/clear', { method: 'POST' }); },
    log:       function (body)     { return apiFetch('/log', { method: 'POST', body: body }); },
    setOffset: function (offset)   { return apiFetch('/time', { method: 'POST', body: { offset: offset } }); },
    reset:     function ()         { return apiFetch('/reset', { method: 'POST' }); }
};