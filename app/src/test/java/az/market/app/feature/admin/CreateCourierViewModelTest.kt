package az.market.app.feature.admin

import az.market.app.R
import az.market.app.data.admin.CourierAdminRepository
import az.market.app.data.admin.CourierCreationError
import az.market.app.data.admin.CourierCreationException
import az.market.app.data.admin.NewCourier
import az.market.app.data.remote.BackendConfig
import az.market.app.data.remote.SupabaseProvider
import az.market.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateCourierViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val sent = mutableListOf<NewCourier>()
    private fun vm(fail: CourierCreationError? = null) =
        CreateCourierViewModel(CourierAdminRepository(SupabaseProvider(BackendConfig("", "")) { error("unused") })).apply {
            creator = CourierCreator { c -> if (fail != null) throw CourierCreationException(fail); sent += c; "new-id" }
        }

    private fun CreateCourierViewModel.fill() {
        onChange("first", "Orxan"); onChange("last", "Məmmədov")
        onChange("phone", "070 765 43 21"); onChange("password", "Kuryer2026"); onChange("vehicle", " Moped ")
    }

    @Test fun validatesInput() {
        val vm = vm()
        vm.onChange("password", "123")
        vm.submit()
        assertEquals(setOf("first", "last", "phone", "password"), vm.state.value.errors.keys)
        assertTrue(sent.isEmpty())
    }

    @Test fun createsCourierAndClearsTheFormIncludingPassword() {
        val vm = vm()
        vm.fill(); vm.submit()
        assertEquals(NewCourier("Orxan", "Məmmədov", "+994707654321", "Kuryer2026", "Moped"), sent.single())
        assertEquals("+994707654321", vm.state.value.createdPhone)
        assertEquals("", vm.state.value.password)
    }

    @Test fun mapsServerErrors() {
        val vm = vm(CourierCreationError.PHONE_EXISTS)
        vm.fill(); vm.submit()
        assertEquals(R.string.err_courier_phone_exists, vm.state.value.error)
        val vm2 = vm(CourierCreationError.ADMIN_MFA_REQUIRED)
        vm2.fill(); vm2.submit()
        assertEquals(R.string.err_courier_admin_mfa, vm2.state.value.error)
    }
}
