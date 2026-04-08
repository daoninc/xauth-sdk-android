package com.daon.fido.sdk.sample.kt.screens.transaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daon.fido.sdk.sample.kt.screens.home.HomeViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.FloatingContentCard
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.sdk.xauth.model.TransactionContent
import com.daon.sdk.xauth.model.TransactionResult

/**
 * Displays the transaction confirmation screen. It handles user interactions for confirming or
 * cancelling the transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionConfirmationScreen(onNavigateUp: () -> Unit, viewModel: HomeViewModel) {

    val state by viewModel.state.collectAsState()
    val transactionContent: TransactionContent? = state.transactionState.transactionContent

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
                    onClick = { confirmTransaction(onNavigateUp, viewModel) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryFloatingButton(
                    text = "Cancel",
                    onClick = { cancelTransaction(onNavigateUp, viewModel) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun TransactionContentCard(transactionContent: TransactionContent?, modifier: Modifier = Modifier) {
    FloatingContentCard(modifier = modifier) {
        when (transactionContent) {
            is TransactionContent.Text -> {
                Text(
                    text = transactionContent.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is TransactionContent.Image -> {
                Image(
                    bitmap = transactionContent.image.asImageBitmap(),
                    contentDescription = "Transaction Confirmation Image",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is TransactionContent.Error -> {
                Text(
                    text = transactionContent.errorMessage,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            null -> {
                Text(
                    text = "No transaction content available",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

fun confirmTransaction(onNavigateUp: () -> Unit, viewModel: HomeViewModel) {
    viewModel.clearTransactionData()
    viewModel.setTransactionConfirmationResult(TransactionResult.Confirmed)
    onNavigateUp()
}

fun cancelTransaction(onNavigateUp: () -> Unit, viewModel: HomeViewModel) {
    viewModel.clearTransactionData()
    viewModel.setTransactionConfirmationResult(TransactionResult.Cancelled)
    onNavigateUp()
}
