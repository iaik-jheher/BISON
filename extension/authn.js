document.getElementById('authn-btn').addEventListener('click', () => {
    browser.runtime.sendMessage({type: 'consent'});
    browser.windows.remove(browser.windows.WINDOW_ID_CURRENT);
});
document.getElementById('cancel-btn').addEventListener('click', () => {
    browser.windows.remove(browser.windows.WINDOW_ID_CURRENT);
});

(async () => {    
    const info = await browser.runtime.sendMessage({type: 'request'});
    if (!info.success) {
        document.body.innerText = info.message;
        return;
    }
    const {sp: spOrigin, idp: idpOrigin, audience: scopeId } = info.state;
    document.querySelectorAll('.sp').forEach((e) => { e.innerText = spOrigin; });
    document.querySelectorAll('.scope').forEach((e) => { e.innerText = scopeId; });
    document.querySelectorAll('.idp').forEach((e) => { e.innerText = idpOrigin; });
    document.querySelector('main').classList.add('has-data');
})();


browser.runtime.onMessage.addListener(async function(message, sender) {
    if (sender.id !== browser.runtime.id) return;
    
    const {scopeId, spOrigin, authnURL} = message;
    const parsedAuthnURL = new URL(authnURL);
    const idpOrigin = parsedAuthnURL.host;
    
    const cancelURL = new URL(parsedAuthnURL.searchParams.get('redirect_uri'));
    cancelURL.searchParams.set('error','access_denied');
    cancelURL.searchParams.set('error_description','Process cancelled by user');
    cancelURL.searchParams.set('state',parsedAuthnURL.searchParams.get('state'));
    
    document.querySelectorAll('.sp').forEach((e) => { e.innerText = spOrigin; });
    document.querySelectorAll('.scope').forEach((e) => { e.innerText = scopeId; });
    document.querySelectorAll('.idp').forEach((e) => { e.innerText = idpOrigin; });
    document.getElementById('authn-link').href = authnURL;
    document.getElementById('cancel-link').href = cancelURL.toString();
    
});
