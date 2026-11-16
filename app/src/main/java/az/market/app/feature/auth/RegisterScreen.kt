package az.market.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
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
import az.market.app.core.common.AzPhone
import az.market.app.core.designsystem.theme.Spacing

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inCodeStep = state.step == RegisterStep.CODE
    BackHandler(enabled = inCodeStep) { viewModel.editNumber() }

    AuthScaffold(
        title = stringResource(if (inCodeStep) R.string.register_code_title else R.string.register_title),
        onBack = if (inCodeStep) viewModel::editNumber else onBack,
    ) {
        if (!inCodeStep) {
            InfoText(stringResource(R.string.register_subtitle))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.weight(1f)) {
                    AuthTextField(state.firstName, { viewModel.onChange(RegisterField.FIRST_NAME, it) }, R.string.field_first_name,
                        "reg_first_name", state.errors[RegisterField.FIRST_NAME])
                }
                Row(Modifier.weight(1f)) {
                    AuthTextField(state.lastName, { viewModel.onChange(RegisterField.LAST_NAME, it) }, R.string.field_last_name,
                        "reg_last_name", state.errors[RegisterField.LAST_NAME])
                }
            }
            AuthTextField(state.phone, { viewModel.onChange(RegisterField.PHONE, it) }, R.string.field_phone, "reg_phone",
                state.errors[RegisterField.PHONE], placeholder = R.string.field_phone_hint, keyboardType = KeyboardType.Phone,
                leadingIcon = Icons.Outlined.Phone)
            PasswordTextField(state.password, { viewModel.onChange(RegisterField.PASSWORD, it) }, R.string.field_password,
                "reg_password", state.errors[RegisterField.PASSWORD], supporting = R.string.password_hint)
            PasswordTextField(state.confirm, { viewModel.onChange(RegisterField.CONFIRM, it) }, R.string.field_password_confirm,
                "reg_confirm", state.errors[RegisterField.CONFIRM])
            AuthTextField(state.settlement, { viewModel.onChange(RegisterField.SETTLEMENT, it) }, R.string.field_settlement,
                "reg_settlement", state.errors[RegisterField.SETTLEMENT], leadingIcon = Icons.Outlined.Place)
            AuthTextField(state.address, { viewModel.onChange(RegisterField.ADDRESS, it) }, R.string.field_address,
                "reg_address", state.errors[RegisterField.ADDRESS], leadingIcon = Icons.Outlined.Home,
                imeAction = ImeAction.Done, onImeDone = viewModel::submitForm)
            ErrorBanner(state.error?.message)
            LoadingButton(stringResource(R.string.action_continue), state.loading, "reg_submit", viewModel::submitForm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.have_account), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onLogin) { Text(stringResource(R.string.action_sign_in)) }
            }
        } else {
            InfoText(stringResource(R.string.register_code_sent, AzPhone.format(state.phoneE164.orEmpty())))
            AuthTextField(state.code, { viewModel.onChange(RegisterField.CODE, it) }, R.string.field_code, "reg_code",
                state.errors[RegisterField.CODE], keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done,
                onImeDone = viewModel::verifyCode, leadingIcon = Icons.Outlined.Sms)
            ErrorBanner(state.error?.message)
            if (state.codeResent && state.error == null) InfoText(stringResource(R.string.code_resent))
            LoadingButton(stringResource(R.string.action_confirm), state.loading, "reg_verify", viewModel::verifyCode)
            ResendCodeButton(state.resendSeconds, viewModel::resendCode)
            TextButton(onClick = viewModel::editNumber) { Text(stringResource(R.string.change_number)) }
        }
    }
}
