# Sample RP App - Kotlin

This sample application demonstrates the comprehensive use of the **IdentityX Android xAuth SDK 1.0**. The application showcases modern FIDO authentication workflows including registration, authentication, transaction authentication, and deregistration using Kotlin coroutines, suspend functions, and Jetpack Compose UI.

## Overview

The IdentityX Android xAuth SDK is a Kotlin-native SDK providing modern APIs with coroutines, suspend functions, and Flow-based processing for FIDO authentication operations.

This sample application demonstrates proper usage of the xAuth SDK APIs with suspend functions, flow-based processing, result pattern for error handling, and modern Android architecture patterns.

## Key Features

### FIDO Operations
- **User Account Management**: Create and delete user accounts with the IdentityX server
- **FIDO Authentication**: Passwordless login using registered FIDO authenticators
- **Authenticator Registration**: Register new FIDO authenticators (Face, Passcode, Voice, OOTP)
- **Transaction Authentication**: Authenticate and approve transactions with FIDO authenticators
- **Step-up Authentication**: Re-authenticate for sensitive operations
- **Authenticator Management**: View, discover, and deregister authenticators
- **Silent Authentication**: Background authentication without UI interaction
- **Confirmation OTP**: Generate one-time passwords for transaction confirmation
- **Push Authentication**: Handle authentication requests via Firebase Cloud Messaging push notifications
- **Offline OTP (OOTP)**: Generate and authenticate with offline one-time passwords

### Supported Authenticators

**ADoS Authenticators:**
- **Face (ADoS)**: Face authentication with injection attack detection and passive liveness
- **Passcode (ADoS)**: Custom PIN/passcode authentication with SRP protocol
- **Voice (ADoS)**: Voice biometric authentication

**Standard Authenticators:**
- **Fingerprint**: Native Android fingerprint/biometric authentication
- **Silent**: Background authentication without user interaction
- **OOTP**: Offline One-Time Password authenticator for transaction signing

### Modern Android Architecture
- **Jetpack Compose**: Declarative UI with Material 3 design
- **Hilt Dependency Injection**: Clean architecture with dependency injection
- **MVVM Pattern**: ViewModel-based state management
- **Kotlin Coroutines**: Asynchronous programming with suspend functions
- **StateFlow**: Reactive state management
- **Navigation Component**: Type-safe navigation with Compose

## Architecture & Design

### Core Components

#### 1. IXUAF (Main SDK Entry Point)

The `IXUAF` class is the primary entry point for all FIDO operations. It must be instantiated with:
- **Application Context**: Android application context
- **IXUAFService**: Communication service for server interactions (RPSA or REST)
- **Extensions Bundle**: Optional SDK configuration and parameters

```kotlin
// Example: Instantiate IXUAF
val service: IXUAFService = RPSAService(context, rpsaParams)
val extensions = getFidoExtensions(prefs)
val ixuaf = IXUAF(application, service, extensions)
```

**Example Extension Parameters:**
- `com.daon.sdk.log`: Enable SDK logging for debugging
- `com.daon.sdk.ados.enabled`: Enable ADOS authenticators
- `com.daon.sdk.injection.enabled`: Enable/disable injection attack detection

#### 2. Registration API

Registration workflows using suspend functions and Result pattern.

```kotlin
// Example: Start registration flow
viewModelScope.launch {
    val ixuaf = IXUAF(application, service, extensions)
    val registration = ixuaf.registration()
    
    val params = ParameterBuilder()
        .username(username)
        .sessionId(sessionId)
        .build()
    
    val chooseAuthenticatorListener = ChooseAuthenticatorListener { policy ->
        // Received Policy object with available authenticators
        _state.update { it.copy(policy = policy) }
        
        if (policy.isUpdate()) {
            // Automatic selection for update scenarios
            val groups = policy.getGroups()
            if (groups.isNotEmpty()) {
                selectAuthenticatorGroup(groups[0])
            }
        } else {
            // Navigate to authenticator selection UI
            navigateToChooseAuthenticator()
        }
    }
    
    when (val response = registration.start(params, chooseAuthenticatorListener)) {
        is Success -> {
            showToast("Registration successful")
            refreshAuthenticators()
        }
        is Failure -> {
            showToast("Registration failed: ${response.errorMessage}")
        }
    }
}
```

**Registration Flow:**
1. Call `registration.start()` with parameters
2. SDK retrieves policy from server via `IXUAFService`
3. `ChooseAuthenticatorListener.chooseAuthenticator()` callback receives `Policy` object
4. Application displays authenticator selection UI (if needed)
5. User selects and completes authenticator enrollment
6. `start()` returns `Success` or `Failure` result

#### 3. Authentication API

Authentication workflows with support for transactions, multiple accounts, and various authenticators.

