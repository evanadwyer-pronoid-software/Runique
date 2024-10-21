@file:OptIn(ExperimentalFoundationApi::class)

package com.plcoding.auth.presentation.login

import androidx.compose.foundation.ExperimentalFoundationApi
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.plcoding.android_test.SessionStorageFake
import com.plcoding.android_test.TestMockEngine
import com.plcoding.android_test.loginResponseStub
import com.plcoding.auth.data.AuthRepositoryImpl
import com.plcoding.auth.data.EmailPatternValidator
import com.plcoding.auth.data.LoginRequest
import com.plcoding.auth.domain.UserDataValidator
import com.plcoding.core.data.networking.HttpClientFactory
import com.plcoding.test.MainCoroutineExtension
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class LoginViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainCoroutineExtension = MainCoroutineExtension()
    }

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var sessionStorage: SessionStorageFake
    private lateinit var mockHttpEngine: TestMockEngine

    @BeforeEach
    fun setUp() {
        sessionStorage = SessionStorageFake()

        val mockEngineConfig = MockEngineConfig().apply {
            requestHandlers.add { request ->
                val relativeUrl = request.url.encodedPath
                if (relativeUrl == "/login") {
                    respond(
                        content = ByteReadChannel(
                            text = Json.encodeToString(loginResponseStub)
                        ),
                        headers = headers {
                            set("Content-Type", "application/json")
                        }
                    )
                } else {
                    respond(
                        content = byteArrayOf(),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
        mockHttpEngine = TestMockEngine(
            dispatcher = mainCoroutineExtension.testDispatcher,
            mockEngineConfig = mockEngineConfig
        )

        val httpClient = HttpClientFactory(
            sessionStorage
        ).build(mockHttpEngine)

        authRepository = AuthRepositoryImpl(
            httpClient,
            sessionStorage
        )

        loginViewModel = LoginViewModel(
            authRepository,
            UserDataValidator(
                EmailPatternValidator
            )
        )
    }

    @Test
    fun login() = runTest {
        assertThat(loginViewModel.state.canLogin).isFalse()

        loginViewModel.state.email.edit {
            append("test@test.com")
        }
        loginViewModel.state.password.edit {
            append("Test12345")
        }

        loginViewModel.onAction(LoginAction.OnLoginClick)

        assertThat(loginViewModel.state.isLoggingIn).isFalse()
        assertThat(loginViewModel.state.email.text.toString()).isEqualTo("test@test.com")
        assertThat(loginViewModel.state.password.text.toString()).isEqualTo("Test12345")

        val loginRequest = mockHttpEngine.mockEngine.requestHistory.find {
            it.url.encodedPath == "/login"
        }
        assertThat(loginRequest).isNotNull()
        assertThat(loginRequest!!.headers.contains("x-api-key")).isTrue()

        val loginRequestBody = Json.decodeFromString<LoginRequest>(
            loginRequest.body.toByteArray().decodeToString()
        )
        assertThat(loginRequestBody.email).isEqualTo("test@test.com")
        assertThat(loginRequestBody.password).isEqualTo("Test12345")

        val session = sessionStorage.get()
        assertThat(session?.userId).isEqualTo(loginResponseStub.userId)
        assertThat(session?.accessToken).isEqualTo(loginResponseStub.accessToken)
        assertThat(session?.refreshToken).isEqualTo(loginResponseStub.refreshToken)
    }
}