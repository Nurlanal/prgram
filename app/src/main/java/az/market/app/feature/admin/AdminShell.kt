package az.market.app.feature.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import az.market.app.navigation.AdminCreateCourierRoute
import az.market.app.navigation.AdminHomeRoute
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import az.market.app.R
import az.market.app.core.designsystem.theme.Spacing

private data class AdminModule(@param:StringRes val title: Int, val icon: ImageVector, val stage: Int, val opensCourierForm: Boolean = false)

private val adminModules = listOf(
    AdminModule(R.string.admin_dashboard, Icons.Outlined.Dashboard, 9),
    AdminModule(R.string.admin_products, Icons.Outlined.Inventory2, 9),
    AdminModule(R.string.admin_categories, Icons.Outlined.Category, 9),
    AdminModule(R.string.admin_orders, Icons.AutoMirrored.Outlined.ReceiptLong, 9),
    AdminModule(R.string.admin_customers, Icons.Outlined.People, 9),
    AdminModule(R.string.admin_couriers, Icons.Outlined.DeliveryDining, 10, opensCourierForm = true),
    AdminModule(R.string.admin_promotions, Icons.Outlined.LocalOffer, 12),
    AdminModule(R.string.admin_banners, Icons.Outlined.Image, 9),
    AdminModule(R.string.admin_bonus, Icons.Outlined.CardGiftcard, 11),
    AdminModule(R.string.admin_delivery, Icons.Outlined.Map, 8),
    AdminModule(R.string.admin_notifications, Icons.Outlined.Campaign, 12),
    AdminModule(R.string.admin_reports, Icons.Outlined.Assessment, 9),
    AdminModule(R.string.admin_settings, Icons.Outlined.Settings, 9),
    AdminModule(R.string.admin_audit, Icons.Outlined.History, 9),
)

/**
 * Admin panel shell (reachable only with role = admin AND an MFA session). Module cards show the stage that
 * implements them; "Kuryerlər" already opens the courier account form (Stage 3).
 */
@Composable
fun AdminShell(onExit: (() -> Unit)?, onSignOut: (() -> Unit)? = null) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = AdminHomeRoute) {
        composable<AdminHomeRoute> {
            AdminHome(onExit, onSignOut, onCreateCourier = { nav.navigate(AdminCreateCourierRoute) })
        }
        composable<AdminCreateCourierRoute> { CreateCourierScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHome(onExit: (() -> Unit)?, onSignOut: (() -> Unit)?, onCreateCourier: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.admin_title))
                        Text(
                            stringResource(R.string.admin_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(adminModules) { module ->
                val onClick = if (module.opensCourierForm) onCreateCourier else null
                Card(
                    onClick = { onClick?.invoke() },
                    enabled = onClick != null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (onClick != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("admin_module_${module.stage}_${module.title}"),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(module.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(module.title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (module.opensCourierForm) stringResource(R.string.courier_create_title)
                            else stringResource(R.string.stage_badge, module.stage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
