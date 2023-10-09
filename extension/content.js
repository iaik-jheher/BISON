if (window.isSecureContext) {
    window.wrappedJSObject.BISON = cloneInto({
        version: browser.runtime.getManifest().version,
        pleaseAuthenticate: ((obj) => {
            return new window.Promise((res,rej) => {
                browser.runtime.sendMessage({type: 'request', message: obj}).then(
                    (r => res(cloneInto(r,window))),
                    (e => rej(cloneInto(e,window))));
            });
        }),
    }, window, { cloneFunctions: true });
} else {
    window.wrappedJSObject.BISON = cloneInto({
        pleaseAuthenticate: (() => {
            return new window.Promise((res,rej) => { rej(new window.DOMException('Only available in a secure context', 'SecurityException')); });
        }),
    }, window, { cloneFunctions: true });
}
