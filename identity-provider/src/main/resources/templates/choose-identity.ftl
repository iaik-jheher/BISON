<#-- @ftlvariable name="isBison" type="Boolean" -->
<#-- @ftlvariable name="version" type="String" -->
<#-- @ftlvariable name="cacheKey" type="String" -->
<#-- @ftlvariable name="parameters" type="kotlin.collections.Map<kotlin.String,kotlin.collections.List<kotlin.String>>" -->
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

<p>Welcome to the identity provider!</p>

<p>
    <#if isBison>
        I've received an authentication request. I don't know who it's from. Here's the data I got:
    <#else>
        I've received an authentication request from <span style="font-family: monospace; font-weight: bold;">${parameters["client_id"][0]}</span>. I know you are talking to this website. Here's the data I got:
    </#if>
    <pre>
        <#list parameters as key, values><#list values as value>
            ${key}: ${value}
        </#list></#list>
    </pre>
    <#if !isBison>
        Want a peek of the future of authentication? Install <a href="/static/extension.xpi">the BISON extension</a>.
    </#if>
</p>


<p>For purposes of this authentication process: who are you?
<form method="POST" action="/login">
    <div id="picker">
        <#list ["Bob","Alice","Charlie"] as who>
        <input type="radio" name="who" required value="${who}" id="picker-${who}">
        <label for="picker-${who}" style="background-image: url('/static/portrait/${who}.jpg')">
            <span class="name">${who}</span>
        </label>
        </#list>
    </div>
    <input type="hidden" name="info" value="${cacheKey}">
    <input type="submit" class="submit disabled" value="Login" disabled>
    <input type="submit" class="submit enabled" value="Login">
</form>
</p>
</main>
<div id="version-note">bison-idp ${version}</div>
<div id="source-note">Images by Yellowstone National Park staff; public domain (<a href="https://www.flickr.com/photos/yellowstonenps/" rel="noreferrer noopener nofollow" target="_blank">Flickr</a>)</div>
</body>
</html>
