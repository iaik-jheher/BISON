<#-- @ftlvariable name="method" type="String" -->
<#-- @ftlvariable name="redirectUri" type="String" -->
<#-- @ftlvariable name="params" type="kotlin.collections.Map<kotlin.String,kotlin.collections.List<kotlin.String>>" -->

<html lang="en">
    <head>
        <title>Authentication Successful</title>
        <link rel="stylesheet" href="/static/style.css">
    </head>
    <body>
        <h1>BISON Identity Provider</h1>
        <main><p>
            You should be redirected automatically. If you are not, click this button:
            <!-- TODO i would really like to use POST
             but getting the information back into the addon seems impossible if you POST to moz-extension:// ... -->
            <form method="${method}" action="${redirectUri}">
                <#list params as key, values>
                    <#list values as value>
                        <input type="hidden" name="${key}" value="${value}">
                    </#list>
                </#list>
                <input type="submit" class="submit" value="Authenticate me">
            </form>
        </main>
        <div id="source-note">Images by Yellowstone National Park staff; public domain (<a href="https://www.flickr.com/photos/yellowstonenps/" rel="noreferrer noopener nofollow" target="_blank">Flickr</a>)</div>
    </body>
    <script type="text/javascript">document.addEventListener('DOMContentLoaded',()=>{document.forms[0].submit();document.querySelector('input[type="submit"]').disabled=true;})</script>
</html>
