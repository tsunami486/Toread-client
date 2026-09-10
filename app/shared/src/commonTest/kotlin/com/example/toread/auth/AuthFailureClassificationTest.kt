package com.example.toread.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthFailureClassificationTest {
    @Test
    fun serverRejectionOfThePresentedTokenIsConfirmed() {
        assertTrue(
            failure(error = InvalidRefreshTokenError, httpStatus = 401)
                .isConfirmedInvalidRefreshToken(),
        )
    }

    @Test
    fun ambiguousOutcomesNeverClearThePersistedLogin() {
        // Timeout, dropped response, or malformed body: the request never produced a status.
        assertFalse(failure(error = null, httpStatus = null).isConfirmedInvalidRefreshToken())
        assertFalse(
            failure(error = InvalidRefreshTokenError, httpStatus = null)
                .isConfirmedInvalidRefreshToken(),
        )
        // A message that merely reads like an expiry must not be trusted without a server verdict.
        assertFalse(
            AuthClientResult.Failure(
                message = "refresh token 无效或已过期",
                error = null,
                httpStatus = null,
            ).isConfirmedInvalidRefreshToken(),
        )
        // Intermediary, rate-limit, and server-error responses are not refresh-token verdicts.
        assertFalse(failure(error = null, httpStatus = 401).isConfirmedInvalidRefreshToken())
        assertFalse(failure(error = "unauthorized", httpStatus = 401).isConfirmedInvalidRefreshToken())
        assertFalse(failure(error = "rate_limited", httpStatus = 429).isConfirmedInvalidRefreshToken())
        assertFalse(
            failure(error = InvalidRefreshTokenError, httpStatus = 500)
                .isConfirmedInvalidRefreshToken(),
        )
    }

    private fun failure(error: String?, httpStatus: Int?): AuthClientResult.Failure =
        AuthClientResult.Failure(
            message = "登录服务暂不可用，请稍后重试。",
            error = error,
            httpStatus = httpStatus,
        )

    private companion object {
        const val InvalidRefreshTokenError = "invalid_refresh_token"
    }
}
