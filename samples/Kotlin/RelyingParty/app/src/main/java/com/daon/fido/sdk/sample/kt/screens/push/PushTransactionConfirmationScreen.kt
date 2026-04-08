package com.daon.fido.sdk.sample.kt.screens.push

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daon.fido.sdk.sample.kt.screens.transaction.TransactionContentCard
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.sdk.xauth.model.TransactionResult

/**
 * Displays the transaction confirmation screen for push authentication flow. It handles user
 * interactions for confirming or cancelling the transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushTransactionConfirmationScreen(
    onNavigateUp: () -> Unit,
    viewModel: PushAuthenticationViewModel,
) {
    val state by viewModel.state.collectAsState()
    val transactionContent = state.transactionState.transactionContent

    Scaffold(topBar = { AppTopAppBar(title = "Transaction Confirmation") }) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(it)
                    .consumeWindowInsets(it)
                    .safeContentPadding()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TransactionContentCard(
                transactionContent = transactionContent,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PrimaryFloatingButton(
                    text = "Confirm",
                    onClick = {
                        viewModel.clearTransactionData()
                        viewModel.setTransactionConfirmationResult(TransactionResult.Confirmed)
                        onNavigateUp()
                    },
                    modifier = Modifier.weight(1f),
                )
                PrimaryFloatingButton(
                    text = "Cancel",
                    onClick = {
                        viewModel.clearTransactionData()
                        viewModel.setTransactionConfirmationResult(TransactionResult.Cancelled)
                        onNavigateUp()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
