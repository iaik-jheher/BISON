<#-- @ftlvariable name="uid" type="String" -->
<#-- @ftlvariable name="idp" type="String" -->
<#-- @ftlvariable name="sp" type="String" -->
<#-- @ftlvariable name="scope" type="String" -->
<#-- @ftlvariable name="version" type="String" -->
<#-- @ftlvariable name="isBison" type="Boolean" -->
<!doctype html>
<html lang="en">
<head>
    <title>Authentication succeeded</title>
    <link rel="stylesheet" href="/static/style.css">
</head>
<body class="gate-opened">
<main>
<h1>BISON Service Provider</h1>
<p>
    You are now authenticated.
    <#if isBison>
        Yippie!
    <#else>
        But you lost your privacy.
        Was it worth it?
    </#if>
</p>
<p>
    <#if isBison>
        Your BISON pseudonym is:
    <#else>
        Your OIDC pairwise pseudonym is:
    </#if>
    <span class="mono">${uid}</span>
</p>
<p>
    This is what I have verified:
    <ul>
        <#if isBison>
            <li class="happy">You used BISON pseudonym derivation.</li>
            <li class="happy">Your browser faithfully performed the algorithm.</li>
        <#else>
            <li class="sad">You used regular OpenID Connect.</li>
            <li class="sad">The identity provider now knows you are talking to me.</li>
        </#if>
        <li>You logged in at <span class="mono">${sp}</span>.</li>
        <li>Your pseudonym is valid for <span class="mono">${scope}</span>.</li>
        <li>You logged in using <span class="mono">${idp}</span>.</li>
    </ul>
</p>
<a href="/"><button id="authn">Exit the grasslands and try again.</button></a>
</main>
<div id="version-note">bison-sp ${version}</div>
<div id="source-note">Images by Yellowstone National Park staff; public domain (<a href="https://www.flickr.com/photos/yellowstonenps/" rel="noreferrer noopener nofollow" target="_blank">Flickr</a>)</div>
</body>
</html>