```kotlin
// Example: Start authentication flow
viewModelScope.launch {
    val ixuaf = IXUAF(application, service, extensions)
    val authentication = ixuaf.authentication()
    
    // Set up listeners
    authentication.transactionConfirmationListener = TransactionConfirmationListener { transaction ->
        // Display transaction for user approval
        val content = TransactionUtils.getTransactionContent(context, transaction)
        _state.update { it.copy(transactionContent = content) }
    }
    
    authentication.confirmationOTPListener = ConfirmationOTPListener { otp ->
        // Display or use the generated OTP
        showToast("Your OTP: $otp")
    }
    
    val chooseAuthenticatorListener = ChooseAuthenticatorListener { policy ->
        _state.update { it.copy(policy = policy) }
        navigateToChooseAuthenticator()
    }
    
    val params = ParameterBuilder()
        .username(username)
        .sessionId(sessionId)
        .confirmationOTP(true)
        .transactionContentType("text/plain")
        .transactionContent("Transfer $100 to Account 123")
        .build()
    
    when (val response = authentication.start(params, chooseAuthenticatorListener)) {
        is Success -> {
            showToast("Authentication successful")
            navigateToHome()
        }
        is Failure -> {
            showToast("Authentication failed: ${response.errorMessage}")
        }
    }
}
```

**Authentication Flow:**
1. Set up listeners for transaction confirmation, OTP, etc.
2. Call `authentication.start()` with parameters
3. SDK retrieves authentication policy from server
4. Handle callbacks:
   - `AccountListListener`: User has multiple accounts, needs to select one
   - `ChooseAuthenticatorListener`: Select an authenticator
   - `TransactionConfirmationListener`: Display transaction for approval
   - `ConfirmationOTPListener`: Receive generated OTP
5. User completes authentication with selected authenticator
6. `start()` returns `Success` or `Failure` result

#### 4. Deregistration API

Handles removal of registered authenticators using suspend functions.

```kotlin
// Example: Deregister an authenticator
viewModelScope.launch {
    val ixuaf = IXUAF(application, service, extensions)
    val deregistration = ixuaf.deregistration()
    
    val params = ParameterBuilder()
        .sessionId(sessionId)
        .build()
    
    when (val response = deregistration.start(aaid, username, params)) {
        is Success -> {
            showToast("Deregistration successful")
            refreshAuthenticators()
        }
        is Failure -> {
            showToast("Deregistration failed: ${response.errorMessage}")
        }
    }
}
```

#### 5. Discover API

Discover available authenticators on the device and check registration status.

```kotlin
// Example: Discover authenticators
viewModelScope.launch {
    val ixuaf = IXUAF(application, service, extensions)
    val appId = SDKPreferences.instance().getString(IXUAF.KEY_APP_ID)
    
    ixuaf.discover().onSuccess { discoverResult ->
        val registeredAuthenticators = mutableListOf<Authenticator>()
        
        discoverResult.availableAuthenticators.forEach { auth ->
            val isRegistered = ixuaf.isRegistered(auth.aaid, username, appId)
            if (isRegistered.getOrNull() == true) {
                registeredAuthenticators.add(auth)
            }
        }
        
        _state.update { it.copy(authenticators = registeredAuthenticators) }
    }.onFailure { error ->
        showToast("Discovery failed: ${error.message}")
    }
}
```

### Communication Service (IXUAFService)

The SDK requires an implementation of `IXUAFService` for server communication. This sample supports both RPSA and REST services.

#### RPSA Service Implementation

```kotlin
class RPSAService(
    private val context: Context,
    private val params: Bundle
) : IXUAFService {

   override suspend fun serviceRequestAccess(params: Bundle): Response {
        // Create session with server
        
    }

   override suspend fun serviceRequestAuthentication(params: Bundle): Response {
        // Request authentication challenge from server
    }

   override suspend fun serviceAuthenticate(params: Bundle): Response {
        // Submit authentication response to server
    }
    
    // Additional methods for registration, deregistration, etc.
}
```

