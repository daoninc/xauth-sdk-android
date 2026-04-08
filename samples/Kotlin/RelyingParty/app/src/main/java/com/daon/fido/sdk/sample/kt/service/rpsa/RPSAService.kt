package com.daon.fido.sdk.sample.kt.service.rpsa

import android.content.Context
import android.os.Bundle
import com.daon.fido.sdk.sample.kt.service.rpsa.model.AuthenticatorInfo
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateAccount
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateAccountResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateAuthRequestResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateAuthenticator
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateAuthenticatorResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateRegistrationRequestResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateSession
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateSessionResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.CreateTransactionAuthRequest
import com.daon.fido.sdk.sample.kt.service.rpsa.model.Error
import com.daon.fido.sdk.sample.kt.service.rpsa.model.ListAuthenticationResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.SubmitFailedAttemptRequest
import com.daon.fido.sdk.sample.kt.service.rpsa.model.SubmitFailedAttemptResponse
import com.daon.fido.sdk.sample.kt.service.rpsa.model.ValidateTransactionAuth
import com.daon.fido.sdk.sample.kt.service.rpsa.model.ValidateTransactionAuthResponse
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.xauth.IXUAF
import com.daon.sdk.xauth.IXUAFService
import com.daon.sdk.xauth.core.ErrorFactory
import com.daon.sdk.xauth.core.Failure
import com.daon.sdk.xauth.core.Response
import com.daon.sdk.xauth.core.Success
import com.daon.sdk.xauth.model.Operation
import com.daon.sdk.xauth.model.SingleShotAuthenticationRequest
import com.daon.sdk.xauth.model.UafProtocolMessageBase
import com.daon.sdk.xauth.uaf.UafMessageUtils
import com.daon.sdk.xauth.util.ServiceParameterParser
import com.daon.sdk.xauth.util.ServiceResponseBuilder
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.util.Locale
import java.util.UUID

/** @suppress */
class RPSAService(private val context: Context, private val params: Bundle) : IXUAFService {
    // Resources
    private val serverResourceAccount = "accounts"
    private val serverResourceRegRequests = "regRequests"
    private val serverResourceAuthenticators = "authenticators"
    private val serverResourceAuthRequests = "authRequests"
    private val serverResourceTransactionAuthRequests = "transactionAuthRequests"
    private val serverResourceTransactionAuthValidation = "transactionAuthValidation"
    private val serverResourceSessions = "sessions"
    private val serverResourceListAuthenticators = "listAuthenticators"
    private val serverResourceSubmitFailedAttempts = "failedTransactionData"

    private var http = HTTP(params)
    private var cachedRegistrationRequestId: String? = null
    private var cachedRegistrationRequest: String? = null

