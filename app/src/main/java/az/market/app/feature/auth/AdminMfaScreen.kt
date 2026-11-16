package az.market.app.feature.auth

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.foundation.layout.padding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.R
import az.market.app.core.designsystem.component.QrCodeImage
import az.market.app.core.designsystem.theme.Spacing

/** Admin second factor: enrol a TOTP authenticator the first time, then enter its code on every sign-in. */
@Composable
fun AdminMfaScreen(viewModel: AdminMfaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AuthScaffold(
        title = stringResource(R.string.mfa_title),
        onBack = null,
        actions = { TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.action_sign_out)) } },
    ) {
        if (state.hasVerifiedFactor) {
            InfoText(stringResource(R.string.mfa_verify_subtitle))
        } else {
            InfoText(stringResource(R.string.mfa_enroll_subtitle))
            val enrollment = state.enrollment
            if (enrollment == null) {
                if (!state.loading) {
                    OutlinedButton(onClick = viewModel::startEnrollment, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mfa_start_enroll))
                    }
                }
            } else {
                Text(stringResource(R.string.mfa_enroll_step1), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    QrCodeImage(enrollment.otpAuthUri, size = 200.dp, modifier = Modifier.testTag("mfa_qr"))
                    OutlinedButton(onClick = { openAuthenticator(context, enrollment.otpAuthUri) }) {
                        Icon(Icons.Outlined.Key, null)
                        Text("  " + stringResource(R.string.mfa_open_app))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                        Text(stringResource(R.string.mfa_secret_label), style = MaterialTheme.typography.labelMedium)
                        Text(enrollment.secret.chunked(4).joinToString(" "), fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("mfa_secret"))
                        TextButton(onClick = { copySecret(context, enrollment.secret) }) {
                            Icon(Icons.Outlined.ContentCopy, null)
                            Text("  " + stringResource(R.string.mfa_copy_secret))
                        }
                    }
                }
                Text(stringResource(R.string.mfa_enroll_step2), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (state.hasVerifiedFactor || state.enrollment != null) {
            AuthTextField(state.code, viewModel::onCodeChange, R.string.field_mfa_code, "mfa_code", state.codeError,
                keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onImeDone = viewModel::verify)
            ErrorBanner(state.error?.message)
            LoadingButton(stringResource(R.string.action_confirm), state.loading, "mfa_verify", viewModel::verify)
        } else {
            ErrorBanner(state.error?.message)
        }
    }
}

private fun openAuthenticator(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
    } catch (_: ActivityNotFoundException) {
        copySecret(context, uri.substringAfter("secret=").substringBefore('&'))
    }
}

private fun copySecret(context: Context, secret: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("TOTP", secret))
    Toast.makeText(context, R.string.mfa_secret_copied, Toast.LENGTH_SHORT).show()
}
