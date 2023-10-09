import './ristretto255.min.js';

const safeget = ((obj,key,kind,nullable) => {
    if ((obj === null) || ((typeof(obj) !== 'object')))
        throw new DOMException('Expected non-null object.', 'NotSupportedError');
    const val = obj[key] ?? null;
    if ((val === null && !nullable) || ((val !== null) && (typeof(val) !== kind)))
        throw new DOMException('Expected '+(nullable?'':'non-null ')+kind+' for \''+key+'\'.', 'NotSupportedError');
    return val;
});
const safeurl = ((str) => {
    try {
        return new URL(str);
    } catch(e) {
        throw new DOMException(`Expected URL, got '${str}'.`, 'NotSupportedError');
    }
});
const b64url = ((bytes) => 
    btoa(Array.from(new Uint8Array(bytes)).map((c) => String.fromCharCode(c)).join('')).replace(/\+/g,'-').replace(/\//g,'_').replace(/\=+$/m,''));
const sha512 = (async(str) => {
    const encoded = new TextEncoder().encode(str);
    const digest = await crypto.subtle.digest('SHA-512', encoded);
    return Array.from(new Uint8Array(digest));
});

// locally-included copy of https://publicsuffix.org/list/public_suffix_list.dat
const publicSuffixList = (async () => {
    const list = (await (await fetch(browser.runtime.getURL('./public_suffix_list.dat'))).text())
                    .split('\n').map((e => e.trim())).filter((o)=>(o)).filter(o => !o.startsWith('//'));
    let rulesByTLD = {};
    for (const rule of list) {
        const isException = rule.startsWith('!');
        const hostParts = rule.substr(isException ? 1 : 0).split('.');
        const lastPart = hostParts[hostParts.length - 1];
        (rulesByTLD[lastPart] || (rulesByTLD[lastPart] = [])).push({hostParts, isException});
    }
    for (const sublist of Object.values(rulesByTLD)) {
        sublist.sort((a,b) => {
            if (a.isException !== b.isException)
                return a.isException ? -1 : 1;
            return b.hostParts.length-a.hostParts.length;
        });
    }
    return rulesByTLD;
})();

// https://url.spec.whatwg.org/#host-public-suffix
const getPublicSuffix = (async (host) => {
    if (typeof(host) !== 'string')
        return null;
    if (host.startsWith('[') || host.endsWith(']') || host.match(/[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}/))
        return null;
    const hostParts = host.split('.');
    const pslForTLD = (await publicSuffixList)[hostParts[hostParts.length-1]];
    // https://github.com/publicsuffix/list/wiki/Format#formal-algorithm
    if (!pslForTLD)
        return hostParts[hostParts.length-1];

out:for (const {hostParts: pslHostParts, isException} of pslForTLD) {
        if (hostParts.length < pslHostParts.length)
            continue;

        for (let i=1; i<=pslHostParts.length; ++i) {
            if (pslHostParts[pslHostParts.length-i] === '*')
                continue; // keep matching
            if (hostParts[hostParts.length-i] !== pslHostParts[pslHostParts.length-i])
                continue out; // match failed
        }
        
        return hostParts.slice(hostParts.length-(pslHostParts.length-(isException?1:0))).join('.');
    }
    
    return hostParts[hostParts.length-1];
});

// https://html.spec.whatwg.org/multipage/browsers.html#is-a-registrable-domain-suffix-of-or-is-equal-to
const isRegistrableDomainSuffixOrIsEqual = (async (hostSuffixString, originalHost) => {
    if ((typeof(hostSuffixString) !== 'string') || (typeof (originalHost) !== 'string'))
        return false;
    if (hostSuffixString === '')
        return false;
    if (hostSuffixString === originalHost)
        return true;
    if (hostSuffixString.startsWith('[') || hostSuffixString.endsWith(']') || hostSuffixString.match(/^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$/))
        return false;
    if (!originalHost.endsWith('.'+hostSuffixString))
        return false;
    const hostPublicSuffix = await getPublicSuffix(hostSuffixString);
    if (!hostPublicSuffix || (hostPublicSuffix === hostSuffixString))
        return false;
    const originalPublicSuffix = await getPublicSuffix(originalHost);
    if (!originalPublicSuffix || originalPublicSuffix.endsWith('.'+hostPublicSuffix))
        return false;
    if (!hostSuffixString.endsWith('.'+originalPublicSuffix))
        return false;
    return true;
});

window.getPublicSuffix = getPublicSuffix;
window.isRegistrableDomainSuffixOrIsEqual = isRegistrableDomainSuffixOrIsEqual;
    
const authnPage = browser.runtime.getURL('./authn.html');
const returnPage = browser.runtime.getURL('./return.html');

// non-async cache to ensure operation atomicity
const _stateCache = {}

const resolveStateCache = ((key) => {
    const v = _stateCache[key];
    if (v !== undefined)
        return Promise.resolve(v);
    return ((_stateCache[key] = browser.storage.session.get(key).then(o => {
        return ((_stateCache[key] = (o[key] ?? null)));
    })));
});

const putState = (async (data) => {
    while (true) {
        const randomUUID = crypto.randomUUID();
        const info = _stateCache[randomUUID];
        if (info === undefined)
            await resolveStateCache(randomUUID);
        else if (info && info.then)
            await info;
        if (_stateCache[randomUUID] !== null)
            continue;
        _stateCache[randomUUID] = data;
        /* do not await */ browser.storage.session.set({[randomUUID]: data});
        return randomUUID;
    }
});

const popState = (async (id) => {
    const cachedPromise = _stateCache[id];
    if (cachedPromise === undefined)
        await resolveStateCache(id);
    else if (cachedPromise && cachedPromise.then)
        await cachedPromise;
    const state = _stateCache[id];
    _stateCache[id] = null;
    /* do not await */ browser.storage.session.remove(id);
    return state;
});

const handleRequest = (async function(message, sender) {
    const idpURL = safeget(message,'authorizationEndpointURI','string');
    const scopeId = safeget(message,'scopeId','string');
    const nonce = safeget(message,'nonce','string');
    const state = safeget(message,'state','string');
    const returnURL = safeget(message,'redirectURI','string');
    
    const currentTabURL = safeurl(sender.url);
    const effectiveDomain = currentTabURL.hostname;
    if (!(await isRegistrableDomainSuffixOrIsEqual(scopeId, effectiveDomain)))
        throw new DOMException(`Value for scopeId is invalid, expected registerable superdomain of '${effectiveDomain}'.`, 'SecurityError');
    
    if (sender.frameId !== 0)
        throw new DOMException('Not permitted from frames.', 'SecurityError');
    
    const returnURLParsed = safeurl(returnURL);
    if (returnURLParsed.host !== currentTabURL.host)
        throw new DOMException('Invalid return URL, must be same host.');
    
    const authnURL = safeurl(idpURL);
    authnURL.searchParams.set('scope', 'openid');
    authnURL.searchParams.set('response_mode', 'query');
    authnURL.searchParams.set('response_type', 'id_token');
    authnURL.searchParams.set('redirect_uri', returnPage)
    authnURL.searchParams.set('pairwise_subject_type', 'bison');
    
    const scope = await sha512("HashToGroup"+scopeId);
    const encodedScope = ristretto255.fromHash(scope);
    const blind = ristretto255.scalar.getRandom();
    const blindedScope = ristretto255.scalarMult(blind, encodedScope);
    const newNonce = b64url(await sha512("BISON:"+currentTabURL.origin+":"+nonce));
    
    const ourState = await putState({
        returnURL,
        theirState: state,
        blind: b64url(blind)
    });
    authnURL.searchParams.set('client_id',b64url(blindedScope));
    authnURL.searchParams.set('state', ourState);
    authnURL.searchParams.set('nonce', newNonce);
    
    const newTab = await browser.tabs.update(sender.tab.id, {url: authnPage});
    
    let fn;
    fn = ((id,changeInfo,tab) => {
        if (tab.url && (tab.url !== authnPage)) {
            browser.tabs.onUpdated.removeListener(fn);
            return;
        }
        
        if (tab.status !== 'complete')
            return;
        
        browser.tabs.sendMessage(id, { authnURL: authnURL.toString(), scopeId, spOrigin: currentTabURL.host });
        browser.tabs.onUpdated.removeListener(fn);
    });
    browser.tabs.onUpdated.addListener(fn, { tabId: newTab.id });
});

const handleResponse = (async function(message, sender) {
    if (sender.id !== browser.runtime.id)
        return { success: false, message: 'Illegal access' };

    const responseURL = safeurl(sender.url);
    
    {
        const dummyURL = new URL(responseURL);
        dummyURL.search = '';
        if (dummyURL.toString() !== returnPage)
            return { success: false, message: 'Illegal access' };
    }
    
    const ourState = responseURL.searchParams.get('state');
    const state = await popState(ourState);
    if (!state)
        return { success: false, message: 'Unexpected state; did you refresh the page/use the back button?' };
    
    const { returnURL, theirState, blind } = state;
    
    const error = responseURL.searchParams.get('error');
    if (error) {
        let params = { 'error': error, 'state': theirState };
        
        const errorDesc = responseURL.searchParams.get('error_description');
        if (errorDesc) params['error_description'] = errorDesc;
        const errorUri = responseURL.searchParams.get('error_uri');
        if (errorUri) params['error_uri'] = errorUri;
        return { success: true, returnURL, params };
    }
    
    const idToken = responseURL.searchParams.get('id_token');
    if (!idToken) {
        return { success: true, returnURL, params: { 'error': 'server_error', 'error_description': 'No id_token received from server', 'state': theirState } }
    }
    
    return { success: true, returnURL, params: { 'id_token': idToken, 'state': theirState, 'blind': blind } };
});

browser.runtime.onMessage.addListener(async function(message, sender) {
    switch (message.type) {
        case 'request': return handleRequest(message.message, sender);
        case 'response': return handleResponse(message.message, sender);
        default: throw new DOMException('Unexpected error.');
    }
});
