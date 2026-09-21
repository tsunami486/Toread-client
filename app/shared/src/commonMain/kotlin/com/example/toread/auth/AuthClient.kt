package com.example.toread.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AuthClient(
    private val baseUrlProvider: () -> String,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            // Connect and read are split on purpose: on a weak link the connect (DNS + TCP + TLS) is
            // what stalls, and failing that in 8s lets the caller retry while the link is still warm,
            // while a slow-but-alive response gets the full 20s to arrive.
            connectTimeoutMillis = AuthConnectTimeoutMillis
            requestTimeoutMillis = AuthHttpTimeoutMillis
            socketTimeoutMillis = AuthHttpTimeoutMillis
        }
        install(ContentNegotiation) {
            json(AuthHttpJson)
        }
    },
) {
    suspend fun register(email: String, password: String): AuthClientResult =
        authenticate(
            path = "/auth/register",
            body = RegisterRequest(email = email.trim(), password = password),
            timeoutMillis = AuthHttpTimeoutMillis,
        )

    suspend fun login(email: String, password: String): AuthClientResult =
        authenticate(
            path = "/auth/login",
            body = LoginRequest(email = email.trim(), password = password),
            timeoutMillis = AuthHttpTimeoutMillis,
        )

    /**
     * Refreshing consumes a single-use refresh token on the server, so a timeout is ambiguous rather
     * than failed: the server may have rotated the token without the response reaching this client.
     * A longer dedicated timeout keeps that window small.
     */
    suspend fun refresh(refreshToken: String): AuthClientResult =
        authenticate(
            path = "/auth/refresh",
            body = RefreshRequest(refreshToken = refreshToken),
            timeoutMillis = AuthRefreshHttpTimeoutMillis,
        )

    suspend fun checkServerConnection(): ServerConnectionResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return ServerConnectionResult.Failure(NetworkServiceUnavailableMessage)
        }

        return runCatching {
            val response = client.get("$baseUrl/health") {
                timeout {
                    connectTimeoutMillis = HealthCheckHttpTimeoutMillis
                    requestTimeoutMillis = HealthCheckHttpTimeoutMillis
                    socketTimeoutMillis = HealthCheckHttpTimeoutMillis
                }
            }
            if (response.status.isSuccess()) {
                ServerConnectionResult.Success
            } else {
                ServerConnectionResult.Failure(NetworkServiceUnavailableMessage)
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            ServerConnectionResult.Failure(NetworkServiceUnavailableMessage)
        }
    }

    private suspend inline fun <reified T : Any> authenticate(
        path: String,
        body: T,
        timeoutMillis: Long,
    ): AuthClientResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return AuthClientResult.Failure(message = AuthServiceUnavailableMessage)
        }

        return runCatching {
            val response = client.post("$baseUrl$path") {
                timeout {
                    connectTimeoutMillis = timeoutMillis
                    requestTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                val error = response.errorDetails()
                return AuthClientResult.Failure(
                    message = error.message,
                    error = error.error,
                    httpStatus = response.status.value,
                )
            }

            val auth = response.body<AuthResponse>()
            AuthClientResult.Success(
                AuthSession(
                    email = auth.user.email,
                    accessToken = auth.tokens.accessToken,
                    refreshToken = auth.tokens.refreshToken,
                    expiresInSeconds = auth.tokens.expiresIn,
                ),
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            // A dropped connection, a DNS stall, or a timeout is a network problem, not the service
            // being unavailable: on a weak network those are the common case, and calling them
            // "登录服务暂不可用" hid which side of the wire actually failed.
            AuthClientResult.Failure(message = NetworkServiceUnavailableMessage)
        }
    }
}

data class AuthSession(
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

sealed interface AuthClientResult {
    data class Success(val session: AuthSession) : AuthClientResult

    /**
     * @param httpStatus status of a server response, or `null` when the request never produced one
     *   (network failure, timeout, malformed body). Only a server response can confirm that a
     *   refresh token is invalid, so a `null` status must stay ambiguous for the caller.
     */
    data class Failure(
        val message: String,
        val error: String? = null,
        val httpStatus: Int? = null,
    ) : AuthClientResult
}

/**
 * `true` only when the server itself rejected the presented refresh token.
 *
 * Network failures, timeouts, malformed error bodies, and intermediary responses stay ambiguous, so
 * the caller keeps the persisted login and retries instead of clearing the session.
 */
internal fun AuthClientResult.Failure.isConfirmedInvalidRefreshToken(): Boolean =
    httpStatus == HttpStatusCode.Unauthorized.value && error == InvalidRefreshTokenError

sealed interface ServerConnectionResult {
    data object Success : ServerConnectionResult
    data class Failure(val message: String) : ServerConnectionResult
}

@Serializable
private data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name")
    val displayName: String? = null,
)

@Serializable
private data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
private data class AuthResponse(
    val user: UserResponse,
    val tokens: TokenResponse,
)

@Serializable
private data class UserResponse(
    val id: String,
    val email: String,
    @SerialName("display_name")
    val displayName: String? = null,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_expires_in")
    val refreshExpiresIn: Long,
)

@Serializable
private data class ErrorResponse(
    val error: String,
    val message: String,
)

private data class AuthErrorDetails(
    val error: String?,
    val message: String,
)

private suspend fun io.ktor.client.statement.HttpResponse.errorDetails(): AuthErrorDetails {
    val body = bodyAsText()
    val parsed = runCatching {
        AuthHttpJson.decodeFromString(ErrorResponse.serializer(), body)
    }.getOrNull()
    return AuthErrorDetails(
        error = parsed?.error,
        message = parsed.toUserMessage(),
    )
}

private fun ErrorResponse?.toUserMessage(): String =
    authErrorToUserMessage(
        error = this?.error,
        serverMessage = this?.message,
    )

internal fun authErrorToUserMessage(error: String?, serverMessage: String?): String =
    when (error) {
        "invalid_request" -> serverMessage?.takeIf { it.isNotBlank() } ?: AuthServiceUnavailableMessage
        "email_exists" -> "该邮箱已经注册"
        "registration_disabled" -> "当前服务器暂未开放新账号注册"
        "rate_limited" -> "请求过于频繁，请稍后重试"
        "invalid_credentials" -> "邮箱或密码不正确"
        "invalid_refresh_token" -> "登录状态已过期，请重新登录。"
        "unauthorized" -> "登录状态无效，请重新登录。"
        else -> AuthServiceUnavailableMessage
    }

private val AuthHttpJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Login and registration share the refresh call's read budget. They used to run on a 3-second
 * connect/read timeout, which a weak mobile network blows routinely — and every such failure surfaced
 * as "登录服务暂不可用", blaming the server for a lost packet.
 */
private const val AuthHttpTimeoutMillis = 20_000L
private const val AuthConnectTimeoutMillis = 8_000L
private const val AuthRefreshHttpTimeoutMillis = 20_000L
private const val HealthCheckHttpTimeoutMillis = 8_000L
private const val InvalidRefreshTokenError = "invalid_refresh_token"
private const val NetworkServiceUnavailableMessage = "网络服务暂不可用，请稍后重试。"
private const val AuthServiceUnavailableMessage = "登录服务暂不可用，请稍后重试。"