    override suspend fun serviceRequestAccess(params: Bundle): Response {
        Logger.logVerbose("RPSAService serviceCreateAccount")
        val parameterParser = ServiceParameterParser(params)
        val username = parameterParser.username() ?: UUID.randomUUID().toString().substring(0, 15)
        val firstname = parameterParser.firstName() ?: "first name"
        val lastname = parameterParser.lastName() ?: "last name"
        val password = parameterParser.password() ?: "password"
        val registration = parameterParser.isRegistrationRequested()
        val account =
            CreateAccount(
                firstname,
                lastname,
                username,
                password,
                registration,
                Locale.getDefault().toString(),
            )
        val sessionId = parameterParser.sessionId()
        val payload = Gson().toJson(account)
        when (val httpResponse = http.post(serverResourceAccount, payload, sessionId)) {
            is HTTP.Success -> {
                return if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val createAccountResponse =
                        Gson().fromJson(httpResponse.payload, CreateAccountResponse::class.java)
                    cachedRegistrationRequest = createAccountResponse.fidoRegistrationRequest
                    cachedRegistrationRequestId = createAccountResponse.registrationRequestId
                    ServiceResponseBuilder()
                        .sessionId(createAccountResponse.sessionId)
                        .buildSuccess()
                } else {
                    ServiceResponseBuilder().withPayloadError(httpResponse.payload).buildFailure()
                }
            }

            is HTTP.Error -> {
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    override suspend fun serviceRevokeAccess(params: Bundle): Response {
        val parameterParser = ServiceParameterParser(params)
        val sessionId = parameterParser.sessionId()
        if (sessionId != null) {
            val httpResponse =
                http.deleteResource(serverResourceSessions, sessionId, false, sessionId)
            return when (httpResponse) {
                is HTTP.Success -> {
                    ServiceResponseBuilder().buildSuccess()
                }

                is HTTP.Error -> {
                    ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
                }
            }
        } else {
            return ServiceResponseBuilder().withNullSession().buildFailure()
        }
    }

    override suspend fun serviceDeleteUser(params: Bundle): Response {
        val parameterParser = ServiceParameterParser(params)
        val sessionId = parameterParser.sessionId()
        if (sessionId != null) {
            val httpResponse =
                http.deleteResource(serverResourceAccount, sessionId, true, sessionId)
            return when (httpResponse) {
                is HTTP.Success -> {
                    ServiceResponseBuilder().buildSuccess()
                }

                is HTTP.Error -> {
                    ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
                }
            }
        } else {
            return ServiceResponseBuilder().withNullSession().buildFailure()
        }
    }

    override suspend fun serviceRequestRegistration(params: Bundle): Response {
        if (cachedRegistrationRequest != null) {
            val result =
                ServiceResponseBuilder()
                    .registrationRequest(cachedRegistrationRequest!!)
                    .requestId(cachedRegistrationRequestId)
                    .buildSuccess()
            cachedRegistrationRequest = null
            cachedRegistrationRequestId = null
            return result
        } else {
            val parameterParser = ServiceParameterParser(params)
            val sessionId = parameterParser.sessionId()
            when (val httpResponse = http.get(serverResourceRegRequests, sessionId)) {
                is HTTP.Success -> {
                    if (
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                            httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                    ) {
                        Logger.logVerbose("serviceRequestRegistration success OK")
                        val regResponse =
                            Gson()
                                .fromJson(
                                    httpResponse.payload,
                                    CreateRegistrationRequestResponse::class.java,
                                )
                        return ServiceResponseBuilder()
                            .registrationRequest(regResponse.fidoRegistrationRequest)
                            .requestId(regResponse.registrationRequestId)
                            .buildSuccess()
                    } else {
                        return ServiceResponseBuilder()
                            .withPayloadError(httpResponse.payload)
                            .buildFailure()
                    }
                }

                is HTTP.Error -> {
                    Logger.logError("serviceRequestRegistration HTTP.Error")
                    return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
                }
            }
        }
    }

    override suspend fun serviceRegister(params: Bundle): Response {
        Logger.logVerbose("serviceRegister")
        val parameterParser = ServiceParameterParser(params)
        val fidoRegistrationResponse: String = parameterParser.registrationResponse()
        val registrationChallengeId = parameterParser.requestId()
        val createAuthenticator =
            CreateAuthenticator(fidoRegistrationResponse, registrationChallengeId)
        val sessionId = parameterParser.sessionId()
        val payload = Gson().toJson(createAuthenticator)
        when (val httpResponse = http.post(serverResourceAuthenticators, payload, sessionId)) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    Logger.logVerbose("serviceRegister Success OK")
                    val createAuthenticatorResponse =
                        Gson()
                            .fromJson(httpResponse.payload, CreateAuthenticatorResponse::class.java)
                    Logger.logVerbose(
                        "serviceRegister responseCode : ${createAuthenticatorResponse.fidoResponseCode.toShort()}"
                    )
                    return ServiceResponseBuilder()
                        .registrationConfirmation(
                            createAuthenticatorResponse.fidoRegistrationConfirmation
                        )
                        .responseCode(createAuthenticatorResponse.fidoResponseCode.toShort())
                        .responseMessage(createAuthenticatorResponse.fidoResponseMsg)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logVerbose("serviceRegister HTTP.Error")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    override suspend fun serviceRequestAuthentication(params: Bundle): Response {
        Logger.logVerbose("serviceRequestAuthentication")
        val parameterParser = ServiceParameterParser(params)
        val sessionId = parameterParser.sessionId()
        val transactionId = parameterParser.transactionId()
        val username = parameterParser.username()
        val singleshot = parameterParser.isSingleShot()
        if (transactionId != null) {
            // Push
            return getAuthRequest("$serverResourceAuthRequests/$transactionId")
        } else {
            return if (sessionId != null) {
                if (singleshot) {
                    try {
                        val appId = params.getString(IXUAF.ParamKeys.APP_ID)
                        val ssar =
                            SingleShotAuthenticationRequest
                                .createUserAuthWithAllRegisteredAuthenticators(
                                    context,
                                    appId,
                                    username,
                                )
                        ssar.addExtension("com.daon.face.ados.mode", "verify")
                        ssar.addExtension("com.daon.face.retriesRemaining", "5")
                        ssar.addExtension("com.daon.passcode.type", "ALPHANUMERIC")
                        // Add the decChain extension value here
                        // ssar.addExtension("com.daon.sdk.ados.decChain", decChain)
                        ssar.addExtension("com.daon.sdk.integrity", "true")
                        return ServiceResponseBuilder()
                            .authenticationRequest(ssar.toString())
                            .buildSuccess()
                    } catch (e: Exception) {
                        Logger.logError("serviceRequestAuthentication error ${e.message}")
                        return ServiceResponseBuilder()
                            .errorCode(-4)
                            .errorMessage(e.message ?: " ")
                            .buildFailure()
                    }
                } else {
                    // step up
                    getTransactionAuthRequest(params)
                }
            } else {
                // Login with username for SRP
                if (username != null) {
                    getAuthRequest("$serverResourceAuthRequests?userId=$username")
                } else {
                    getAuthRequest(serverResourceAuthRequests)
                }
            }
        }
    }

    private fun getTransactionAuthRequest(params: Bundle): Response {
        val createTransactionAuthRequest: CreateTransactionAuthRequest
        val parameterParser = ServiceParameterParser(params)
        val confirmationOTP = parameterParser.isConfirmationOTP()
        createTransactionAuthRequest =
            if (parameterParser.hasTransactionContent()) {
                CreateTransactionAuthRequest(
                    transactionContentType = parameterParser.transactionContentType(),
                    transactionContent = parameterParser.transactionContent(),
                    otpEnabled = confirmationOTP,
                )
            } else {
                CreateTransactionAuthRequest(otpEnabled = confirmationOTP)
            }

        val sessionId = parameterParser.sessionId()
        val payload = Gson().toJson(createTransactionAuthRequest)
        when (
            val httpResponse = http.post(serverResourceTransactionAuthRequests, payload, sessionId)
        ) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val authResponse =
                        Gson().fromJson(httpResponse.payload, CreateAuthRequestResponse::class.java)
                    return ServiceResponseBuilder()
                        .authenticationRequest(authResponse.fidoAuthenticationRequest)
                        .requestId(authResponse.authenticationRequestId)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logVerbose("getTransactionAuthRequest HTTP.Error")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    private fun getAuthRequest(relativeUrl: String): Response {
        // sessionId is null for login
        when (val httpResponse = http.get(relativeUrl, null)) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val authResponse =
                        Gson().fromJson(httpResponse.payload, CreateAuthRequestResponse::class.java)
                    return ServiceResponseBuilder()
                        .authenticationRequest(authResponse.fidoAuthenticationRequest)
                        .requestId(authResponse.authenticationRequestId)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logError("getAuthRequest HTTP.Error")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    override suspend fun serviceAuthenticate(params: Bundle): Response {
        Logger.logVerbose("serviceAuthenticate")
        val parameterParser = ServiceParameterParser(params)
        val sessionId = parameterParser.sessionId()
        return if (sessionId == null) {
            Logger.logVerbose("serviceAuthenticate createSession")
            createSession(params)
        } else {
            Logger.logVerbose("serviceAuthenticate verify")
            verify(params)
        }
    }

    private fun createSession(params: Bundle): Response {
        Logger.logVerbose("createSession")
        val parameterParser = ServiceParameterParser(params)
        val fidoAuthResponse: String = parameterParser.authenticationResponse()

        val createSession =
            CreateSession(
                fidoAuthenticationResponse = fidoAuthResponse,
                authenticationRequestId = parameterParser.requestId(),
            )
        val payload = Gson().toJson(createSession)
        // sessionId is null for login
        when (val httpResponse = http.post(serverResourceSessions, payload, null)) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val authResponse =
                        Gson().fromJson(httpResponse.payload, CreateSessionResponse::class.java)
                    return ServiceResponseBuilder()
                        .authenticationConfirmation(authResponse.fidoAuthenticationResponse)
                        .responseCode(authResponse.fidoResponseCode.toShort())
                        .lastLogin(authResponse.lastLoggedIn.toString())
                        .loggedInWith(authResponse.loggedInWith.toString())
                        .userEmail(authResponse.email)
                        .sessionId(authResponse.sessionId)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logError("createSession HTTP.Error")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    private fun verify(params: Bundle): Response {
        var validateTransactionAuth: ValidateTransactionAuth? = null
        val parameterParser = ServiceParameterParser(params)
        val authResponse: String = parameterParser.authenticationResponse()
        val authRequest: String? = parameterParser.authenticationRequest()
        val username: String? = parameterParser.username()
        val authRequestId: String? = parameterParser.requestId()

        if (authRequestId != null) {
            validateTransactionAuth =
                ValidateTransactionAuth(
                    fidoAuthenticationResponse = authResponse,
                    authenticationRequestId = authRequestId,
                )
        } else if (authRequest != null) {
            validateTransactionAuth =
                ValidateTransactionAuth(
                    email = username,
                    fidoAuthenticationRequest = authRequest,
                    fidoAuthenticationResponse = authResponse,
                )
        }

        val sessionId = parameterParser.sessionId()
        val payload = Gson().toJson(validateTransactionAuth)
        when (
            val httpResponse =
                http.post(serverResourceTransactionAuthValidation, payload, sessionId)
        ) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val validateTransactionAuthResponse =
                        Gson()
                            .fromJson(
                                httpResponse.payload,
                                ValidateTransactionAuthResponse::class.java,
                            )
                    return ServiceResponseBuilder()
                        .authenticationConfirmation(
                            validateTransactionAuthResponse.fidoAuthenticationResponse
                        )
                        .responseCode(validateTransactionAuthResponse.fidoResponseCode.toShort())
                        .responseMessage(validateTransactionAuthResponse.fidoResponseMsg)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logVerbose("verify HTTP.Error")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    override suspend fun serviceRequestDeregistration(params: Bundle): Response {
        val parameterParser = ServiceParameterParser(params)
        val sessionId = parameterParser.sessionId()
        val aaid = parameterParser.aaid()
        when (val httpResponse = http.get(serverResourceListAuthenticators, sessionId)) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val listAuthenticatorsResponse =
                        Gson()
                            .fromJson(httpResponse.payload, ListAuthenticationResponse::class.java)
                    val authenticatorInfo = listAuthenticatorsResponse.authenticatorInfoList
                    return when (
                        val deregRequest =
                            getDeregistrationRequest(aaid, authenticatorInfo, sessionId)
                    ) {
                        is Failure -> {

                            ServiceResponseBuilder()
                                .errorCode(deregRequest.params.getInt(IXUAF.ErrorKeys.ERROR_CODE))
                                .errorMessage(
                                    deregRequest.params.getString(IXUAF.ErrorKeys.ERROR_MESSAGE)
                                        ?: ""
                                )
                                .buildFailure()
                        }
                        is Success -> {
                            ServiceResponseBuilder().buildSuccess(deregRequest.params)
                        }
                    }
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    private fun getDeregistrationRequest(
        aaid: String,
        authenticatorInfo: Array<AuthenticatorInfo>,
        sessionId: String?,
    ): Response {
        var found = false
        var authInfo: AuthenticatorInfo? = null
        for (auth in authenticatorInfo) {
            if (auth.aaid == aaid && auth.status == "ACTIVE") {
                found = true
                authInfo = auth
                break
            }
        }
        if (found && authInfo != null) {
            val httpResponse =
                http.deleteResource(serverResourceAuthenticators, authInfo.id, true, sessionId)
            when (httpResponse) {
                is HTTP.Success -> {
                    return if (httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK) {
                        ServiceResponseBuilder()
                            .deregistrationRequest(httpResponse.payload ?: "")
                            .buildSuccess()
                    } else {
                        ServiceResponseBuilder()
                            .withPayloadError(httpResponse.payload)
                            .buildFailure()
                    }
                }

                is HTTP.Error -> {
                    return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
                }
            }
        } else {
            return ServiceResponseBuilder().withServerError().buildFailure()
        }
    }

    override suspend fun serviceUpdate(params: Bundle): Response {
        val parameterParser = ServiceParameterParser(params)
        val response = parameterParser.serverData()
        val uafRequests: Array<UafProtocolMessageBase> =
            UafMessageUtils.validateUafMessage(
                context,
                response,
                UafMessageUtils.OpDirection.Response,
                null,
            )
        return if (uafRequests[0].header.op == Operation.Reg) {
            serviceRegister(params)
        } else {
            serviceAuthenticate(params)
        }
    }

    override suspend fun serviceUpdateAttempt(info: Bundle): Response {
        val parameterParser = ServiceParameterParser(info)
        val verificationAttemptInfo = parameterParser.verificationAttemptInfo()
        val submitFailedAttemptRequest =
            SubmitFailedAttemptRequest(
                emailAddress = verificationAttemptInfo.emailAddress,
                attempt = verificationAttemptInfo.attempt.toString(),
                attemptsRemaining = verificationAttemptInfo.attemptsRemaining.toString(),
                globalAttempt = verificationAttemptInfo.globalAttempt.toString(),
                lockStatus = verificationAttemptInfo.lockStatus,
                errorCode = verificationAttemptInfo.errorCode.toString(),
                score = verificationAttemptInfo.score.toString(),
                userAuthKeyId = verificationAttemptInfo.userAuthKeyId,
                authenticationRequestId = verificationAttemptInfo.authenticationRequestId,
            )
        val sessionId = parameterParser.sessionId()
        val payload = Gson().toJson(submitFailedAttemptRequest)
        when (
            val httpResponse = http.post(serverResourceSubmitFailedAttempts, payload, sessionId)
        ) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val submitFailedAttemptResponse =
                        Gson()
                            .fromJson(httpResponse.payload, SubmitFailedAttemptResponse::class.java)
                    return ServiceResponseBuilder()
                        .response(submitFailedAttemptResponse.fidoAuthenticationResponse)
                        .buildSuccess()
                } else {
                    return ServiceResponseBuilder()
                        .withPayloadError(httpResponse.payload)
                        .buildFailure()
                }
            }

            is HTTP.Error -> {
                Logger.logError("verify HTTP.Error : ${httpResponse.code}")
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    private fun ServiceResponseBuilder.withServerError() = apply {
        errorCode(-4)
        errorMessage("Server communication error")
    }

    private fun ServiceResponseBuilder.withNullSession() = apply {
        errorCode(ErrorFactory.UNEXPECTED_ERROR_CODE)
        errorMessage("SessionId is null")
    }

    private fun ServiceResponseBuilder.withHTTPError(httpError: HTTP.Error) = apply {
        errorCode(httpError.code)
        errorMessage(httpError.message)
    }

    private fun ServiceResponseBuilder.withPayloadError(payload: String?) = apply {
        val error: Error =
            try {
                Logger.logError("payload error $payload")
                Gson().fromJson(payload, Error::class.java)
            } catch (_: Exception) {
                Error(code = -4, message = "Server communication error")
            }
        errorCode(error.code)
        errorMessage(error.message)
    }
}
