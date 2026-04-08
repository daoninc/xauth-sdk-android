package com.daon.fido.sdk.sample.kt.screens.intro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.FloatingCard
import com.daon.sdk.xauth.model.AccountInfo

/**
 * Displays a list of authenticators for the user to select for authentication. It handles user
 * interactions for selecting an authenticator and navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(onNavigateUp: () -> Unit, viewModel: IntroViewModel) {
    // Collect the UiState from the ViewModel which includes the list of accounts
    val state = viewModel.state.collectAsState()
    val accountList: List<AccountInfo> = state.value.accountSelectionState.accountArray.toList()

    // Screen layout
    Scaffold(topBar = { AppTopAppBar(title = "Choose user account") }) {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .padding(it)
                    .consumeWindowInsets(it)
                    .safeContentPadding()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(accountList) { accountInfo ->
                AccountCard(account = accountInfo, onNavigateUp, viewModel)
            }
        }
    }

    // Handle back button press
    BackHandler(true) {
        viewModel.clearAccountList()
        onNavigateUp()
    }
}

@Composable
fun AccountCard(account: AccountInfo, onNavigateUp: () -> Unit, viewModel: IntroViewModel) {
    FloatingCard(
        onClick = {
            // Update the selected account and navigate back
            viewModel.selectAccount(account)
            onNavigateUp()
        }
    ) {
        Text(
            text = account.userName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