#### Dependency Injection with Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    
    @Provides
    @Singleton
    fun provideService(@ApplicationContext appContext: Context): IXUAFService {
        val rpsaParams = Bundle().apply {
            putString("server_url", Config.getProperty(Config.SERVER_URL, appContext))
        }
        return RPSAService(appContext, rpsaParams)
        
        // Alternative: Use REST service
        // return RestService(appContext, restParams)
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext appContext: Context): SharedPreferences {
        return appContext.getSharedPreferences("pref_sampleapp_kotlin", Context.MODE_PRIVATE)
    }
}
```

### Application Structure

```
app/
├── src/main/java/com/daon/fido/sdk/sample/kt/
│   ├── HiltApplication.kt              # Application class with Hilt
│   ├── IntroActivity.kt                # Main activity with navigation
│   ├── di/
│   │   └── AppModule.kt               # Hilt dependency injection module
│   ├── screens/
│   │   ├── intro/                     # Login/signup screens
│   │   │   ├── IntroScreen.kt
│   │   │   ├── IntroViewModel.kt
│   │   │   ├── AccountListScreen.kt
│   │   │   └── AuthenticationChoicesScreen.kt
│   │   ├── home/                      # Main home screen
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── authenticators/            # Authenticator management
│   │   │   ├── AuthenticatorsScreen.kt
│   │   │   ├── AuthenticatorsViewModel.kt
│   │   │   └── RegistrationChoicesScreen.kt
│   │   ├── transaction/               # Transaction confirmation
│   │   │   ├── TransactionChoicesScreen.kt
│   │   │   └── TransactionConfirmationScreen.kt
│   │   └── push/                      # Push notification authentication
│   │       ├── PushAuthenticationScreen.kt
│   │       ├── PushAuthenticationViewModel.kt
│   │       └── PushAuthenticationChoicesScreen.kt
│   ├── capture/                       # Authenticator capture UI
│   │   ├── face/                      # Face capture with Compose
│   │   │   ├── FaceScreen.kt
│   │   │   └── FaceViewModel.kt
│   │   ├── fingerprint/               # Fingerprint capture
│   │   │   ├── FingerprintScreen.kt
│   │   │   └── FingerprintViewModel.kt
│   │   ├── passcode/                  # Passcode capture
│   │   │   ├── PasscodeScreen.kt
│   │   │   └── PasscodeViewModel.kt
│   │   ├── voice/                     # Voice capture
│   │   │   ├── VoiceScreen.kt
│   │   │   └── VoiceViewModel.kt
│   │   └── ootp/                      # Offline OTP capture
│   │       ├── OOTPScreen.kt
│   │       ├── OOTPViewModel.kt
│   │       ├── RegisterOOTPScreen.kt
│   │       └── AuthenticateOOTPScreen.kt
│   ├── push/                          # Push notification handling
│   │   ├── FidoFirebaseMessagingService.kt
│   │   └── NotificationUtils.kt
│   ├── service/
│   │   ├── rpsa/                      # RPSA service implementation
│   │   │   ├── RPSAService.kt
│   │   │   └── model/                 # Server data models
│   │   └── rest/                      # REST service implementation
│   │       └── RestService.kt
│   ├── ui/theme/                      # Compose theme and components
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Buttons.kt
│   │   └── FloatingCard.kt
│   └── util/                          # Utility classes
│       ├── Config.kt
│       ├── Utils.kt
│       └── FidoAppState.kt
```

## Setup Instructions

### Prerequisites
- Android Studio Iguana (2023.2.1) or later
- Android SDK API 24 or higher (minimum SDK)
- Target SDK API 36
- Kotlin 1.9 or higher
- Java 17 or higher
- IdentityX server instance with FIDO support

### 1. Clone the Repository
```bash
git clone <repository-url>
cd identityx-android-sdk-xauth/sample-rpapp-kotlin
```

### 2. Open in Android Studio
1. Launch Android Studio
2. Select **File > Open**
3. Navigate to `sample-rpapp-kotlin`
4. Click **Open**

### 3. Configure the Application

#### Server Configuration
Edit `src/main/assets/config.properties`:

```properties
server_url=https://your-identityx-server.com
```

Or update `Config.kt` to load from your preferred configuration source.

#### License Configuration
The SDK requires a valid license. Add your license file to `src/main/assets/` or configure via extensions:

```kotlin
val extensions = Bundle().apply {
    putString("com.daon.sdk.license", "YOUR_LICENSE_KEY")
}
```

#### Push Notifications Configuration (Firebase)
To enable push authentication, configure Firebase Cloud Messaging:

1. **Add Firebase to your project**:
   - Go to the [Firebase Console](https://console.firebase.google.com/)
   - Create a new project or select an existing one
   - Add an Android app with your package name
   - Download `google-services.json` and place it in `sample-rpapp-kotlin/`

2. **Configure the IdentityX server**:
   - Upload your Firebase Server Key to the IdentityX server
   - Enable push notifications for your application

3. **Permissions**: The sample app already includes the required permission in `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   ```

4. **Request notification permission** (Android 13+): The app should request notification permission at runtime.

The sample app includes:
- `FidoFirebaseMessagingService`: Handles incoming FCM messages and displays notifications
- `NotificationUtils`: Creates notification channels and displays system notifications
- Push authentication navigation flow via `PushAuthenticationScreen`

### 4. Build and Run
1. Connect an Android device or start an emulator
2. Click **Run** in Android Studio
3. Select your target device
4. The application will install and launch

## Usage Guide

### First-Time Setup

1. **Launch the Application**
   - The app opens to the IntroScreen

2. **Create a New Account**
   - Click "Create Account"
   - A random email is generated automatically
   - Account is created on the IdentityX server
   - Session is established

3. **Register an Authenticator**
   - After account creation, you'll be prompted to register an authenticator
   - Select from available authenticators (Face, Passcode, Voice, Fingerprint, OOTP)
   - Follow the on-screen prompts to complete enrollment

### Authentication Flows

#### Standard Login
1. Launch the app (or return to IntroScreen)
2. Click "Log in with FIDO"
3. Select an authenticator if multiple are available
4. Complete authentication
5. Navigate to HomeScreen on success

#### Transaction Authentication
1. From HomeScreen, click "Step-up Auth"
2. Select an authenticator
3. Review the transaction details displayed
4. Complete authentication to approve the transaction
5. Optionally view the generated confirmation OTP

#### Push Authentication
Push authentication allows users to authenticate from a push notification triggered by an external request (e.g., web login, transaction approval).

1. Receive a push notification on the device
2. Tap the notification to open the app
3. The app navigates to the Push Authentication screen
4. Select an authenticator if multiple are available
5. Complete authentication
6. Transaction is approved on the server

**How it works:**
- Firebase Cloud Messaging (FCM) receives data-only push messages from the IdentityX server
- `FidoFirebaseMessagingService` processes the push payload and displays a system notification
- When the user taps the notification, `IntroActivity` is launched with the transaction ID
- The app navigates to `PushAuthenticationScreen` which initiates the authentication flow
- On successful authentication, the server receives the signed response

#### OOTP Authentication
OOTP (Offline One-Time Password) is used for transaction signing scenarios.

**Registration:**
1. Navigate to authenticator management
2. Select OOTP from available authenticators
3. Registration is silent (no user input required)
4. OOTP authenticator is added to your account

**Authentication:**
1. When OOTP is selected for authentication, the behavior depends on server policy:
   - **Silent mode**: Authentication completes automatically without user input
   - **Transaction UI mode**: User enters a transaction code (e.g., from QR code or displayed value)
2. The OOTP generates a signed response based on the transaction data


### Authenticator Management

#### View Registered Authenticators
1. Navigate to Authenticators screen from HomeScreen
2. See all authenticators available on this device
3. Registered authenticators are listed with status

#### Register Additional Authenticators
1. From Authenticators screen, click "Registration"
2. Select from available authenticators
3. Complete the enrollment process
4. Authenticator is added to your account

#### Deregister an Authenticator
1. Select an authenticator from the list
2. Click the deregister option
3. Confirm deregistration
4. Authenticator is removed from device and server

### Account Management

#### Delete Account
1. From HomeScreen menu, select "Delete"
2. Confirm deletion
3. All authenticators are deregistered
4. Account is deleted from server
5. Return to IntroScreen

## Code Examples

### Complete Registration with ViewModel

```kotlin
@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences
) : AndroidViewModel(application) {
    
    private val _state = MutableStateFlow(RegistrationState())
    val state: StateFlow<RegistrationState> = _state
    
    private val ixuaf = IXUAF(application, service, getFidoExtensions(prefs))
    private val registration = ixuaf.registration()
    
    private val chooseAuthenticatorListener = ChooseAuthenticatorListener { policy ->
       // Show authenticator selection UI
    }
    
    fun register(username: String, sessionId: String) {
        _state.update { it.copy(inProgress = true) }
        
        viewModelScope.launch(Dispatchers.Default) {
            val params = ParameterBuilder()
                .username(username)
                .sessionId(sessionId)
                .build()
            
            when (val response = registration.start(params, chooseAuthenticatorListener)) {
                is Success -> {
                    _state.update { it.copy(inProgress = false) }
                    _eventFlow.emit(ShowToast("Registration successful"))
                }
                is Failure -> {
                    _state.update { it.copy(inProgress = false) }
                    _eventFlow.emit(ShowToast(
                        "Registration failed: ${response.errorCode} - ${response.errorMessage}"
                    ))
                }
            }
        }
    }
}
```

### Complete Authentication with Listeners

```kotlin
@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences
) : AndroidViewModel(application) {
    
    private val _state = MutableStateFlow(AuthenticationState())
    val state: StateFlow<AuthenticationState> = _state
    
    private val ixuaf = IXUAF(application, service, getFidoExtensions(prefs))
    private val authentication = ixuaf.authentication()
    
    init {
        setupListeners()
    }
    
    private fun setupListeners() {
        // Transaction confirmation listener
        authentication.transactionConfirmationListener = TransactionConfirmationListener { transaction ->
            val content = TransactionUtils.getTransactionContent(application, transaction)
            _state.update { it.copy(
                showTransactionConfirmation = true,
                transactionContent = content
            )}
        }
        
        // Confirmation OTP listener
        authentication.confirmationOTPListener = ConfirmationOTPListener { otp ->
            _state.update { it.copy(confirmationOTP = otp) }
        }
        
        // Account list listener
        authentication.accountListListener = AccountListListener { accounts ->
            _state.update { it.copy(
                showAccountSelection = true,
                accounts = accounts
            )}
        }
    }
    
    fun authenticate(username: String, sessionId: String, withTransaction: Boolean = false) {
        _state.update { it.copy(inProgress = true) }
        
        viewModelScope.launch(Dispatchers.Default) {
            val params = ParameterBuilder()
                .username(username)
                .sessionId(sessionId)
            
            if (withTransaction) {
                params.transactionContentType("text/plain")
                     .transactionContent("Transfer $100 to Account 123")
                     .confirmationOTP(true)
            }
            
            val chooseAuthListener = ChooseAuthenticatorListener { policy ->
                _state.update { it.copy(policy = policy) }
            }
            
            when (val response = authentication.start(params.build(), chooseAuthListener)) {
                is Success -> {
                    _state.update { it.copy(inProgress = false) }
                    _eventFlow.emit(AuthenticationSuccess)
                }
                is Failure -> {
                    _state.update { it.copy(inProgress = false) }
                    _eventFlow.emit(ShowError(
                        "Error ${response.errorCode}: ${response.errorMessage}"
                    ))
                }
            }
        }
    }
}
```

### Jetpack Compose UI Example

```kotlin
@Composable
fun AuthenticatorsScreen(
    viewModel: AuthenticatorsViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.onStart()
        
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ShowToast -> {
                    // Show toast message
                }
                is NavigateToAuthenticatorSelection -> {
                    navController.navigate("choose_authenticator")
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Authenticators") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.register() }) {
                Icon(Icons.Default.Add, "Register")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (state.inProgress) {
                CircularProgressIndicator()
            }
            
            LazyColumn {
                items(state.authenticators) { authenticator ->
                    AuthenticatorCard(
                        authenticator = authenticator,
                        onDeregister = { viewModel.deregister(it) }
                    )
                }
            }
        }
    }
}
```

## Key Kotlin API Patterns

### Suspend Functions
All FIDO operations are suspend functions that can be called from coroutines:

```kotlin
viewModelScope.launch {
    val response = ixuaf.registration().start(params, listener)
    // Handle response
}
```

### Result Pattern
Operations return `Success` or `Failure` sealed class results:

```kotlin
when (val response = authentication.start(params, listener)) {
    is Success -> {
        // Handle success
        val data = response.data
    }
    is Failure -> {
        // Handle failure
        val errorCode = response.errorCode
        val errorMessage = response.errorMessage
    }
}
```

### StateFlow for Reactive UI
Use StateFlow for reactive state management:

```kotlin
private val _state = MutableStateFlow(UiState())
val state: StateFlow<UiState> = _state

