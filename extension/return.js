(async () => {
    const info = await browser.runtime.sendMessage({type: 'response'});
    if (!info.success) {
        document.body.innerText = info.message;
        return;
    }
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = info.returnURL;
    for (const [k,v] of Object.entries(info.params)) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = k;
        input.value = v;
        form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
})();
