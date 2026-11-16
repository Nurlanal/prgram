package az.market.app.navigation

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.session.SessionRepository
import az.market.app.data.store.BackendStatus
import az.market.app.data.store.StoreSettings
import az.market.app.data.store.StoreSettingsRepository
import kotlinx.coroutines.flow.Flow
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

    private class FakeStoreRepository : StoreSettingsRepository {
        val settings = MutableStateFlow(StoreSettings())
        var status: BackendStatus = BackendStatus.NotConfigured
        override fun observeSettings(): Flow<StoreSettings> = settings
        override suspend fun checkBackend(): BackendStatus = status
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun exposesSessionChangesFromRepository() = runTest {
        val repo = FakeSessionRepository()
        val vm = RootViewModel(repo, FakeStoreRepository())
        assertEquals(SessionState.Loading, vm.session.value)

        repo.session.value = SessionState.SignedOut
        assertEquals(SessionState.SignedOut, vm.session.value)

        repo.session.value = SessionState.SignedIn("u1", UserRole.ADMIN)
        assertEquals(SessionState.SignedIn("u1", UserRole.ADMIN), vm.session.value)
    }

    @Test fun storeSettingsAreLiveAndBackendStatusIsChecked() = runTest {
        val store = FakeStoreRepository().apply { status = BackendStatus.Connected("Market", 12, 40) }
        val vm = RootViewModel(FakeSessionRepository(), store)
        assertEquals(BackendStatus.Connected("Market", 12, 40), vm.backendStatus.value)

        // e.g. admin changes the brand color → Realtime update reaches the UI state
        store.settings.value = StoreSettings(storeName = "Kənd Market", primaryColorArgb = 0xFFC62828)
        assertEquals("Kənd Market", vm.storeSettings.value.storeName)
        assertEquals(0xFFC62828, vm.storeSettings.value.primaryColorArgb)

        store.status = BackendStatus.Unreachable("timeout")
        vm.checkBackend()
        assertEquals(BackendStatus.Unreachable("timeout"), vm.backendStatus.value)
    }
}