// Update state
_state.update { currentState ->
    currentState.copy(loading = true)
}

// Collect in Compose
val state by viewModel.state.collectAsState()
```

### ParameterBuilder for Type-Safe Parameters
Use ParameterBuilder instead of raw Bundles:

```kotlin
val params = ParameterBuilder()
    .username(username)
    .sessionId(sessionId)
    .transactionContent("Transfer $100")
    .confirmationOTP(true)
    .build()
```

### Listener Interfaces
Set listeners before calling operations:

```kotlin
authentication.transactionConfirmationListener = TransactionConfirmationListener { transaction ->
    // Handle transaction confirmation
}

authentication.confirmationOTPListener = ConfirmationOTPListener { otp ->
    // Handle OTP
}
```

### Push Authentication
Handle push notification triggered authentication:

```kotlin
// FidoFirebaseMessagingService.kt - Handle incoming FCM messages
class FidoFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            val transactionId = remoteMessage.data["id"] ?: return
            
            // Create intent to launch IntroActivity with transaction ID
            val intent = Intent(this, IntroActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                action = ACTION_PUSH_AUTHENTICATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            // Display notification
            NotificationUtils(applicationContext).showNotificationMessage(
                title = remoteMessage.data["provider"] ?: "Authentication Request",
                message = remoteMessage.data["description"] ?: "Tap to authenticate",
                intent = intent
            )
        }
    }
    
    override fun onNewToken(token: String) {
        // Register FCM token with SDK for push notifications
        IXUAF(applicationContext, null, null).setPushNotificationServiceToken(token)
    }
}

