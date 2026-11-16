package az.market.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.R

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, viewModel: ForgotPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inCodeStep = state.step == ForgotStep.CODE
    BackHandler(enabled = inCodeStep) { viewModel.back() }

    AuthScaffold(title = stringResource(R.string.forgot_title), onBack = if (inCodeStep) viewModel::back else onBack) {
        if (!inCodeStep) {
            InfoText(stringResource(if (state.byEmail) R.string.forgot_subtitle_email else R.string.forgot_subtitle_phone))
            if (state.byEmail) {
                AuthTextField(state.email, viewModel::onEmailChange, R.string.field_email, "forgot_input", state.inputError,
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, onImeDone = viewModel::requestCode,
                    leadingIcon = Icons.Outlined.Email)
            } else {
                AuthTextField(state.phone, viewModel::onPhoneChange, R.string.field_phone, "forgot_input", state.inputError,
                    placeholder = R.string.field_phone_hint, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done,
                    onImeDone = viewModel::requestCode, leadingIcon = Icons.Outlined.Phone)
            }
            ErrorBanner(state.error?.message)
            LoadingButton(stringResource(R.string.action_send_code), state.loading, "forgot_send", viewModel::requestCode)
        } else {
            InfoText(stringResource(R.string.forgot_code_sent_generic))
            AuthTextField(state.code, viewModel::onCodeChange,
                if (state.byEmail) R.string.field_email_code else R.string.field_code, "forgot_code", state.codeError,
                keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onImeDone = viewModel::verifyCode,
                leadingIcon = Icons.Outlined.Sms)
            ErrorBanner(state.error?.message)
            LoadingButton(stringResource(R.string.action_confirm), state.loading, "forgot_verify", viewModel::verifyCode)
            ResendCodeButton(state.resendSeconds, viewModel::requestCode)
        }
    }
}

/** Shown by the root navigation while SessionState is PasswordRecovery. */
@Composable
fun NewPasswordScreen(viewModel: NewPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { viewModel.cancel() }
    AuthScaffold(title = stringResource(R.string.new_password_title), onBack = viewModel::cancel) {
        InfoText(stringResource(R.string.new_password_subtitle))
        PasswordTextField(state.password, viewModel::onPasswordChange, R.string.field_new_password, "new_password",
            state.passwordError, supporting = R.string.password_hint)
        PasswordTextField(state.confirm, viewModel::onConfirmChange, R.string.field_password_confirm, "new_password_confirm",
            state.confirmError, imeAction = ImeAction.Done, onImeDone = viewModel::submit)
        ErrorBanner(state.error?.message)
        LoadingButton(stringResource(R.string.action_save), state.loading, "new_password_save", viewModel::submit)
        TextButton(onClick = viewModel::cancel, modifier = Modifier.testTag("new_password_cancel")) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
