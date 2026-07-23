package com.example.toread.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthClientErrorMessageTest {
    @Test
    fun disabledRegistrationHasASpecificSafeMessage() {
        assertEquals(
            "当前服务器暂未开放新账号注册",
            authErrorToUserMessage(
                error = "registration_disabled",
                serverMessage = "server detail must not leak through",
            ),
        )
    }

    @Test
    fun rateLimitHasASpecificSafeMessage() {
        assertEquals(
            "请求过于频繁，请稍后重试",
            authErrorToUserMessage(
                error = "rate_limited",
                serverMessage = "server detail must not leak through",
            ),
        )
    }
}
