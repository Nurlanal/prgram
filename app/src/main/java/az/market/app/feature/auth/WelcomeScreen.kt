package az.market.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import az.market.app.R
import az.market.app.core.designsystem.component.MarketLogoMark
import az.market.app.core.designsystem.theme.MarketTheme
import az.market.app.core.designsystem.theme.Spacing
import az.market.app.core.model.UserRole

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    showDevRolePreview: Boolean,
    onDevPreviewRole: (UserRole) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.82f))),
                )
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MarketLogoMark(size = 72.dp)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeatureRow(Icons.Outlined.LocalShipping, stringResource(R.string.welcome_feature_delivery))
            FeatureRow(Icons.Outlined.CardGiftcard, stringResource(R.string.welcome_feature_bonus))
            FeatureRow(Icons.Outlined.LocalOffer, stringResource(R.string.welcome_feature_deals))

            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_sign_in)) }
            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_register)) }

            if (showDevRolePreview) {
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                Text(stringResource(R.string.dev_preview_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.dev_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { onDevPreviewRole(UserRole.CUSTOMER) }) { Text(stringResource(R.string.role_customer)) }
                    TextButton(onClick = { onDevPreviewRole(UserRole.ADMIN) }) { Text(stringResource(R.string.role_admin)) }
                    TextButton(onClick = { onDevPreviewRole(UserRole.COURIER) }) { Text(stringResource(R.string.role_courier)) }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    MarketTheme { WelcomeScreen({}, {}, showDevRolePreview = true, onDevPreviewRole = {}) }
}

