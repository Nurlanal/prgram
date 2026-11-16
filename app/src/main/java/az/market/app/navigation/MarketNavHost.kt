package az.market.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import az.market.app.BuildConfig
import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.store.BackendStatus
import az.market.app.feature.admin.AdminShell
import az.market.app.feature.auth.AccountUnavailableScreen
import az.market.app.feature.auth.AdminMfaScreen
import az.market.app.feature.auth.BlockedScreen
import az.market.app.feature.auth.ForgotPasswordScreen
import az.market.app.feature.auth.LoginScreen
import az.market.app.feature.auth.NewPasswordScreen
import az.market.app.feature.auth.RegisterScreen
import az.market.app.feature.auth.SessionActionsViewModel
import az.market.app.feature.auth.WelcomeScreen
import az.market.app.feature.courier.CourierShell
import az.market.app.feature.customer.CustomerShell

/** Identity of a session "kind": any change re-creates the NavHost so back stacks never leak across roles. */
internal fun sessionKey(session: SessionState): String = when (session) {
    is SessionState.SignedIn -> "in:${session.userId}:${session.role}"
    is SessionState.AdminMfaRequired -> "mfa:${session.userId}:${session.hasVerifiedFactor}"
    is SessionState.PasswordRecovery -> "recovery:${session.userId}"
    is SessionState.Blocked -> "blocked:${session.userId}"
    SessionState.AccountUnavailable -> "unavailable"
    SessionState.Loading, SessionState.SignedOut -> "signed_out"
}

@Composable
fun MarketNavHost(
    session: SessionState,
    backendStatus: BackendStatus,
    onRetryBackend: () -> Unit,
) {
    key(sessionKey(session)) {
        val navController = rememberNavController()
        val signedIn = session is SessionState.SignedIn
        NavHost(
            navController = navController,
            startDestination = startDestinationFor(session),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            // ---- signed out
            composable<WelcomeRoute> {
                WelcomeScreen(
                    onSignIn = { navController.navigate(LoginRoute) },
                    onRegister = { navController.navigate(RegisterRoute) },
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
            composable<LoginRoute> {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onRegister = { navController.navigate(RegisterRoute) { popUpTo(WelcomeRoute) } },
                    onForgotPassword = { admin -> navController.navigate(ForgotPasswordRoute(admin)) },
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    onBack = { navController.popBackStack() },
                    onLogin = { navController.navigate(LoginRoute) { popUpTo(WelcomeRoute) } },
                )
            }
            composable<ForgotPasswordRoute> { ForgotPasswordScreen(onBack = { navController.popBackStack() }) }

            // ---- session-driven
            composable<AdminMfaRoute> { AdminMfaScreen() }
            composable<NewPasswordRoute> { NewPasswordScreen() }
            composable<BlockedRoute> { BlockedScreen() }
            composable<AccountUnavailableRoute> { AccountUnavailableScreen() }

            // ---- role shells
            composable<CustomerShellRoute> {
                val actions: SessionActionsViewModel = hiltViewModel()
                CustomerShell(onSignOut = if (signedIn) actions::signOut else ({ navController.popBackStack(); Unit }))
            }
            composable<AdminShellRoute> {
                val actions: SessionActionsViewModel = hiltViewModel()
                AdminShell(
                    onExit = if (signedIn) null else ({ navController.popBackStack() }),
                    onSignOut = if (signedIn) actions::signOut else null,
                )
            }
            composable<CourierShellRoute> {
                val actions: SessionActionsViewModel = hiltViewModel()
                CourierShell(
                    onExit = if (signedIn) null else ({ navController.popBackStack() }),
                    onSignOut = if (signedIn) actions::signOut else null,
                )
            }
        }
    }
}
