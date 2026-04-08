package com.daon.fido.sdk.sample.kt.screens.intro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.FloatingCard
import com.daon.fido.sdk.sample.kt.util.getBitmap
import com.daon.fido.sdk.sample.kt.util.getGroupDescription
import com.daon.fido.sdk.sample.kt.util.getGroupTitle
import com.daon.sdk.xauth.core.Group

/**
 * Displays a list of authenticators for the user to select for authentication. It handles user
 * interactions for selecting an authenticator and navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationChoicesScreen(onNavigateUp: () -> Unit, viewModel: IntroViewModel) {
    // Collect the AuthenticatorState from the ViewModel which includes the policy
    val state = viewModel.state.collectAsState()
    val policy = state.value.loginProcessState.policy
    val groups = policy?.getGroups()

    // Screen layout
    Scaffold(
        topBar = { AppTopAppBar(title = "Select an authenticator") },
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (groups != null) {
            GroupList(groups, onNavigateUp, viewModel, it)
        }
    }

    // Handle back button press
    BackHandler(true) {
        viewModel.setInProgressFalse()
        onNavigateUp()
    }
}

@Composable
fun GroupList(
    groups: Array<Group>,
    onNavigateUp: () -> Unit,
    viewModel: IntroViewModel,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .safeContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(groups) { group -> AuthCard(group, onNavigateUp, viewModel) }
    }
}

@Composable
fun AuthCard(group: Group, onNavigateUp: () -> Unit, viewModel: IntroViewModel) {
    // Card layout for each authenticator item
    FloatingCard(
        onClick = {
            // Update the selected authenticator and navigate back
            viewModel.selectAuthenticatorGroup(group)
            onNavigateUp()
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Display the authenticator icon if available
            Image(
                bitmap = getBitmap(group.getAuthenticator().icon ?: ""),
                contentDescription = " ",
                modifier = Modifier.size(60.dp).padding(end = 16.dp),
                contentScale = ContentScale.Fit,
            )
            // Display the authenticator title and description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getGroupTitle(group),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getGroupDescription(group),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                )
            }
        }
    }
}
