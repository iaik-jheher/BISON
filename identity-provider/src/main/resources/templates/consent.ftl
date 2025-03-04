<#-- @ftlvariable name="who" type="String" -->
<#-- @ftlvariable name="version" type="String" -->
<#-- @ftlvariable name="cacheKey" type="String" -->
<#-- @ftlvariable name="clientId" type="String" -->
<#-- @ftlvariable name="failureMethod" type="String" -->
<#-- @ftlvariable name="failureRedirectUri" type="String" -->
<#-- @ftlvariable name="failureState" type="String" -->
<!doctype html>
<html lang="en">
<head>
    <link rel="preload" href="/static/bison.jpg" as="image" type="image/jpeg">
    <#list ["Bob","Alice","Charlie"] as who>
        <link rel="preload" href="/static/portrait/${who}.jpg" as="image" type="image/jpeg">
    </#list>
    <title>Choose your identity</title>
    <link rel="stylesheet" href="/static/style.css">
</head>
<body>
<h1>BISON Identity Provider</h1>
<main>

    <p>Do you wish to proceed, ${who}?</p>

    <p>Do you want to log in to <span style="font-family: monospace; font-weight: bold;">${clientId}</span> as "${who}"?</p>

    <form id="failure-form" method="${failureMethod}" action="${failureRedirectUri}">
        <input type="hidden" name="state" value="${failureState}">
        <input type="hidden" name="error" value="access_denied">
        <input type="hidden" name="error_description" value="Authentication process cancelled by user">
    </form>

    <form method="POST" action="/login">
        <input type="hidden" name="info" value="${cacheKey}">
        <input type="hidden" name="who" value="${who}">
        <button type="submit" id="btn-consent" name="consent" value="true">Yes, proceed with login</button>
        <button type="submit" id="btn-no-consent" form="failure-form">Cancel and return</button>
    </form>
    <p>
        ⚠️ Of course, the problem is that you've told me that you're talking to <span style="font-family: monospace; font-weight: bold;">${clientId}</span>.<br>
        ⚠️ Want a peek of the future of authentication? Install <a href="/static/extension.xpi">the BISON extension</a>.
    </p>
</main>
<div id="version-note">bison-idp ${version}</div>
<div id="source-note">Images by Yellowstone National Park staff; public domain (<a href="https://www.flickr.com/photos/yellowstonenps/" rel="noreferrer noopener nofollow" target="_blank">Flickr</a>)</div>
</body>
</html>
