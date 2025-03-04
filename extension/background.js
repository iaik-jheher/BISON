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
    const list = (await (await fetch(browser.runtime.getURL('public_suffix_list.dat'))).text())
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
    
const authnPage = browser.runtime.getURL('authn.html');
const returnPage = browser.runtime.getURL('return.html');

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
    if (sender.id !== browser.runtime.id)
        return { success: false, message: 'Illegal access' };

    const responseURL = safeurl(sender.url);
    
    {
        const dummyURL = new URL(responseURL);
        dummyURL.search = '';
        if (dummyURL.toString() !== authnPage)
            return { success: false, message: 'Illegal access' };
    }
    
    const ourState = responseURL.searchParams.get('state');
    const state = await popState(ourState);
    if (!(state && state.idp && state.sp && state.audience))
        return { success: false, message: 'Unexpected state; did you refresh the page/use the back button?' };
    return { success: true, state };
});

const consentResolversByWindowId = {};
const handleConsent = (async function(message, sender) {
    if (sender.id !== browser.runtime.id)
        return { success: false, message: 'Illegal access' };
    
    const responseURL = safeurl(sender.url);
    
    {
        const dummyURL = new URL(responseURL);
        dummyURL.search = '';
        if (dummyURL.toString() !== authnPage)
            return { success: false, message: 'Illegal access' };
    }
    
    consentResolversByWindowId[sender.tab?.windowId]?.(true);
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
    
    const { idToken, error, returnURL, theirState, blind } = state;
    
    if (error) {
        let params = { 'error': error, 'state': theirState };
        
        const errorDesc = state.error_description;
        if (errorDesc) params['error_description'] = errorDesc;
        const errorUri = state.error_uri;
        if (errorUri) params['error_uri'] = errorUri;
        return { success: true, returnURL, params };
    }
    
    if (!idToken) {
        return { success: true, returnURL, params: { 'error': 'server_error', 'error_description': 'No id_token received from server', 'state': theirState } }
    }
    
    return { success: true, returnURL, params: { 'id_token': idToken, 'state': theirState, 'blind': blind } };
});

browser.runtime.onMessage.addListener(async function(message, sender) {
    switch (message.type) {
        case 'request': return handleRequest(message.message, sender);
        case 'consent': return handleConsent(message.message, sender);
        case 'response': return handleResponse(message.message, sender);
        default: throw new DOMException('Unexpected error.');
    }
});

const consentPopup = (async (spOrigin, idpOrigin, audienceId) => {
    const state = await putState({sp: spOrigin, idp: idpOrigin, audience: audienceId});
    const window = await browser.windows.create({
        type: 'detached_panel',
        url: `authn.html?state=${state}`,
        width: 1000,
        height: 600,
    });
    return new Promise((r) => { consentResolversByWindowId[window.id] = r; });
});
browser.windows.onRemoved.addListener(function(id) {
    consentResolversByWindowId[id]?.(false);
});

browser.webRequest.onBeforeRequest.addListener(async function(requestDetails) {
    try {
        if (!(requestDetails.originUrl && requestDetails.url)) return;
        const origin = safeurl(requestDetails.originUrl);
        const target = safeurl(requestDetails.url);
        if (target.searchParams.get('scope')?.includes('openid')) {
            console.log(`OIDC request ${origin.origin} -> ${target.origin}`);
            if (target.searchParams.get('pairwise_subject_type')) { /* already processed */ return; }
            if (target.searchParams.get('response_type') !== 'id_token') { console.log('not implicit flow'); return; }
            if (target.searchParams.get('response_mode') !== 'form_post') { console.log('not form_post (insecure)'); return; }
            if (target.searchParams.get('pairwise_subject_types') !== 'bison') { console.log('bison unsupported'); return; }
            
            const theirState = target.searchParams.get('state');
            const audienceId = target.searchParams.get('audience_id') ?? safeurl(target.searchParams.get('client_id')).hostname;
            const redirectUri = target.searchParams.get('redirect_uri');
            const theirNonce = target.searchParams.get('nonce');
            if (!(theirState && audienceId && redirectUri && theirNonce)) { console.log('incomplete information '); return; }
            if (!(await isRegistrableDomainSuffixOrIsEqual(audienceId, origin.hostname))) { console.log(`invalid audience ID (expected registrable superdomain of '${origin.hostname}'.`); return; }
            if (safeurl(redirectUri).origin !== origin.origin) { console.log('invalid return uri'); return; }
            
            const shouldProceed = await consentPopup(origin.host, target.host, audienceId);
            if (!shouldProceed) {
                const newState = await putState({returnURL: redirectUri, theirState, error: 'access_denied', error_description: 'Process cancelled by user'});
                return {redirectUrl: returnPage.toString()+`?state=${newState}`};
            }
            
            const audience = await sha512('HashToGroup'+audienceId);
            const encodedScope = ristretto255.fromHash(audience);
            const blind = ristretto255.scalar.getRandom();
            const blindedScope = ristretto255.scalarMult(blind, encodedScope);
            const newNonce = b64url(await sha512('BISON:'+origin.origin+':'+theirNonce));
            
            const ourState = await putState({
                returnURL: redirectUri,
                theirState,
                blind: b64url(blind)
            });
            
            target.searchParams.delete('audience_id');
            target.searchParams.delete('pairwise_subject_types');
            target.searchParams.set('client_id',b64url(blindedScope));
            target.searchParams.set('pairwise_subject_type','bison'),
            target.searchParams.set('nonce', newNonce);
            target.searchParams.set('redirect_uri', 'https://anonymous.invalid/bison');
            target.searchParams.set('state', ourState);
            
            return {redirectUrl: target.toString()};
        }
    } catch (e) { console.error(e); return {cancel:true}; }
}, { 'urls': ['https://bison.grazing.website/*','https://choose.from.bison.pics/*','http://localhost/*'], 'types': ['main_frame'] }, ['blocking']);

browser.webRequest.onBeforeRequest.addListener(async function(requestDetails) {
    try {
        const ourState = requestDetails.requestBody.formData['state'][0];
        const responseInfo = await popState(ourState);
        if (!responseInfo) throw 'Invalid state?';
        if (responseInfo.idToken || responseInfo.error) throw 'Duplicate ID token?';
        responseInfo.idToken = requestDetails.requestBody.formData['id_token']?.[0];
        responseInfo.error = requestDetails.requestBody.formData['error']?.[0]
        const newState = await putState(responseInfo);
        return {redirectUrl: returnPage.toString()+`?state=${newState}`};
    } catch (e) { console.error(e); return {cancel:true}; }
}, { 'urls': ['https://anonymous.invalid/bison'], 'types': ['main_frame'] }, ['blocking', 'requestBody']);
