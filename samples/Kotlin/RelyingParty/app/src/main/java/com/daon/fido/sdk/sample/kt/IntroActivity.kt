package com.daon.fido.sdk.sample.kt

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.daon.fido.sdk.sample.kt.capture.face.FaceScreen
import com.daon.fido.sdk.sample.kt.capture.fingerprint.FingerprintScreen
import com.daon.fido.sdk.sample.kt.capture.ootp.OOTPScreen
import com.daon.fido.sdk.sample.kt.capture.passcode.PasscodeScreen
import com.daon.fido.sdk.sample.kt.capture.voice.VoiceScreen
import com.daon.fido.sdk.sample.kt.push.FidoFirebaseMessagingService
import com.daon.fido.sdk.sample.kt.screens.authenticators.AuthenticatorsScreen
import com.daon.fido.sdk.sample.kt.screens.authenticators.AuthenticatorsViewModel
import com.daon.fido.sdk.sample.kt.screens.authenticators.RegistrationChoicesScreen
import com.daon.fido.sdk.sample.kt.screens.home.HomeScreen
import com.daon.fido.sdk.sample.kt.screens.home.HomeViewModel
import com.daon.fido.sdk.sample.kt.screens.intro.AccountListScreen
import com.daon.fido.sdk.sample.kt.screens.intro.AuthenticationChoicesScreen
import com.daon.fido.sdk.sample.kt.screens.intro.IntroScreen
import com.daon.fido.sdk.sample.kt.screens.intro.IntroViewModel
import com.daon.fido.sdk.sample.kt.screens.push.PushAuthenticationChoicesScreen
import com.daon.fido.sdk.sample.kt.screens.push.PushAuthenticationScreen
import com.daon.fido.sdk.sample.kt.screens.push.PushAuthenticationViewModel
import com.daon.fido.sdk.sample.kt.screens.push.PushTransactionConfirmationScreen
import com.daon.fido.sdk.sample.kt.screens.transaction.TransactionChoicesScreen
import com.daon.fido.sdk.sample.kt.screens.transaction.TransactionConfirmationScreen
import com.daon.fido.sdk.sample.kt.settings.DevSettingsWrapper
import com.daon.fido.sdk.sample.kt.settings.loadDevSettings
import com.daon.fido.sdk.sample.kt.settings.saveImmediateSetting
import com.daon.fido.sdk.sample.kt.settings.saveServerSettings
import com.daon.fido.sdk.sample.kt.ui.theme.IdentityxandroidsdkfidoTheme
import com.daon.fido.sdk.sample.kt.util.FidoAppState
import com.daon.fido.sdk.sample.kt.util.getFidoExtensions
import com.daon.fido.sdk.sample.kt.util.rememberFidoAppState
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.OOTPController
import com.daon.sdk.faceauthenticator.controller.FaceController
import com.daon.sdk.xauth.IXUAF
import com.daon.sdk.xauth.IXUAFService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/*
 * This file defines the `IntroActivity` class, which is the entry point of the application.
 * It sets up the initial UI using Jetpack Compose and handles navigation between different screens.
 */
