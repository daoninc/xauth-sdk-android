# xAuth Android (Kotlin) SDK

The xAuth Android SDK provides a Kotlin APIs that allow a developer to create applications that authenticate users using FIDO authenticators.

The FIDO (Fast Identity Online) specification is a set of protocols and standards developed to provide a secure and easy-to-use method for user authentication.

## FIDO UAF
FIDO UAF (Universal Authentication Framework) is a set of specifications developed by the FIDO Alliance that provides a secure and easy-to-use authentication framework for online services. It is designed to replace traditional password-based authentication methods with more secure and user-friendly alternatives.

FIDO UAF works by using public key cryptography to authenticate users. When a user wants to authenticate themselves to an online service, their device generates a public-private key pair. The private key is stored securely on the device, while the public key is registered with the online service. When the user wants to authenticate themselves, they simply need to provide a signature using their private key, which can be verified by the online service using the registered public key.

One of the key benefits of FIDO UAF is that it is resistant to phishing attacks, since the user's private key is never transmitted over the network. This means that even if an attacker is able to intercept the authentication request, they will not be able to use the user's private key to authenticate themselves to the service.

FIDO UAF also supports a wide range of authentication methods, including biometrics, PINs, and Passkeys. This allows users to choose the authentication method that works best for them, while still maintaining a high level of security.

## License
The SDK requires a license that is bound to an application identifier. This license may in turn embed licenses that are required for specific authenticators. Contact Daon Support or Sales to request a license.

## Samples

The demo sample includes the following:

- **RelyingParty**: A reference sample Relying Party application.



## SDK repository
In your project-level build.gradle file, make sure to include the Daon Maven repository in your buildscript or allprojects sections.

[Daon Maven repository](https://github.com/daoninc/sdk-packages/blob/main/README.md)

## API

Add the following dependencies to the build.gradle file:

```gradle

TBD

// CameraX core library
// Used by the face capture library

def camerax_version = "1.5.1"

implementation "androidx.camera:camera-core:${camerax_version}"
implementation "androidx.camera:camera-camera2:${camerax_version}"
implementation "androidx.camera:camera-lifecycle:${camerax_version}"
implementation "androidx.camera:camera-video:${camerax_version}"
implementation "androidx.camera:camera-view:${camerax_version}"
implementation "androidx.camera:camera-extensions:${camerax_version}"
```

The face capture library is part of the [xProof Face SDK](https://github.com/daoninc/face-sdk-android) and is used to capture images of the user's face for authentication. 

See included samples for details and additional information.



### Register 

Register a new authenticator with the FIDO server.

```kotlin
val fido = IXUAF(context, service, extensions)​
val registration = fido.registration()​

when (val result = registration.start(params, chooseAuthenticatorListener)) {​
    is Success -> {​
        handleRegistrationSuccess() ​
    }​

    is Failure -> {​
        handleRegistrationFailure() ​
    }​
}
```


### Authenticate

Authenticate the user with the FIDO server. If a username is provided, a step-up authentication is performed.

```kotlin
val fido = IXUAF(context, service, extensions)​
val authentication = fido.authentication()​

when (val result = authentication.start(params, chooseAuthenticatorListener)) {​
    is Success -> {​
        handleAuthenticationSuccess()​
    }​

    is Failure -> {​
        handleAuthenticationFailure()​
    }​
}
```

See included samples and [xAuth Android SDK Documentation](https://developer.identityx-cloud.com/client/xauth/android/) for details and additional information.



