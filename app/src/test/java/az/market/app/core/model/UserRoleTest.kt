package az.market.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserRoleTest {
    @Test fun mapsServerValues() {
        assertEquals(UserRole.ADMIN, UserRole.fromServerValue("admin"))
        assertEquals(UserRole.COURIER, UserRole.fromServerValue(" Courier "))
        assertEquals(UserRole.CUSTOMER, UserRole.fromServerValue("CUSTOMER"))
    }

    @Test fun unknownOrMissingRoleGrantsNothing() {
        assertNull(UserRole.fromServerValue(null))
        assertNull(UserRole.fromServerValue(""))
        assertNull(UserRole.fromServerValue("superadmin"))
    }
}
