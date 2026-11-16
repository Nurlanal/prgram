package az.market.app.feature.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
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
import az.market.app.core.common.AzPhone
import az.market.app.core.designsystem.theme.LocalMarketColors
import az.market.app.feature.auth.AuthScaffold
import az.market.app.feature.auth.AuthTextField
import az.market.app.feature.auth.ErrorBanner
import az.market.app.feature.auth.InfoText
import az.market.app.feature.auth.LoadingButton
import az.market.app.feature.auth.PasswordTextField

@Composable
fun CreateCourierScreen(onBack: () -> Unit, viewModel: CreateCourierViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(title = stringResource(R.string.courier_create_title), onBack = onBack) {
        InfoText(stringResource(R.string.courier_create_subtitle))
        state.createdPhone?.let {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CheckCircle, null, tint = LocalMarketColors.current.success) },
                headlineContent = { Text(stringResource(R.string.courier_created, AzPhone.format(it))) },
                modifier = Modifier.testTag("courier_created"),
            )
        }
        AuthTextField(state.firstName, { viewModel.onChange("first", it) }, R.string.field_first_name, "courier_first", state.errors["first"])
        AuthTextField(state.lastName, { viewModel.onChange("last", it) }, R.string.field_last_name, "courier_last", state.errors["last"])
        AuthTextField(state.phone, { viewModel.onChange("phone", it) }, R.string.field_phone, "courier_phone", state.errors["phone"],
            placeholder = R.string.field_phone_hint, keyboardType = KeyboardType.Phone, leadingIcon = Icons.Outlined.Phone)
        PasswordTextField(state.password, { viewModel.onChange("password", it) }, R.string.field_password, "courier_password",
            state.errors["password"], supporting = R.string.password_hint)
        AuthTextField(state.vehicle, { viewModel.onChange("vehicle", it) }, R.string.field_vehicle, "courier_vehicle",
            leadingIcon = Icons.AutoMirrored.Outlined.DirectionsBike, imeAction = ImeAction.Done, onImeDone = viewModel::submit)
        ErrorBanner(state.error)
        LoadingButton(stringResource(R.string.courier_create_action), state.loading, "courier_submit", viewModel::submit)
    }
}
