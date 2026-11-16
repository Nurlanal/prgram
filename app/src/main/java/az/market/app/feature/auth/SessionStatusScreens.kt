package az.market.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import az.market.app.R
import az.market.app.core.designsystem.component.FeaturePlaceholderIcon
import az.market.app.core.designsystem.theme.Spacing

@Composable
fun BlockedScreen(actions: SessionActionsViewModel = hiltViewModel()) {
    StatusScreen(Icons.Outlined.Block, R.string.blocked_title, R.string.blocked_body) {
        OutlinedButton(onClick = actions::signOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_sign_out)) }
    }
}

@Composable
fun AccountUnavailableScreen(actions: SessionActionsViewModel = hiltViewModel()) {
    StatusScreen(Icons.Outlined.CloudOff, R.string.unavailable_title, R.string.unavailable_body) {
        LoadingButton(stringResource(R.string.action_retry), loading = false, tag = "account_retry", onClick = actions::retryLoadingAccount)
        OutlinedButton(onClick = actions::signOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_sign_out)) }
    }
}

@Composable
private fun StatusScreen(icon: ImageVector, title: Int, body: Int, buttons: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            FeaturePlaceholderIcon(icon)
            Text(stringResource(title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            InfoText(stringResource(body))
            buttons()
        }
    }
}