@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {

    lateinit var ixuaf: IXUAF

    @Inject lateinit var service: IXUAFService

    @Inject lateinit var prefs: SharedPreferences

    // Use MutableState so Compose can observe changes from onNewIntent
    private val pendingPushTransactionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handlePushIntent(intent)

        setContent { FidoApp() }

        ixuaf = IXUAF(this, service, getFidoExtensions(prefs))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
    }

    private fun handlePushIntent(intent: Intent?) {
        if (intent?.action == FidoFirebaseMessagingService.ACTION_PUSH_AUTHENTICATION) {
            pendingPushTransactionId.value =
                intent.getStringExtra(FidoFirebaseMessagingService.EXTRA_TRANSACTION_ID)
        }
    }

    private fun restartApp() {
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    @Composable
    fun FidoApp() {
        IdentityxandroidsdkfidoTheme {
            val scope = rememberCoroutineScope()
            val appState: FidoAppState = rememberFidoAppState()
            val navController = appState.navController
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current
            var devSettings by remember { mutableStateOf(loadDevSettings(context, prefs)) }

            // Handle pending push authentication navigation
            val pushTransactionId = pendingPushTransactionId.value
            LaunchedEffect(pushTransactionId) {
                pushTransactionId?.let { transactionId ->
                    navController.navigate(Screen.PushAuth.createRoute(transactionId))
                    pendingPushTransactionId.value = null
                }
            }

            // Scaffold layout for the app
            Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding
                ->

                // Navigation host for the app
                NavHost(
                    navController = navController,
                    startDestination = Screen.Intro.route,
                    modifier = Modifier.Companion.padding(innerPadding),
                ) {

                    // Composable for the Intro screen
                    composable(route = Screen.Intro.route) {
                        DevSettingsWrapper(
                            devSettings = devSettings,
                            onServerSettingsSaveAndRestart = { settings ->
                                saveServerSettings(prefs, settings)
                                scope.launch {
                                    ixuaf.reset()
                                    restartApp()
                                }
                            },
                            onImmediateSettingChange = { key, value ->
                                saveImmediateSetting(prefs, key, value)
                                devSettings = loadDevSettings(context, prefs)
                            },
                        ) {
                            IntroScreen(
                                onNavigateToHome = { user: String, sessionId: String ->
                                    navController.navigate(Screen.Home.createRoute(user, sessionId))
                                },
                                onNavigateToChooseAuth = { _, _ ->
                                    navController.navigate(Screen.AuthenticationAuths.route)
                                },
                                onNavigateToAccounts = { _, _ ->
                                    navController.navigate(Screen.Accounts.route)
                                },
                                onNavigateToPasscode = {
                                    navController.navigate(Screen.Passcode.route)
                                },
                                onNavigateToFace = { navController.navigate(Screen.Face.route) },
                                onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                                onNavigateToFingerprint = {
                                    navController.navigate(Screen.Fingerprint.route)
                                },
                                onNavigateToOOTP = { navController.navigate(Screen.OOTP.route) },
                                onNavigateUp = { navController.popBackStack() },
                            )
                        }
                    }

                    // Composable for the Home screen
                    composable(Screen.Home.route) {
                        val user = it.arguments?.getString("user")
                        val sessionId = it.arguments?.getString("sessionId")
                        if (user != null && sessionId != null) {
                            HomeScreen(
                                user,
                                sessionId,
                                onNavigateToRegistration = { sessionId: String ->
                                    navController.navigate(
                                        Screen.Registration.createRoute(sessionId)
                                    )
                                },
                                backToIntro = {
                                    navController.navigate(Screen.Intro.route) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                },
                                onNavigateToChooseAuth = { _, _ ->
                                    navController.navigate(Screen.TransactionAuths.route)
                                },
                                onNavigateToPasscode = {
                                    navController.navigate(Screen.Passcode.route)
                                },
                                onNavigateToFace = { navController.navigate(Screen.Face.route) },
                                onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                                onNavigateToFingerprint = {
                                    navController.navigate(Screen.Fingerprint.route)
                                },
                                onNavigateToOOTP = { navController.navigate(Screen.OOTP.route) },
                                onNavigateUp = { navController.popBackStack() },
                                onNavigateToTransactionConfirmation = { _, _ ->
                                    navController.navigate(Screen.TransactionConfirmation.route)
                                },
                            )
                        }
                    }

                    // Composable for the Passcode screen
                    composable(Screen.Passcode.route) {
                        val previousBackStackEntry = remember {
                            navController.previousBackStackEntry
                        }
                        val passcodeController =
                            when (previousBackStackEntry?.destination?.route) {
                                Screen.Authenticators.route ->
                                    hiltViewModel<AuthenticatorsViewModel>(previousBackStackEntry)
                                        .getPasscodeController()

                                Screen.Intro.route ->
                                    hiltViewModel<IntroViewModel>(previousBackStackEntry)
                                        .getPasscodeController()

                                Screen.Home.route ->
                                    hiltViewModel<HomeViewModel>(previousBackStackEntry)
                                        .getPasscodeController()

                                Screen.PushAuthentication.route ->
                                    hiltViewModel<PushAuthenticationViewModel>(
                                            previousBackStackEntry
                                        )
                                        .getPasscodeController()
                                else -> null
                            }
                        passcodeController?.let {
                            PasscodeScreen(
                                onNavigateUp = { navController.popBackStack() },
                                passcodeController = it,
                            )
                        }
                    }
                    // Composable for the Face screen
                    composable(Screen.Face.route) {
                        val parentEntry: NavBackStackEntry?
                        var parentViewModel: IntroViewModel? = null
                        var faceController: FaceController? = null
                        val previousBackStackEntry =
                            remember(it) { navController.previousBackStackEntry }

                        when (previousBackStackEntry?.destination?.route) {
                            Screen.Authenticators.route -> {
                                parentEntry = previousBackStackEntry
                                faceController =
                                    hiltViewModel<AuthenticatorsViewModel>(parentEntry)
                                        .getFaceController()
                            }

                            Screen.Intro.route -> {
                                parentEntry = previousBackStackEntry
                                parentViewModel = hiltViewModel<IntroViewModel>(parentEntry)
                                faceController =
                                    hiltViewModel<IntroViewModel>(parentEntry).getFaceController()
                            }

                            Screen.Home.route -> {
                                parentEntry = previousBackStackEntry
                                faceController =
                                    hiltViewModel<HomeViewModel>(parentEntry).getFaceController()
                            }

                            Screen.PushAuthentication.route -> {
                                parentEntry = previousBackStackEntry
                                faceController =
                                    hiltViewModel<PushAuthenticationViewModel>(parentEntry)
                                        .getFaceController()
                            }
                        }
                        faceController?.let { controller ->
                            FaceScreen(
                                onNavigateUp = { navController.popBackStack() },
                                introViewModel = parentViewModel,
                                onNavigateToAccounts = { _, _ ->
                                    navController.navigate(Screen.Accounts.route)
                                },
                                faceController = controller,
                            )
                        }
                    }

                    // Composable for the Voice screen
                    composable(Screen.Voice.route) {
                        val previousBackStackEntry = navController.previousBackStackEntry
                        val voiceController =
                            when (previousBackStackEntry?.destination?.route) {
                                Screen.Authenticators.route ->
                                    hiltViewModel<AuthenticatorsViewModel>(previousBackStackEntry)
                                        .getVoiceController()

                                Screen.Intro.route ->
                                    hiltViewModel<IntroViewModel>(previousBackStackEntry)
                                        .getVoiceController()

                                Screen.Home.route ->
                                    hiltViewModel<HomeViewModel>(previousBackStackEntry)
                                        .getVoiceController()

                                Screen.PushAuthentication.route ->
                                    hiltViewModel<PushAuthenticationViewModel>(
                                            previousBackStackEntry
                                        )
                                        .getVoiceController()
                                else -> null
                            }
                        voiceController?.let {
                            VoiceScreen(
                                onNavigateUp = { navController.popBackStack() },
                                voiceController = it,
                            )
                        }
                    }

                    // Composable for the Fingerprint screen
                    composable(Screen.Fingerprint.route) {
                        val previousBackStackEntry =
                            remember(it) { navController.previousBackStackEntry }

                        val fingerController =
                            when (previousBackStackEntry?.destination?.route) {
                                Screen.Authenticators.route ->
                                    hiltViewModel<AuthenticatorsViewModel>(previousBackStackEntry)
                                        .getFingerprintController()

                                Screen.Intro.route ->
                                    hiltViewModel<IntroViewModel>(previousBackStackEntry)
                                        .getFingerprintController()

                                Screen.Home.route ->
                                    hiltViewModel<HomeViewModel>(previousBackStackEntry)
                                        .getFingerprintController()

                                Screen.PushAuthentication.route ->
                                    hiltViewModel<PushAuthenticationViewModel>(
                                            previousBackStackEntry
                                        )
                                        .getFingerprintController()
                                else -> null
                            }

                        fingerController?.let {
                            FingerprintScreen(
                                onNavigateUp = { navController.popBackStack() },
                                biometricController = it as BiometricController,
                            )
                        }
                    }

                    // Composable for the OOTP screen
                    composable(Screen.OOTP.route) {
                        val previousBackStackEntry = remember {
                            navController.previousBackStackEntry
                        }

                        val ootpController =
                            when (previousBackStackEntry?.destination?.route) {
                                Screen.Authenticators.route ->
                                    hiltViewModel<AuthenticatorsViewModel>(previousBackStackEntry)
                                        .getOotpController()

                                Screen.Intro.route ->
                                    hiltViewModel<IntroViewModel>(previousBackStackEntry)
                                        .getOotpController()

                                Screen.Home.route ->
                                    hiltViewModel<HomeViewModel>(previousBackStackEntry)
                                        .getOotpController()

                                Screen.PushAuthentication.route ->
                                    hiltViewModel<PushAuthenticationViewModel>(
                                            previousBackStackEntry
                                        )
                                        .getOotpController()
                                else -> null
                            }

                        ootpController?.let {
                            OOTPScreen(
                                onNavigateUp = { navController.popBackStack() },
                                ootpController = it as OOTPController,
                            )
                        }
                    }

                    // Navigation graph for the registration flow
                    registerGraph(navController = navController)

                    // Composable for the AuthenticationAuths screen
                    composable(Screen.AuthenticationAuths.route) {
                        val parentEntry =
                            remember(it) { navController.getBackStackEntry(Screen.Intro.route) }
                        val parentViewModel = hiltViewModel<IntroViewModel>(parentEntry)
                        AuthenticationChoicesScreen(
                            onNavigateUp = { navController.popBackStack() },
                            parentViewModel,
                        )
                    }

                    composable(Screen.Accounts.route) {
                        val parentEntry =
                            remember(it) { navController.getBackStackEntry(Screen.Intro.route) }
                        val parentViewModel = hiltViewModel<IntroViewModel>(parentEntry)
                        AccountListScreen(
                            onNavigateUp = { navController.popBackStack() },
                            parentViewModel,
                        )
                    }

                    // Composable for the TransactionAuths screen
                    composable(Screen.TransactionAuths.route) {
                        val parentEntry =
                            remember(it) {
                                runCatching { navController.getBackStackEntry(Screen.Home.route) }
                                    .getOrNull()
                            }
                        val parentViewModel =
                            parentEntry?.let { entry -> hiltViewModel<HomeViewModel>(entry) }
                        if (parentViewModel == null) {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Intro.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        } else {
                            TransactionChoicesScreen(
                                onNavigateUp = { navController.popBackStack() },
                                parentViewModel,
                            )
                        }
                    }

                    // Composable for the TransactionConfirmation screen
                    composable(Screen.TransactionConfirmation.route) {
                        val parentEntry =
                            remember(it) {
                                runCatching { navController.getBackStackEntry(Screen.Home.route) }
                                    .getOrNull()
                            }
                        val parentViewModel =
                            parentEntry?.let { entry -> hiltViewModel<HomeViewModel>(entry) }
                        if (parentViewModel == null) {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Intro.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        } else {
                            TransactionConfirmationScreen(
                                onNavigateUp = { navController.popBackStack() },
                                parentViewModel,
                            )
                        }
                    }

                    // Push authentication navigation graph
                    pushAuthGraph(navController = navController)
                }
            }
        }
    }

    // Navigation graph for push authentication flow
    private fun NavGraphBuilder.pushAuthGraph(navController: NavController) {
        navigation(
            startDestination = Screen.PushAuthentication.route,
            route = Screen.PushAuth.route,
        ) {
            composable(Screen.PushAuthentication.route) {
                val transactionId = it.arguments?.getString("transactionId")
                if (transactionId != null) {
                    PushAuthenticationScreen(
                        transactionId = transactionId,
                        onNavigateToChooseAuth = { _, _ ->
                            navController.navigate(Screen.PushAuthenticationAuths.route)
                        },
                        onNavigateToPasscode = {
                            navController.navigate(Screen.Passcode.route) {
                                popUpTo(Screen.PushAuthentication.route) { inclusive = false }
                            }
                        },
                        onNavigateToFace = {
                            navController.navigate(Screen.Face.route) {
                                popUpTo(Screen.PushAuthentication.route) { inclusive = false }
                            }
                        },
                        onNavigateToVoice = {
                            navController.navigate(Screen.Voice.route) {
                                popUpTo(Screen.PushAuthentication.route) { inclusive = false }
                            }
                        },
                        onNavigateToFingerprint = {
                            navController.navigate(Screen.Fingerprint.route) {
                                popUpTo(Screen.PushAuthentication.route) { inclusive = false }
                            }
                        },
                        onNavigateToOOTP = {
                            navController.navigate(Screen.OOTP.route) {
                                popUpTo(Screen.PushAuthentication.route) { inclusive = false }
                            }
                        },
                        onNavigateToTransactionConfirmation = {
                            navController.navigate(Screen.PushTransactionConfirmation.route)
                        },
                        onNavigateUp = { navController.popBackStack() },
                        onAuthenticationComplete = { message ->
                            // Pop the entire push auth graph
                            navController.popBackStack(Screen.PushAuth.route, inclusive = true)
                            message?.let {
                                Toast.makeText(navController.context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            composable(Screen.PushAuthenticationAuths.route) {
                val transactionId = it.arguments?.getString("transactionId")
                val parentEntry =
                    remember(it) {
                        navController.getBackStackEntry(
                            Screen.PushAuthentication.createRoute(transactionId ?: "")
                        )
                    }
                val parentViewModel = hiltViewModel<PushAuthenticationViewModel>(parentEntry)
                PushAuthenticationChoicesScreen(
                    onNavigateUp = { navController.popBackStack() },
                    parentViewModel,
                )
            }

            composable(Screen.PushTransactionConfirmation.route) {
                // Get transactionId from previous back stack entry (PushAuthentication screen)
                val previousEntry = navController.previousBackStackEntry
                val transactionId = previousEntry?.arguments?.getString("transactionId") ?: ""
                val parentEntry =
                    remember(it) {
                        navController.getBackStackEntry(
                            Screen.PushAuthentication.createRoute(transactionId)
                        )
                    }
                val parentViewModel = hiltViewModel<PushAuthenticationViewModel>(parentEntry)
                PushTransactionConfirmationScreen(
                    onNavigateUp = { navController.popBackStack() },
                    parentViewModel,
                )
            }
        }
    }

    // Navigation graph for the registration flow
    private fun NavGraphBuilder.registerGraph(navController: NavController) {
        navigation(
            startDestination = Screen.Authenticators.route,
            route = Screen.Registration.route,
        ) {
            // Composable for the Authenticators screen
            composable(Screen.Authenticators.route) {
                val sessionId = it.arguments?.getString("sessionId")
                if (sessionId != null) {
                    AuthenticatorsScreen(
                        sessionId = sessionId,
                        onNavigateToChooseAuth = { _, _ ->
                            navController.navigate(Screen.RegistrationAuths.route)
                        },
                        onNavigateToPasscode = { navController.navigate(Screen.Passcode.route) },
                        onNavigateToFace = { navController.navigate(Screen.Face.route) },
                        onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                        onNavigateToFingerprint = {
                            navController.navigate(Screen.Fingerprint.route)
                        },
                        onNavigateToOOTP = { navController.navigate(Screen.OOTP.route) },
                        onNavigateUp = { navController.popBackStack() },
                    )
                }
            }

            // Composable for the RegistrationAuths screen
            composable(Screen.RegistrationAuths.route) {
                val parentEntry =
                    remember(it) { navController.getBackStackEntry(Screen.Authenticators.route) }
                val parentViewModel = hiltViewModel<AuthenticatorsViewModel>(parentEntry)
                RegistrationChoicesScreen(
                    onNavigateUp = { navController.popBackStack() },
                    parentViewModel,
                )
            }
        }
    }
}

// Sealed class representing different screens in the app
sealed class Screen(val route: String) {
    data object Intro : Screen("intro")

    data object Home : Screen("home/{user}/{sessionId}") {
        fun createRoute(user: String, sessionId: String) = "home/$user/$sessionId"
    }

    data object Passcode : Screen("passcode")

    data object Registration : Screen("registration/{sessionId}") {
        fun createRoute(sessionId: String) = "registration/$sessionId"
    }

    data object Authenticators : Screen("authenticators/{sessionId}") {
        fun createRoute(sessionId: String) = "authenticators/$sessionId"
    }

    data object RegistrationAuths : Screen("registrationAuths")

    data object AuthenticationAuths : Screen("authenticationAuths")

    data object TransactionAuths : Screen("transactionAuths")

    data object TransactionConfirmation : Screen("transactionConfirmation")

    data object Accounts : Screen("accounts")

    data object Face : Screen("face")

    data object Fingerprint : Screen("fingerprint")

    data object Voice : Screen("voice")

    data object OOTP : Screen("ootp")

    data object PushAuth : Screen("pushAuth/{transactionId}") {
        fun createRoute(transactionId: String) = "pushAuth/$transactionId"
    }

    data object PushAuthentication : Screen("pushAuthentication/{transactionId}") {
        fun createRoute(transactionId: String) = "pushAuthentication/$transactionId"
    }

    data object PushAuthenticationAuths : Screen("pushAuthenticationAuths")

    data object PushTransactionConfirmation : Screen("pushTransactionConfirmation")
}