// PushAuthenticationViewModel.kt - Handle push authentication flow
fun startPushAuthentication(transactionId: String) {
    val ixuaf = IXUAF(application, service, getFidoExtensions(prefs))
    val authentication = ixuaf.authentication()
    
    viewModelScope.launch(Dispatchers.Default) {
        val parameters = ParameterBuilder()
            .id(transactionId)  // Use transaction ID from push payload
            .username(currentUser)
            .build()
        
        val response = authentication.start(parameters, chooseAuthenticatorListener)
        when (response) {
            is Success -> { /* Authentication completed */ }
            is Failure -> { /* Handle error */ }
        }
    }
}
```

### OOTP Authentication
Handle Offline OTP registration and authentication:

```kotlin
// OOTPViewModel.kt - OOTP controller usage with Flow
class OOTPViewModel : AndroidViewModel(application) {
    
    lateinit var controller: OOTPController
    
    fun onStart(ootpController: OOTPController) {
        controller = ootpController
        controller.startCapture()
    }
    
    // Register OOTP (silent operation - no user input required)
    fun register() {
        viewModelScope.launch {
            controller.register().collect { event ->
                when (event) {
                    is CaptureController.RegistrationEvent.Success -> {
                        navigateUp()
                    }
                    is CaptureController.RegistrationEvent.Failure.Validation -> {
                        showError("${event.error.message}, ${event.retriesRemaining} retries remaining")
                    }
                    is CaptureController.RegistrationEvent.Failure.Fatal -> {
                        showError("Operation failed: ${event.error.message}")
                    }
                }
            }
        }
    }
    
