package com.example.toread.auth

import com.example.toread.platform.SecureTokenStore
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
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AuthClient(
    private val baseUrlProvider: () -> String,
    private val tokenStore: SecureTokenStore,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = AuthHttpTimeoutMillis
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
        )

    suspend fun login(email: String, password: String): AuthClientResult =
        authenticate(
            path = "/auth/login",
            body = LoginRequest(email = email.trim(), password = password),
        )

    suspend fun refresh(refreshToken: String): AuthClientResult =
        authenticate(
            path = "/auth/refresh",
            body = RefreshRequest(refreshToken = refreshToken),
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
            ServerConnectionResult.Failure(NetworkServiceUnavailableMessage)
        }
    }

    private suspend inline fun <reified T : Any> authenticate(
        path: String,
        body: T,
    ): AuthClientResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return AuthClientResult.Failure(AuthServiceUnavailableMessage)
        }

        return runCatching {
            val response = client.post("$baseUrl$path") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                val error = response.errorDetails()
                return AuthClientResult.Failure(
                    message = error.message,
                    error = error.error,
                )
            }

            val auth = response.body<AuthResponse>()
            tokenStore.saveToken(auth.tokens.accessToken)
            AuthClientResult.Success(
                AuthSession(
                    email = auth.user.email,
                    accessToken = auth.tokens.accessToken,
                    refreshToken = auth.tokens.refreshToken,
                ),
            )
        }.getOrElse { throwable ->
            AuthClientResult.Failure(AuthServiceUnavailableMessage)
        }
    }
}

data class AuthSession(
    val email: String,
    val accessToken: String,
    val refreshToken: String,
)

sealed interface AuthClientResult {
    data class Success(val session: AuthSession) : AuthClientResult
    data class Failure(
        val message: String,
        val error: String? = null,
    ) : AuthClientResult
}

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

private const val AuthHttpTimeoutMillis = 3_000L
private const val HealthCheckHttpTimeoutMillis = 8_000L
private const val NetworkServiceUnavailableMessage = "网络服务暂不可用，请稍后重试。"
private const val AuthServiceUnavailableMessage = "登录服务暂不可用，请稍后重试。"
