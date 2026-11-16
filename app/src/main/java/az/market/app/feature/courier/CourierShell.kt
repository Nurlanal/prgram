package az.market.app.feature.courier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import az.market.app.R
import az.market.app.core.designsystem.component.FeaturePlaceholder

/** Courier panel shell; order lists are implemented in Stage 10. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierShell(onExit: (() -> Unit)?, onSignOut: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.courier_new, R.string.courier_assigned, R.string.courier_history)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.courier_title)) },
                navigationIcon = {
                    if (onExit != null) {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (onSignOut != null) {
                        IconButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out")) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.action_sign_out))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeaturePlaceholder(
                    icon = Icons.Outlined.DeliveryDining,
                    title = stringResource(tabs[tab]),
                    description = stringResource(R.string.courier_placeholder_desc),
                    stage = 10,
                )
            }
        }
    }
}