    // Authenticate with optional transaction data
    fun authenticate(transactionData: String? = null) {
        viewModelScope.launch {
            controller.authenticate(transactionData).collect { event ->
                when (event) {
                    is CaptureController.AuthenticationEvent.Success -> {
                        navigateUp()
                    }
                    is CaptureController.AuthenticationEvent.Failure.Validation -> {
                        showError("${event.error.message}, ${event.retriesRemaining} retries remaining")
                    }
                    is CaptureController.AuthenticationEvent.Failure.Fatal -> {
                        showError("Operation failed: ${event.error.message}")
                    }
                }
            }
        }
    }
}

// Check if OOTP requires transaction UI
val requiresTransactionUI = ootpController.configuration.getBooleanExtension(
    Extensions.OTP_TRANSACTION_UI, 
    false
)

if (requiresTransactionUI) {
    // Show transaction input UI
} else {
    // Authenticate silently
    viewModel.authenticate(null)
}
```

## Dependencies

This sample uses the following key dependencies:

**xAuth SDK:**
- `xauth`: Kotlin xAuth SDK with coroutines and suspend functions
- `xauth-device`: Device SDK
- `xauth-crypto`: Cryptographic operations
- `authenticator-core`: Core authenticator framework
- `authenticator-face-ifp`: Face IFP authenticator with injection detection
- `authenticator-voice`: Voice biometric authenticator

**Face Processing:**
- `daon-face`: Core face processing
- `daon-face-quality`: Face quality assessment
- `daon-face-liveness`: Passive liveness detection
- `daon-face-matcher`: Face template matching
- `daon-face-detector`: Face detection
- `daon-face-capture`: Face capture with injection attack detection

**Voice Processing:**
- `daon-voice`: Voice biometric processing
- `daon-voice-ogg`: OGG audio format support

**Push Notifications:**
- `firebase-messaging`: Firebase Cloud Messaging for push notifications

**Android Libraries:**
- Jetpack Compose with Material 3
- Hilt for dependency injection
- Navigation Compose
- CameraX for camera operations
- AndroidX Biometric
- Kotlin Coroutines and Flow

See `build.gradle.kts` for complete dependency list.

## Advanced Features

### Extension Configuration with ExtensionBuilder

The SDK provides an `ExtensionBuilder` class for type-safe configuration of SDK behavior and authenticator settings. This builder pattern approach is preferred over manually creating Bundle objects, as it provides compile-time safety, better IDE support, and clearer code.

#### ExtensionBuilder Overview

The `ExtensionBuilder` class offers a fluent API to configure:
- **General SDK Settings**: Logging, ADOS, screen capture, overlay detection
- **Face Authenticator**: Recognition thresholds, quality settings, liveness configuration
- **Fingerprint/Biometric**: Device biometric prompts, silent registration, locking behavior
- **Passcode**: Length constraints, type (numeric/alphanumeric)
- **Voice**: Quality, text validation, recognition timeout
- **Injection Attack Detection**: Enable/disable face injection attack prevention

#### Basic Usage Example

```kotlin
fun getFidoExtensions(prefs: SharedPreferences): Bundle {
    val isInjectionDetectionEnabled = prefs.getBoolean("injectionDetectionEnabled", true)
    
    val extensions = ExtensionBuilder()
        .adosEnabled(true)
        .adosPasscodeVersion("2")
        .loggingEnabled(true)
        .invalidateFingerEnrollment(true)
        .injectionAttackDetection(isInjectionDetectionEnabled)
        .deviceBiometricsTitle("Biometric login for my app")
        .deviceBiometricsSubtitle("Log in using your biometric credentials")
        .deviceBiometricsRegistrationReason("Use your fingerprint to enroll.")
        .deviceBiometricsAuthenticationReason("Use your fingerprint to log in securely.")
        .deviceBiometricsNegativeButtonText("Use another method")
        .build()
    
    return extensions
}
```

#### General SDK Configuration

```kotlin
val extensions = ExtensionBuilder()
    // Enable SDK logging for debugging
    .loggingEnabled(true)
    
    // Enable ADOS (Adaptive Orchestration Service) authenticators
    .adosEnabled(true)
    .adosPasscodeVersion("2")
    .adosRootCertificate("CERTIFICATE_STRING")
    .adosCertificateIdentifier("DEC_ID")
    
    // Enable screen capture during authentication
    .screenCapture(true)
    
    // Enable overlay detection (security feature)
    .overlayDetection(true)
    
    // Allow authenticator choice even when policy has single option
    .alwaysAllowAuthenticatorChoice(true)
    
    // Enable device performance monitoring
    .devicePerformance(true)
    
    // Send initialization params to server
    .initParamsToServer(true)
    
    // Set SDK license
    .license("YOUR_LICENSE_KEY")
    
    .build()
