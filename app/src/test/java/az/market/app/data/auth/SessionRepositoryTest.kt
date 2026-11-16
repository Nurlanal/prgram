package az.market.app.data.auth

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.session.SupabaseSessionRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private class FakeGateway : AuthGateway {
        override val status = MutableStateFlow<AuthStatus>(AuthStatus.Initializing)
        var account: Account? = null
        var fail = false
        var signOuts = 0
        override suspend fun fetchAccount(): Account? = if (fail) throw IOException("offline") else account
        override suspend fun signOut() { signOuts++; status.value = AuthStatus.SignedOut }
    }

    private fun account(role: UserRole?, active: Boolean = true) = Account("u1", role, active, "Ad", "Soyad", "+994501234567", null)
    private fun signedIn(aal2: Boolean = false, factor: Boolean = false, refresh: Boolean = false) =
        AuthStatus.SignedIn("u1", aal2, factor, refresh)

    private fun scenario(block: suspend (FakeGateway, AuthFlowState, () -> SessionState) -> Unit) = runTest {
        val gateway = FakeGateway()
        val flow = AuthFlowState()
        val repo = SupabaseSessionRepository(gateway, flow)
        var latest: SessionState = SessionState.Loading
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repo.session.collect { latest = it } }
        block(gateway, flow) { latest }
        job.cancel()
    }

    @Test fun startsLoadingThenSignedOut() = scenario { g, _, state ->
        assertEquals(SessionState.Loading, state())
        g.status.value = AuthStatus.SignedOut
        assertEquals(SessionState.SignedOut, state())
    }

    @Test fun customerAndCourierRolesComeFromTheServer() = scenario { g, _, state ->
        g.account = account(UserRole.CUSTOMER)
        g.status.value = signedIn()
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())

        g.account = account(UserRole.COURIER)
        g.status.value = signedIn(refresh = true)
        assertEquals(SessionState.SignedIn("u1", UserRole.COURIER), state())
    }

    @Test fun adminWithoutMfaMustEnterCode() = scenario { g, _, state ->
        g.account = account(UserRole.ADMIN)
        g.status.value = signedIn(aal2 = false, factor = false)
        assertEquals(SessionState.AdminMfaRequired("u1", hasVerifiedFactor = false), state())
        g.status.value = signedIn(aal2 = false, factor = true)
        assertEquals(SessionState.AdminMfaRequired("u1", hasVerifiedFactor = true), state())
        g.status.value = signedIn(aal2 = true, factor = true)
        assertEquals(SessionState.SignedIn("u1", UserRole.ADMIN), state())
    }

    @Test fun mfaSessionDoesNotMakeACustomerAnAdmin() = scenario { g, _, state ->
        g.account = account(UserRole.CUSTOMER)
        g.status.value = signedIn(aal2 = true, factor = true)
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())
    }

    @Test fun blockedAccount() = scenario { g, _, state ->
        g.account = account(UserRole.COURIER, active = false)
        g.status.value = signedIn()
        assertEquals(SessionState.Blocked("u1"), state())
    }

    @Test fun missingAccountOrUnknownRoleIsSignedOut() = scenario { g, _, state ->
        g.account = account(role = null)
        g.status.value = signedIn()
        assertEquals(SessionState.SignedOut, state())
        assertEquals(1, g.signOuts)
    }

    @Test fun passwordRecoveryTakesPrecedenceOverTheShell() = scenario { g, flow, state ->
        g.account = account(UserRole.CUSTOMER)
        flow.setPasswordRecovery(true)
        g.status.value = signedIn()
        assertEquals(SessionState.PasswordRecovery("u1"), state())
        flow.setPasswordRecovery(false)
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())
    }

    @Test fun adminRecoveryAsksForMfaFirst() = scenario { g, flow, state ->
        g.account = account(UserRole.ADMIN)
        flow.setPasswordRecovery(true)
        g.status.value = signedIn(aal2 = false, factor = true)
        assertTrue(state() is SessionState.AdminMfaRequired)
        g.status.value = signedIn(aal2 = true, factor = true)
        assertEquals(SessionState.PasswordRecovery("u1"), state())
    }

    @Test fun offlineAtStartShowsRetryAndRecovers() = scenario { g, flow, state ->
        g.fail = true
        g.status.value = signedIn()
        assertEquals(SessionState.AccountUnavailable, state())
        g.fail = false
        g.account = account(UserRole.CUSTOMER)
        flow.reloadAccount()
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())
    }

    @Test fun offlineTokenRefreshKeepsTheUserSignedIn() = scenario { g, _, state ->
        g.account = account(UserRole.CUSTOMER)
        g.status.value = signedIn()
        g.fail = true
        g.status.value = signedIn(refresh = true)
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())
        g.status.value = AuthStatus.RefreshFailed
        assertEquals(SessionState.SignedIn("u1", UserRole.CUSTOMER), state())
    }

    @Test fun signOutClearsRecoveryFlag() = scenario { g, flow, state ->
        flow.setPasswordRecovery(true)
        g.status.value = AuthStatus.SignedOut
        assertEquals(SessionState.SignedOut, state())
        assertEquals(false, flow.passwordRecovery.value)
    }

    @Test fun noBackendMeansSignedOut() = runTest {
        var latest: SessionState? = null
        SupabaseSessionRepository(null, AuthFlowState()).session.collect { latest = it }
        assertEquals(SessionState.SignedOut, latest)
    }
}
