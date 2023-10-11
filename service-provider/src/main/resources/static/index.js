(()=> {
    if ((!('BISON' in window)) || (window.BISON.version !== '0.0.9')) {
        window.location = '/extension.html';
        return;
    }
    const status = ((m) => {
        document.getElementById('status').innerText = ('' + m);
    });
    let lockout = false;
    document.getElementById('authn').addEventListener('click', async () => {
        if (lockout) return;
        lockout = true;
        try {
            status('Requesting authn metadata...');
            const response = await fetch('/auth');
            const info = await response.json();
            if (!response.ok) {
                throw info.message;
            }
            console.log('authn request from server',info);
            status('Requesting authentication...');
            await window.BISON.pleaseAuthenticate(info);
        } catch (e) {
            console.error(e);
            status('FAIL: ' + e);
        } finally { lockout = false; }
    })
})();
