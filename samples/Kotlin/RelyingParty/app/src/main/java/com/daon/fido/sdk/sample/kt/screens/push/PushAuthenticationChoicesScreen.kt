package com.daon.fido.sdk.sample.kt.screens.push

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * Displays a list of authenticators for the user to select for push authentication. It handles user
 * interactions for selecting an authenticator and navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushAuthenticationChoicesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PushAuthenticationViewModel,
) {
    val state by viewModel.state.collectAsState()
    val policy = state.authenticationState.policy
    val groups = policy?.getGroups()

    Scaffold(topBar = { AppTopAppBar(title = "Select an authenticator") }) {
        if (groups != null) {
            PushGroupList(groups, onNavigateUp, viewModel, it)
        }
    }

    BackHandler(true) {
        viewModel.setInProgressFalse()
        onNavigateUp()
    }
}

@Composable
private fun PushGroupList(
    groups: Array<Group>,
    onNavigateUp: () -> Unit,
    viewModel: PushAuthenticationViewModel,
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
        items(groups) { group ->
            PushAuthCard(group = group, onNavigateUp = onNavigateUp, viewModel = viewModel)
        }
    }
}

@Composable
private fun PushAuthCard(
    group: Group,
    onNavigateUp: () -> Unit,
    viewModel: PushAuthenticationViewModel,
) {
    FloatingCard(
        onClick = {
            viewModel.selectAuthenticatorGroup(group)
            onNavigateUp()
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = getBitmap(group.getAuthenticator().icon ?: ""),
                contentDescription = " ",
                modifier = Modifier.size(60.dp).padding(16.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getGroupTitle(group),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
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
