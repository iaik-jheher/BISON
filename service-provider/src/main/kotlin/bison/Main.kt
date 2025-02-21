package bison

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.ResponseMode
import com.nimbusds.oauth2.sdk.ResponseType
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.AuthenticationRequest
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser
import com.nimbusds.openid.connect.sdk.Nonce
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator
import ec.BISON
import freemarker.cache.ClassTemplateLoader
import freemarker.core.HTMLOutputFormat
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import net.minidev.json.JSONObject
import java.lang.invoke.MethodHandles
import java.net.URI
import java.security.MessageDigest
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
fun Application.module() {
    log.info("BISON Service Provider starting up [$version]")
    configureRouting()
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
        outputFormat = HTMLOutputFormat.INSTANCE
    }
    install(CachingHeaders) {
        options { _, content ->
            when (content.contentType?.withoutParameters()) {
                ContentType.Image.JPEG -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 7 * 24 * 60 * 60))
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 7 * 24 * 60 * 60))
                else -> null
            }
        }
    }
}

lateinit var issuerBase: Issuer
val issuer: OIDCProviderMetadata by lazy {
    val info = try {
        OIDCProviderMetadata.resolve(issuerBase)
    } catch(e: Throwable) {
        throw IllegalStateException("Failed to query OIDC metadata - $e")
    }
    require(info.getCustomParameter("pairwise_subject_types")?.let { (it as List<*>).contains("bison") } ?: false)
    println("Successfully got OIDC metadata for $issuerBase")
    return@lazy info
}

val version: String by lazy {
    MethodHandles.lookup().lookupClass().getResource("VERSION")?.readText()?.let {
        System.getenv("LASTDEPLOYED")?.let { deploy -> "$it deployed $deploy" } ?: it
    }
    ?: "local"
}

data class Info(val nonce: Nonce)

val cache: MutableMap<State, Info> = mutableMapOf()
fun newCacheKey(info: Info): State {
    while (true) {
        val key = State()
        cache.putIfAbsent(key, info) ?: return key
    }
}

suspend fun ApplicationCall.respondWithError(message: String) =
    this.respond(
        status = HttpStatusCode.BadRequest,
        message = FreeMarkerContent("error.ftl", mapOf("message" to message))
    )

@OptIn(ExperimentalEncodingApi::class)
private object Base64Url {
    fun encode(v: ByteArray) =
        Base64.UrlSafe.encode(v).trimEnd('=')
    fun decode(v: String) =
        Base64.UrlSafe.decode(v)
}

val URI.origin : String get() = "${this.scheme}://${this.authority}"

fun Application.configureRouting() {
    val ourBaseURI = URI(environment.config.property("ourBaseURI").getString())
    val ourScopeId = environment.config.property("ourScopeId").getString()
    issuerBase = Issuer(environment.config.property("issuerBaseURI").getString())
    try { issuer } catch(e:Throwable) {}
    routing {
        route("/") {
            get {
                call.respond(FreeMarkerContent("index.ftl", mapOf("version" to version)))
            }
        }
        staticResources("/", "base")
        staticResources("/static", "static")
        route("auth") {
            get {
                val nonce = Nonce()
                val state = newCacheKey(Info(nonce))

                val request = AuthenticationRequest.Builder(
                    ResponseType.IDTOKEN,
                    Scope("openid"),
                    ClientID(ourScopeId),
                    ourBaseURI.resolve("/authenticated")
                ).apply {
                    endpointURI(issuer.authorizationEndpointURI)
                    state(state)
                    nonce(nonce)
                    responseMode(ResponseMode.FORM_POST)
                    customParameter("audience_id", ourScopeId)
                    customParameter("pairwise_subject_types", "bison")
                }.build()

                cache[state] = Info(nonce = nonce)

                call.respondText(
                    JSONObject(
                        mapOf("authnUri" to request.toURI().toString()),
                    ).toJSONString(),
                    status = HttpStatusCode.OK, contentType = ContentType.Application.Json
                )
            }
        }
        route("authenticated") {
            post {
                val responseParams = call.receiveParameters().toMap()
                val response =
                    AuthenticationResponseParser.parse(URI(call.request.uri), responseParams)
                val state = cache.remove(response.state)
                if (state == null) {
                    call.respondWithError("Unexpected response; did you use the \"Back\" button?")
                    return@post
                }

                if (!response.indicatesSuccess()) {
                    call.respondWithError("Authentication failed (${response.toErrorResponse().errorObject.description ?: "<null>"})")
                    return@post
                }
                val successResponse = response.toSuccessResponse()

                val claimedBlind = responseParams["blind"]?.firstOrNull()?.let { Base64Url.decode(it) }
                if (claimedBlind != null) {
                    // BISON-OIDC
                    val expectedClientIDForClaimedBlind = ClientID(
                        Base64Url.encode(
                            BISON.Blind(ourScopeId.toByteArray(), claimedBlind)
                        )
                    )

                    val validator = IDTokenValidator(
                        issuer.issuer, expectedClientIDForClaimedBlind,
                        JWSAlgorithm.ES256, issuer.jwkSet
                    )

                    val expectedNonce = Base64Url.encode(
                        MessageDigest.getInstance("SHA-512").digest(
                            "BISON:${ourBaseURI.origin}:${state.nonce}".toByteArray()))

                    val claims = validator.validate(successResponse.idToken, Nonce(expectedNonce))

                    if (claims.getStringClaim("pairwise_subject_type") != "bison") {
                        call.respondWithError("Not a BISON credential")
                        return@post
                    }

                    val userIdentifier = Base64Url.encode(
                        BISON.Finalize(
                            ourScopeId.toByteArray(), claimedBlind, Base64Url.decode(claims.subject.value)
                        )
                    )

                    call.respond(FreeMarkerContent("authenticated.ftl",
                        mapOf("idp" to URI(issuer.issuer.value),
                            "sp" to ourBaseURI.origin,
                            "scope" to ourScopeId,
                            "uid" to userIdentifier,
                            "version" to version,
                            "isBison" to true)))
                } else {
                    val validator = IDTokenValidator(
                        issuer.issuer, ClientID(ourScopeId),
                        JWSAlgorithm.ES256, issuer.jwkSet
                    )
                    val claims = validator.validate(successResponse.idToken, state.nonce)
                    val userIdentifier = claims.subject.value
                    call.respond(FreeMarkerContent("authenticated.ftl",
                        mapOf("idp" to URI(issuer.issuer.value),
                            "sp" to ourBaseURI.origin,
                            "scope" to ourScopeId,
                            "uid" to userIdentifier,
                            "version" to version,
                            "isBison" to false)))
                }
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (call.request.path() != "/auth") {
                call.respondWithError("Failed: $cause")
                return@exception
            }
            call.respondText(
                JSONObject(mapOf("message" to "$cause")).toJSONString(),
                status = HttpStatusCode.InternalServerError, contentType = ContentType.Application.Json
            )
        }
    }
}
