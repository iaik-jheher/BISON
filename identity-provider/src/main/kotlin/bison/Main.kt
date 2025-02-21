package bison

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.ResponseMode
import com.nimbusds.oauth2.sdk.ResponseType
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.openid.connect.sdk.AuthenticationRequest
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse
import com.nimbusds.openid.connect.sdk.SubjectType
import com.nimbusds.openid.connect.sdk.federation.registration.ClientRegistrationType
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import ec.BISON
import freemarker.cache.ClassTemplateLoader
import freemarker.core.HTMLOutputFormat
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.lang.invoke.MethodHandles
import java.net.URI
import java.security.MessageDigest
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val privateKey = ECKeyGenerator(Curve.P_256).keyID("ephemeralIDPKeypair").generate()!!
val publicKey = privateKey.toPublicJWK()!!

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
fun Application.module() {
    log.info("BISON Identity Provider starting up [$version]")
    configureRouting()
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
        outputFormat = HTMLOutputFormat.INSTANCE
    }
    install(CachingHeaders) {
        options { _, content ->
            when (content.contentType?.withoutParameters()) {
                ContentType.Image.JPEG -> io.ktor.http.content.CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 7 * 24 * 60 * 60))
                ContentType.Text.CSS -> io.ktor.http.content.CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 7 * 24 * 60 * 60))
                else -> null
            }
        }
    }
}

lateinit var baseURI: URI
val oidcMetadata: OIDCProviderMetadata by lazy {
    OIDCProviderMetadata(Issuer(baseURI), listOf(SubjectType.PAIRWISE),
        listOf(ClientRegistrationType.AUTOMATIC), null, null, JWKSet(publicKey))
        .also {
            it.authorizationEndpointURI = baseURI.resolve("/login")
            it.responseTypes = listOf(ResponseType.IDTOKEN)
            it.responseModes = listOf(ResponseMode.QUERY, ResponseMode.FORM_POST)
            it.setCustomParameter("pairwise_subject_types", listOf("bison"))
        }
}

val localUserSecrets = mapOf(
    "Alice" to "0ea26b3a-151c-47e3-88a6-b2320af0fc1e".encodeToByteArray(),
    "Bob" to "acd491f8-e05c-41d5-be29-af074d0e3875".encodeToByteArray(),
    "Charlie" to "ed255467-35b2-4a5d-a6b8-791be77c07ba".encodeToByteArray())

val version: String by lazy {
    MethodHandles.lookup().lookupClass().getResource("VERSION")?.readText()?.let {
        System.getenv("LASTDEPLOYED")?.let { deploy -> "$it deployed $deploy" } ?: it
    }
        ?: "local"
}

val cache: MutableMap<String, AuthenticationRequest> = mutableMapOf()
fun pickCacheKey(): String {
    while (true) {
        val key = UUID.randomUUID().toString()
        if (!cache.containsKey(key))
            return key
    }
}

suspend fun ApplicationCall.respondWithError(message: String) =
    this.respond(FreeMarkerContent("error.ftl", mapOf("message" to message)))

@OptIn(ExperimentalEncodingApi::class)
private object Base64Url {
    fun encode(v: ByteArray) =
        Base64.UrlSafe.encode(v).trimEnd('=')
    fun decode(v: String) =
        Base64.UrlSafe.decode(v)
}

val URI.origin : String get() = "${this.scheme}://${this.authority}"

fun Application.configureRouting() {
    baseURI = URI(environment.config.property("ourBaseURI").getString())
    routing {
        staticResources("/", "base", index="index.html")
        staticResources("/static", "static")
        get("/.well-known/openid-configuration") {
            call.respondText(oidcMetadata.toJSONObject().toJSONString(),
                status = HttpStatusCode.OK, contentType=ContentType.Application.Json)
        }
        route("login") {
            get {
                val oidcRequest = AuthenticationRequest.parse(call.request.queryString())
                require(oidcRequest.responseType == ResponseType.IDTOKEN)

                val key = pickCacheKey()
                cache[key] = oidcRequest
                call.respond(FreeMarkerContent("choose-identity.ftl",
                    mapOf(
                        "cacheKey" to key,
                        "parameters" to call.parameters.toMap(),
                        "version" to version,
                        "isBison" to (oidcRequest.getCustomParameter("pairwise_subject_type") == listOf("bison"))
                    )))
            }
            post {
                val callParams = call.receiveParameters()
                val oidcRequest = cache.remove(callParams["info"])
                if (oidcRequest == null) {
                    call.respondWithError("Unexpected request; did you click \"Back\"?")
                    return@post
                }

                val localUser = localUserSecrets[callParams["who"]]
                if (localUser == null) {
                    call.respondWithError("Unknown user")
                    return@post
                }

                val claims = JWTClaimsSet.Builder()
                    .issuer(baseURI.toString())
                    .issueTime(Date())
                    .expirationTime(Date(Date().time + 60 * 1000))
                    .claim("nonce", oidcRequest.nonce.value)
                    .apply {
                        if (oidcRequest.getCustomParameter("pairwise_subject_type") == listOf("bison")) {
                            val blindedScopeId = Base64Url.decode(oidcRequest.clientID.value)
                            val blindedPseudonym = BISON.BlindEvaluate(localUser, blindedScopeId)
                            claim("pairwise_subject_type", "bison")
                            subject(Base64Url.encode(blindedPseudonym))
                            audience(Base64Url.encode(blindedScopeId))
                        } else {
                            val audienceId = oidcRequest.clientID.value
                            val ppid = MessageDigest.getInstance("SHA-512").digest(
                                "PPID:${audienceId}:${localUser.decodeToString()}".toByteArray()
                            ).let(Base64Url::encode)
                            subject(ppid)
                            audience(audienceId)
                        }
                    }.build()

                val jwt = SignedJWT(
                    JWSHeader.Builder(JWSAlgorithm.ES256).keyID(privateKey.keyID).build(),
                    claims
                )
                jwt.sign(ECDSASigner(privateKey))

                val desiredParams = AuthenticationSuccessResponse(
                    oidcRequest.redirectionURI, null, jwt, null,
                    oidcRequest.state, null, ResponseMode.FORM_POST
                ).toParameters()

                call.respond(FreeMarkerContent("redirect.ftl", mapOf(
                    "method" to (if (oidcRequest.responseMode == ResponseMode.FORM_POST) "POST" else "GET"),
                    "redirectUri" to oidcRequest.redirectionURI.toString(),
                    "params" to desiredParams)))
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondWithError("Failed: $cause")
        }
    }
}