```

#### Face Authenticator Configuration

```kotlin
val extensions = ExtensionBuilder()
    // Recognition settings
    .recognitionThreshold(0.75f)         // Higher = stricter matching (0.0-1.0)
    .recognitionTimeout(30000L)          // 30 seconds timeout
    
    // Quality settings
    .qualityThreshold(70f)               // Minimum quality score (0-100)
    .imageQuality(0.8f)                  // JPEG compression quality
    .imageFormat("JPEG")                 // Output image format
    .eyesOpen(true)                      // Require eyes to be open
    .eyesOpenFavorPerformance(true)      // Prioritize speed over accuracy
    
    // Liveness detection
    .passiveLivenessType("client")       // "client", "server", or "none"
    .passiveLivenessTimeout(15000L)      // 15 seconds for liveness check
    .activeLivenessType("blink")         // "blink" or "none"
    .activeLivenessThreshold(0.7f)       // Blink detection threshold
    .livenessAtEnrollment(true)          // Require liveness during enrollment
    .continuityAtEnrollment(true)        // Ensure same person throughout enrollment
    .livenessSecurityLevel(4)            // 2=LOW, 3=MEDIUM, 4=HIGH, 5=HIGHEST
    .livenessStartDelay(500L)            // Delay before starting liveness (ms)
    .minServerResolution(480)            // Minimum resolution for server liveness
    
    // Injection Attack Detection (IFP - Injection/Face Prevention)
    .injectionAttackDetection(true)      // Enable injection attack detection
    .requireInjectionDetectionPayload(true) // Require IFP payload in response
    
    // Data management
    .saveEnrollmentData(true)            // Save enrollment data for debugging
    .faceLicense("FACE_LICENSE_KEY")     // Face module license
    
    .build()
```

**Face Liveness Types Explained:**
- **Passive Liveness (`passiveLivenessType`)**:
  - `"client"`: Liveness detection performed on-device
  - `"server"`: Liveness detection performed on server
  - `"none"`: No passive liveness detection
  
- **Active Liveness (`activeLivenessType`)**:
  - `"blink"`: User must blink during capture
  - `"none"`: No active liveness check

**Injection Attack Detection:**
- When enabled, the Face IFP authenticator detects and prevents photo attacks, video replay attacks, and other injection attempts
- Uses advanced algorithms to ensure the user is physically present

#### Fingerprint/Biometric Configuration

```kotlin
val extensions = ExtensionBuilder()
    // Silent registration (no UI prompts)
    .silentFingerprintRegistration(false)
    
    // Invalidate enrollment when new fingerprints are added to device
    .invalidateFingerEnrollment(true)
    
    // Enable access to device biometry
    .accessBiometry(true)
    
    // SDK-managed finger locking (lockout after failed attempts)
    .sdkManagedFingerLocking(true)
    
    // Device biometric prompt customization
    .deviceBiometricsTitle("Biometric Login")
    .deviceBiometricsSubtitle("Use your fingerprint or face")
    .deviceBiometricsRegistrationReason("Enroll your fingerprint for secure access")
    .deviceBiometricsAuthenticationReason("Verify your identity to continue")
    .deviceBiometricsNegativeButtonText("Cancel")
    .deviceBiometricsConfirmationRequired(false) // Require explicit confirmation
    
    .build()
```

**Biometric Prompt Customization:**
These settings control the Android BiometricPrompt dialog shown to users:
- **Title**: Main heading displayed on the prompt
- **Subtitle**: Additional context below the title
- **Registration Reason**: Explanation shown during enrollment
- **Authentication Reason**: Explanation shown during authentication
- **Negative Button**: Text for the cancel/alternative button
- **Confirmation Required**: Whether user must explicitly confirm after biometric success

#### Passcode Authenticator Configuration

```kotlin
val extensions = ExtensionBuilder()
    // Passcode length constraints
    .passcodeMinLength(4)                // Minimum 4 digits/characters
    .passcodeMaxLength(8)                // Maximum 8 digits/characters
    
    // Passcode type
    .passcodeType("NUMERIC")             // "NUMERIC" or "ALPHANUMERIC"
    
    .build()
```

**Passcode Types:**
- `"NUMERIC"`: Numbers only (0-9)
- `"ALPHANUMERIC"`: Letters and numbers

#### Voice Authenticator Configuration

```kotlin
val extensions = ExtensionBuilder()
    // Voice quality validation
    .voiceQuality(true)                  // Enable quality checks
    
    // Text validation
    .voiceTextValidation(true)           // Verify spoken text matches prompt
    
    // Recognition timeout
    .voiceRecognitionTimeout(30000L)     // 30 seconds timeout
    
    // Save enrollment data
    .voiceSaveEnrollmentData(true)       // Save for debugging/analysis
    
    .build()
