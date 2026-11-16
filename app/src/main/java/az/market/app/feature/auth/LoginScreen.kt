package az.market.app.feature.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.R

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: (admin: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(title = stringResource(R.string.login_title), onBack = onBack) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LoginMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { viewModel.onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, LoginMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("login_mode_${mode.name.lowercase()}"),
                ) {
                    Text(stringResource(if (mode == LoginMode.PHONE) R.string.login_tab_phone else R.string.login_tab_admin))
                }
            }
        }
        InfoText(
            stringResource(if (state.mode == LoginMode.PHONE) R.string.login_subtitle_phone else R.string.login_subtitle_admin),
        )
        if (state.mode == LoginMode.PHONE) {
            AuthTextField(
                value = state.phone, onValueChange = viewModel::onPhoneChange, label = R.string.field_phone,
                placeholder = R.string.field_phone_hint, tag = "login_phone", error = state.phoneError,
                keyboardType = KeyboardType.Phone, leadingIcon = Icons.Outlined.Phone,
            )
        } else {
            AuthTextField(
                value = state.email, onValueChange = viewModel::onEmailChange, label = R.string.field_email,
                tag = "login_email", error = state.emailError, keyboardType = KeyboardType.Email,
                leadingIcon = Icons.Outlined.Email,
            )
        }
        PasswordTextField(
            value = state.password, onValueChange = viewModel::onPasswordChange, label = R.string.field_password,
            tag = "login_password", error = state.passwordError, imeAction = ImeAction.Done, onImeDone = viewModel::submit,
        )
        ErrorBanner(state.error?.message)
        LoadingButton(stringResource(R.string.action_sign_in), state.loading, "login_submit", viewModel::submit)
        TextButton(onClick = { onForgotPassword(state.mode == LoginMode.ADMIN) }, modifier = Modifier.testTag("login_forgot")) {
            Text(stringResource(R.string.forgot_password))
        }
        if (state.mode == LoginMode.PHONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.no_account), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRegister, modifier = Modifier.testTag("login_register")) {
                    Text(stringResource(R.string.action_register))
                }
            }
            Text(
                stringResource(R.string.courier_login_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
