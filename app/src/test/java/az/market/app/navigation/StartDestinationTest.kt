package az.market.app.navigation

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    @Test fun signedOutUsersSeeWelcome() {
        assertEquals(WelcomeRoute, startDestinationFor(SessionState.SignedOut))
        assertEquals(WelcomeRoute, startDestinationFor(SessionState.Loading))
    }

    @Test fun eachRoleOpensItsOwnInterface() {
        assertEquals(CustomerShellRoute, startDestinationFor(SessionState.SignedIn("u1", UserRole.CUSTOMER)))
        assertEquals(AdminShellRoute, startDestinationFor(SessionState.SignedIn("u2", UserRole.ADMIN)))
        assertEquals(CourierShellRoute, startDestinationFor(SessionState.SignedIn("u3", UserRole.COURIER)))
    }
}
