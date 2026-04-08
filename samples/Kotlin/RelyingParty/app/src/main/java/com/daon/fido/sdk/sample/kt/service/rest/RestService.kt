package com.daon.fido.sdk.sample.kt.service.rest

import android.content.Context
import android.os.Bundle
import com.daon.fido.sdk.sample.kt.service.rest.model.AuthDetails
import com.daon.fido.sdk.sample.kt.service.rest.model.AuthenticationRequest
import com.daon.fido.sdk.sample.kt.service.rest.model.DeregistrationRequest
import com.daon.fido.sdk.sample.kt.service.rest.model.Error
import com.daon.fido.sdk.sample.kt.service.rest.model.RegistrationChallenge
import com.daon.fido.sdk.sample.kt.service.rest.model.User
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
import org.json.JSONObject

/** @suppress */
class RestService(private val context: Context, private val params: Bundle) : IXUAFService {
    private val USERS = "users"
    private val AUTHENTICATORS = "authenticators"
    private val AUTHENTICATIONREQUESTS = "authenticationRequests"
    private val REGISTRATIONCHALLENGES = "registrationChallenges"
    private val AUTHKEYID = "authKeyId"
    private val ERRORCODE = "errorCode"
    private val SCORE = "score"
    private val ID = "id"
    private val FAILEDCLIENTATTEMPT = "failedClientAttempt"

    private var http: HTTP = HTTP(params)
    private var appId: String = params.getString("appId").toString()
    private var regPolicy: String = params.getString("regPolicy") ?: "reg"
    private var authPolicy: String = params.getString("authPolicy") ?: "auth"

    private val TAG = RestService::class.simpleName ?: Logger.TAG

    override suspend fun serviceRequestAccess(params: Bundle): Response {
        // Nothing to do at the moment

        // serviceRequestRegistration
        //
        // The registration/user will be created if it is not found by a
        // registrationId/applicationId
        // combination as long as a user is also submitted as part of the registration.
        //
        // If a userId is used and the user does not exist then a user will be created.
        return Success(Bundle())
    }

    override suspend fun serviceRevokeAccess(params: Bundle): Response {
        // Nothing to do at the moment
        return Success(Bundle())
    }