```

#### Complete Configuration Example

```kotlin
// Production-ready configuration example
fun getProductionExtensions(prefs: SharedPreferences): Bundle {
    return ExtensionBuilder()
        // General SDK settings
        .loggingEnabled(BuildConfig.DEBUG) // Only log in debug builds
        .adosEnabled(true)
        .adosPasscodeVersion("2")
        // Face authenticator with high security
        .qualityThreshold(75f)
        .recognitionThreshold(0.8f)
        .passiveLivenessType("client")
        .livenessAtEnrollment(true)
        .livenessSecurityLevel(4) // HIGH security
        .injectionAttackDetection(true) // Enable IFP
        .eyesOpen(true)
        
        // Fingerprint with custom prompts
        .invalidateFingerEnrollment(true)
        .deviceBiometricsTitle(getString(R.string.biometric_title))
        .deviceBiometricsSubtitle(getString(R.string.biometric_subtitle))
        .deviceBiometricsAuthenticationReason(getString(R.string.auth_reason))
        .deviceBiometricsNegativeButtonText(getString(R.string.use_passcode))
        
        // Passcode constraints
        .passcodeMinLength(6)
        .passcodeMaxLength(12)
        .passcodeType("NUMERIC")
        
        // Voice settings
        .voiceQuality(true)
        .voiceTextValidation(true)
        .voiceRecognitionTimeout(25000L)
        
        .build()
}
```

#### Dynamic Configuration Based on User Preferences

```kotlin
fun getDynamicExtensions(prefs: SharedPreferences): Bundle {
    // Read user preferences
    val highSecurity = prefs.getBoolean("high_security_mode", false)
    val enableInjectionDetection = prefs.getBoolean("injectionDetectionEnabled", true)
    val livenessLevel = prefs.getInt("liveness_level", 3) // MEDIUM by default
    
    val builder = ExtensionBuilder()
        .adosEnabled(true)
        .loggingEnabled(BuildConfig.DEBUG)
        .injectionAttackDetection(enableInjectionDetection)
        .livenessSecurityLevel(livenessLevel)
    
    // Apply stricter settings in high security mode
    if (highSecurity) {
        builder
            .recognitionThreshold(0.85f)
            .qualityThreshold(80f)
            .passiveLivenessType("server")
            .livenessAtEnrollment(true)
            .passcodeMinLength(8)
    } else {
        builder
            .recognitionThreshold(0.75f)
            .qualityThreshold(70f)
            .passiveLivenessType("client")
            .passcodeMinLength(4)
    }
    
    return builder.build()
}
```

#### Per-Operation Extensions

You can also pass extensions directly to individual operations instead of setting them globally:

```kotlin
// Apply extensions only for a specific registration
val registrationExtensions = ExtensionBuilder()
    .qualityThreshold(80f) // Stricter quality for this registration
    .livenessAtEnrollment(true)
    .build()

val params = ParameterBuilder()
    .username(username)
    .sessionId(sessionId)
    .build()
    .apply { putAll(registrationExtensions) } // Merge extensions into params

val response = registration.start(params, chooseAuthListener)
```

#### Legacy Bundle Approach (Not Recommended)

While you can still create extensions using raw Bundle objects, the ExtensionBuilder is strongly recommended:

```kotlin
// Old approach - avoid this
val extensions = Bundle().apply {
    putString("com.daon.sdk.log", "true")
    putString("com.daon.sdk.ados.enabled", "true")
    putString("com.daon.face.quality.threshold", "70")
    // Error-prone: typos, type mismatches, no IDE support
}

// New approach - use this instead
val extensions = ExtensionBuilder()
    .loggingEnabled(true)
    .adosEnabled(true)
    .qualityThreshold(70f)
    .build()
```

**Benefits of ExtensionBuilder:**
- ✅ Type-safe (Float, Boolean, Int instead of String)
- ✅ IDE auto-completion and documentation
- ✅ Compile-time validation
- ✅ No typos in extension keys
- ✅ Fluent, readable API
- ✅ Easy to discover available options

### Transaction Authentication

Authenticate with transaction data:

```kotlin
val params = ParameterBuilder()
    .username(username)
    .sessionId(sessionId)
    .transactionContentType("text/plain")
    .transactionContent("Transfer $100 to Account 123456")
    .confirmationOTP(true)
    .build()

authentication.transactionConfirmationListener = TransactionConfirmationListener { transaction ->
    // Display transaction details
    val content = TransactionUtils.getTransactionContent(context, transaction)
    showTransactionDialog(content)
}

authentication.confirmationOTPListener = ConfirmationOTPListener { otp ->
    // Display OTP
    Toast.makeText(context, "OTP: $otp", Toast.LENGTH_LONG).show()
}

val response = authentication.start(params, chooseAuthListener)
```

### Error Handling

Parse error responses consistently:

```kotlin
when (val response = operation.start(params)) {
    is Failure -> {
        val errorCode = response.errorCode
        val errorMessage = response.errorMessage
        
        Log.e(TAG, "Error $errorCode: $errorMessage")
    }
    is Success -> {
        // Handle success
    }
}
```

## License

The FIDO SDK requires a license that is bound to an application identifier. This license may embed licenses required for specific authenticators (Face, Voice, etc.). Contact Daon Support or Sales to request a license for your application.

## Support

For technical support, questions, or issues:
- Check the `/docs/` directory for detailed documentation
- Review error codes in `/docs/error-codes.md`
- Contact Daon Technical Support
- Refer to the sample code for implementation examples
