package az.market.app.navigation

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private class FakeSessionRepository : SessionRepository {
        override val session = MutableStateFlow<SessionState>(SessionState.Loading)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun exposesSessionChangesFromRepository() = runTest {
        val repo = FakeSessionRepository()
        val vm = RootViewModel(repo)
        assertEquals(SessionState.Loading, vm.session.value)

        repo.session.value = SessionState.SignedOut
        assertEquals(SessionState.SignedOut, vm.session.value)

        repo.session.value = SessionState.SignedIn("u1", UserRole.ADMIN)
        assertEquals(SessionState.SignedIn("u1", UserRole.ADMIN), vm.session.value)
    }
}