    override suspend fun serviceRequestRegistration(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceRequestRegistration")
        when (
            val httpResponse =
                http.post(REGISTRATIONCHALLENGES, createRequestRegistrationPayload(params))
        ) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val registrationChallenge =
                        Gson().fromJson(httpResponse.payload, RegistrationChallenge::class.java)

                    return ServiceResponseBuilder()
                        .registrationRequest(registrationChallenge.fidoRegistrationRequest)
                        .requestId(registrationChallenge.id)
                        .buildSuccess()
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

    private fun createRequestRegistrationPayload(params: Bundle): String {

        val parameterParser = ServiceParameterParser(params)
        val username = parameterParser.username()

        val user = JSONObject()
        user.put("userId", username)

        val policy = JSONObject()
        policy.put("policyId", regPolicy)
        val application = JSONObject()
        application.put("applicationId", appId)
        policy.put("application", application)

        val reg = JSONObject()
        reg.put("registrationId", username)
        reg.put("application", application)
        reg.put("user", user)

        val challenge = JSONObject()
        challenge.put("policy", policy)
        challenge.put("registration", reg)

        return challenge.toString()

        // Example challnege :
        // {"policy":{"policyId":"reg","application":{"applicationId":"fido"}},"registration":{"registrationId":"ft@ft.com fido registration","application":{"applicationId":"fido"},"user":{"userId":"ft@ft.com"}}}
    }

    override suspend fun serviceRegister(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceRegister")
        val parameterParser = ServiceParameterParser(params)
        val regRequestId = parameterParser.requestId()
        when (
            val httpResponse =
                http.post(
                    "$REGISTRATIONCHALLENGES/$regRequestId",
                    createRegistrationPayload(params),
                )
        ) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val registrationChallenege =
                        Gson().fromJson(httpResponse.payload, RegistrationChallenge::class.java)

                    return ServiceResponseBuilder()
                        .registrationConfirmation(registrationChallenege.fidoRegistrationResponse)
                        .responseCode(registrationChallenege.fidoResponseCode.toShort())
                        .responseMessage(registrationChallenege.fidoResponseMsg)
                        .buildSuccess()
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

    private fun createRegistrationPayload(params: Bundle): String {
        val parameterParser = ServiceParameterParser(params)
        val fidoRegistrationResponse: String = parameterParser.registrationResponse()
        val regRequestId = parameterParser.requestId()
        val request = JSONObject()
        request.put("id", regRequestId)
        request.put("status", "PENDING")
        request.put("fidoRegistrationResponse", fidoRegistrationResponse)
        return request.toString()
    }

    override suspend fun serviceRequestAuthentication(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceRequestAuthentication")
        val parameterParser = ServiceParameterParser(params)
        val singleshot = parameterParser.isSingleShot()
        if (singleshot) {
            try {
                val appId = parameterParser.appId()
                val username = parameterParser.username()
                val ssar =
                    SingleShotAuthenticationRequest.createUserAuthWithAllRegisteredAuthenticators(
                        context,
                        appId,
                        username,
                    )
                ssar.addExtension("com.daon.face.ados.mode", "verify")
                ssar.addExtension("com.daon.face.retriesRemaining", "5")
                ssar.addExtension("com.daon.face.liveness.passive.type", "server")
                ssar.addExtension("com.daon.face.liveness.active.type", "none")
                ssar.addExtension("com.daon.passcode.type", "ALPHANUMERIC")
                // Add the decChain extension value here
                // ssar.addExtension("com.daon.sdk.ados.decChain", decChain)
                ssar.addExtension("com.daon.sdk.integrity", "true")
                return ServiceResponseBuilder()
                    .authenticationRequest(ssar.toString())
                    .buildSuccess()
            } catch (e: Exception) {
                Logger.logError(TAG, "serviceRequestAuthentication error ${e.message}")
                return ServiceResponseBuilder()
                    .errorCode(-4)
                    .errorMessage(e.message ?: "")
                    .buildFailure()
            }
        } else {
            when (
                val httpResponse =
                    http.post(AUTHENTICATIONREQUESTS, createAuthenticationRequestPayload(params))
            ) {
                is HTTP.Success -> {
                    if (
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                            httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                    ) {
                        val authenticationChallenge =
                            Gson().fromJson(httpResponse.payload, AuthenticationRequest::class.java)

                        return ServiceResponseBuilder()
                            .authenticationRequest(
                                authenticationChallenge.fidoAuthenticationRequest
                            )
                            .requestId(authenticationChallenge.id)
                            .buildSuccess()
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
    }

    private fun createAuthenticationRequestPayload(params: Bundle): String {
        val parameterParser = ServiceParameterParser(params)
        val username = parameterParser.username()
        val description = parameterParser.description()
        val confirmationOTP = parameterParser.isConfirmationOTP()
        val user = JSONObject()
        user.put("userId", username)

        val policy = JSONObject()
        policy.put("policyId", authPolicy)
        val application = JSONObject()
        application.put("applicationId", appId)
        policy.put("application", application)

        val request = JSONObject()
        request.put("policy", policy)
        if (parameterParser.hasTransactionContent()) {
            request.put("secureTransactionContentType", parameterParser.transactionContentType())
            if (parameterParser.transactionContentType().equals("text/plain", ignoreCase = true)) {
                request.put("secureTextTransactionContent", parameterParser.transactionContent())
            } else {
                request.put("secureImageTransactionContent", parameterParser.transactionContent())
            }
        }
        request.put("user", user)
        request.put("type", "FI")
        if (confirmationOTP) {
            request.put("oneTimePasswordEnabled", true)
        }
        request.put("description", description ?: "NA")
        return request.toString()
    }

    override suspend fun serviceAuthenticate(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceAuthenticate")
        val parameterParser = ServiceParameterParser(params)
        val authRequestId = parameterParser.requestId()
        val httpUrl: String =
            if (authRequestId != null) {
                "$AUTHENTICATIONREQUESTS/$authRequestId"
            } else {
                AUTHENTICATIONREQUESTS
            }
        when (val httpResponse = http.post(httpUrl, createAuthenticationPayload(params))) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val authResponse =
                        Gson().fromJson(httpResponse.payload, AuthenticationRequest::class.java)
                    return ServiceResponseBuilder()
                        .authenticationConfirmation(authResponse.fidoAuthenticationResponse)
                        .responseCode(authResponse.fidoResponseCode.toShort())
                        .responseMessage(authResponse.fidoResponseMsg)
                        .userEmail(authResponse.userId)
                        .buildSuccess()
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

    private fun createAuthenticationPayload(params: Bundle): String {
        val parameterParser = ServiceParameterParser(params)
        val fidoAuthResponse: String = parameterParser.authenticationResponse()
        val authRequestId = parameterParser.requestId()
        val fidoAuthRequest: String? = parameterParser.authenticationRequest()

        val request = JSONObject()
        request.put("fidoAuthenticationResponse", fidoAuthResponse)
        if (authRequestId == null) {
            // authRequestId is null for SingleShot request
            // making the SingleShot request here
            request.put("fidoAuthenticationRequest", fidoAuthRequest)
            val policy = JSONObject()
            policy.put("policyId", authPolicy)
            val application = JSONObject()
            application.put("applicationId", appId)
            policy.put("application", application)
            request.put("policy", policy)
            request.put("description", "Single shot")
            request.put("type", "FI")
        }

        return request.toString()
    }

    override suspend fun serviceUpdate(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceUpdate")
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

    override suspend fun serviceRequestDeregistration(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceRequestDeregistration")
        val parameterParser = ServiceParameterParser(params)
        val username = parameterParser.username()
        val aaid = parameterParser.aaid()
        if (username != null && aaid != null) {
            val authId = getActiveAuthenticatorId(username, aaid)
            if (authId != null) {
                return archiveAuthenticator(authId)
            }
        }
        return ServiceResponseBuilder().withServerError().buildFailure()
    }

    private fun archiveAuthenticator(authId: String): Response {
        val httpUrl = "$AUTHENTICATORS/$authId/archived"
        return when (val httpResponse = http.post(httpUrl, "{}")) {
            is HTTP.Success -> {
                val deregRequest =
                    Gson().fromJson(httpResponse.payload, DeregistrationRequest::class.java)
                ServiceResponseBuilder()
                    .deregistrationRequest(deregRequest.fidoDeregistrationRequest)
                    .buildSuccess()
            }

            is HTTP.Error -> {
                return ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    private fun getActiveAuthenticatorId(username: String, aaid: String): String? {
        when (val authResponse = getAuthenticators(username)) {
            is Success -> {
                val authDetailsJson =
                    authResponse.params.getString("authDetails")?.let { JSONObject(it) }
                if (authDetailsJson != null) {
                    if (authDetailsJson.has("items")) {
                        val authDetails = authDetailsJson.getJSONArray("items")
                        val authDetailsArray =
                            Gson().fromJson(authDetails.toString(), Array<AuthDetails>::class.java)
                        for (auth in authDetailsArray) {
                            if (
                                auth.authenticatorAttestationId == aaid && auth.status == "ACTIVE"
                            ) {
                                return auth.id
                            }
                        }
                    }
                }
                return null
            }

            is Failure -> {
                return null
            }
        }
    }

    private fun parseUserResponse(userResponseString: String?): User? {
        val usersJson = userResponseString?.let { JSONObject(it) }
        if (usersJson != null) {
            if (usersJson.has("items")) {
                val userDetails = usersJson.getJSONArray("items")
                val usersArray = Gson().fromJson(userDetails.toString(), Array<User>::class.java)
                for (user in usersArray) {
                    if (user.status == "ACTIVE") {
                        return user
                    }
                }
            }
        }
        return null
    }

    private fun getAuthenticators(username: String): Response {
        when (val userResponse = getUser(username)) {
            is Success -> {
                val userString = userResponse.params.getString("user")
                val user = parseUserResponse(userString)
                if (user != null) {
                    val httpUrl = "$USERS/${user.id}/authenticators?limit=1000"
                    return when (val authResponse = http.get(httpUrl)) {
                        is HTTP.Success -> {
                            ServiceResponseBuilder()
                                .customParameter("authDetails", authResponse.payload)
                                .buildSuccess()
                        }

                        is HTTP.Error -> {
                            return ServiceResponseBuilder()
                                .withHTTPError(authResponse)
                                .buildFailure()
                        }
                    }
                } else {
                    return ServiceResponseBuilder().withServerError().buildFailure()
                }
            }

            is Failure -> {
                return ServiceResponseBuilder()
                    .errorCode(userResponse.params.getInt(IXUAF.ErrorKeys.ERROR_CODE))
                    .errorMessage(
                        userResponse.params.getString(IXUAF.ErrorKeys.ERROR_MESSAGE) ?: ""
                    )
                    .buildFailure()
            }
        }
    }

    private fun getUser(username: String): Response {
        val httpUrl = "$USERS?userId=$username"
        return when (val httpResponse = http.get(httpUrl)) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    ServiceResponseBuilder()
                        .customParameter("user", httpResponse.payload)
                        .buildSuccess()
                } else {
                    ServiceResponseBuilder().withPayloadError(httpResponse.payload).buildFailure()
                }
            }

            is HTTP.Error -> {
                ServiceResponseBuilder().withHTTPError(httpResponse).buildFailure()
            }
        }
    }

    override suspend fun serviceUpdateAttempt(info: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceUpdateAttempt")
        val parameterParser = ServiceParameterParser(info)
        val verificationAttemptInfo = parameterParser.verificationAttemptInfo()
        val paramId = verificationAttemptInfo.userAuthKeyId
        val paramErrorCode = verificationAttemptInfo.errorCode
        val paramScore = verificationAttemptInfo.score
        val authRequestId = verificationAttemptInfo.authenticationRequestId

        val attempts = JSONObject()
        attempts.put(AUTHKEYID, paramId)
        attempts.put(ERRORCODE, paramErrorCode)
        attempts.put(SCORE, paramScore)

        val request = JSONObject()
        request.put(ID, authRequestId)
        request.put(FAILEDCLIENTATTEMPT, attempts)

        val httpUrl = "$AUTHENTICATIONREQUESTS/$authRequestId/appendFailedAttempt"

        when (val httpResponse = http.post(httpUrl, request.toString())) {
            is HTTP.Success -> {
                if (
                    httpResponse.httpStatusCode == HttpURLConnection.HTTP_CREATED ||
                        httpResponse.httpStatusCode == HttpURLConnection.HTTP_OK
                ) {
                    val attemptResponse =
                        Gson().fromJson(httpResponse.payload, AuthenticationRequest::class.java)
                    return ServiceResponseBuilder()
                        .response(attemptResponse.fidoAuthenticationResponse)
                        .buildSuccess()
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

    override suspend fun serviceDeleteUser(params: Bundle): Response {
        Logger.logVerbose(TAG, "RestService serviceDeleteUser")
        val parameterParser = ServiceParameterParser(params)
        val username = parameterParser.username()
        if (username != null) return archiveUser(username)
        return ServiceResponseBuilder().withUsernameNull().buildFailure()
    }

    private fun archiveUser(username: String): Response {
        when (val userResponse = getUser(username)) {
            is Success -> {
                val userString = userResponse.params.getString("user")
                val user = Gson().fromJson(userString, User::class.java)
                val httpUrl = "$USERS/${user.id}/archived"
                return when (val response = http.post(httpUrl, " ")) {
                    is HTTP.Success -> {
                        Success(Bundle())
                    }

                    is HTTP.Error -> {
                        ServiceResponseBuilder().withHTTPError(response).buildFailure()
                    }
                }
            }

            is Failure -> {
                return ServiceResponseBuilder()
                    .errorCode(userResponse.params.getInt(IXUAF.ErrorKeys.ERROR_CODE))
                    .errorMessage(
                        userResponse.params.getString(IXUAF.ErrorKeys.ERROR_MESSAGE) ?: ""
                    )
                    .buildFailure()
            }
        }
    }

    private fun ServiceResponseBuilder.withServerError() = apply {
        errorCode(-4)
        errorMessage("Server communication error")
    }

    private fun ServiceResponseBuilder.withUsernameNull() = apply {
        errorCode(ErrorFactory.UNEXPECTED_ERROR_CODE)
        errorMessage("username is null")
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
            } catch (e: Exception) {
                Error(code = -4, message = "Server communication error")
            }
        errorCode(error.code)
        errorMessage(error.message)
    }
}
