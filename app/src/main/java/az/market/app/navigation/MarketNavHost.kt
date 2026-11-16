package az.market.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import az.market.app.BuildConfig
import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.store.BackendStatus
import az.market.app.feature.admin.AdminShell
import az.market.app.feature.auth.LoginPlaceholderScreen
import az.market.app.feature.auth.WelcomeScreen
import az.market.app.feature.courier.CourierShell
import az.market.app.feature.customer.CustomerShell

/**
 * Root navigation. The NavHost is re-created whenever the session "kind" changes
 * (signed out ↔ signed in as role X), so the back stack of one role can never leak into another.
 */
@Composable
fun MarketNavHost(
    session: SessionState,
    backendStatus: BackendStatus,
    onRetryBackend: () -> Unit,
) {
    val sessionKey = when (session) {
        is SessionState.SignedIn -> "${session.userId}:${session.role}"
        else -> "signed_out"
    }
    key(sessionKey) {
        val navController = rememberNavController()
        val signedIn = session is SessionState.SignedIn
        NavHost(
            navController = navController,
            startDestination = startDestinationFor(session),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable<WelcomeRoute> {
                WelcomeScreen(
                    onSignIn = { navController.navigate(LoginRoute) },
                    onRegister = { navController.navigate(LoginRoute) },
                    showDevRolePreview = BuildConfig.DEBUG,
                    backendStatus = backendStatus,
                    onRetryBackend = onRetryBackend,
                    onDevPreviewRole = { role ->
                        navController.navigate(
                            when (role) {
                                UserRole.CUSTOMER -> CustomerShellRoute
                                UserRole.ADMIN -> AdminShellRoute
                                UserRole.COURIER -> CourierShellRoute
                            },
                        )
                    },
                )
            }
            composable<LoginRoute> { LoginPlaceholderScreen(onBack = { navController.popBackStack() }) }
            composable<CustomerShellRoute> { CustomerShell() }
            composable<AdminShellRoute> {
                AdminShell(onExit = if (signedIn) null else ({ navController.popBackStack() }))
            }
            composable<CourierShellRoute> {
                CourierShell(onExit = if (signedIn) null else ({ navController.popBackStack() }))
            }
        }
    }
}
