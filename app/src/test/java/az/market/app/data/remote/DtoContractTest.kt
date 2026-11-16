package az.market.app.data.remote

import az.market.app.data.store.toDomain
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract test: the fixtures are real rows exported from a database built from
 * supabase/migrations (supabase/tests/run_local_tests.sh). If a column is renamed in SQL
 * without updating the DTOs, this test fails.
 */
class DtoContractTest {
    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    @Test fun appSettingsRowDecodes() {
        val dto = MarketJson.decodeFromString<AppSettingsDto>(fixture("app_settings.json"))
        assertEquals(1, dto.id)
        assertEquals("Market", dto.storeName)
        assertEquals("#C62828", dto.primaryColor)          // changed by the admin step of the DB tests
        assertEquals("0.0500", dto.bonusEarnRate)          // numeric kept exact, no Double rounding

        val domain = dto.toDomain()
        assertEquals(0xFFC62828, domain.primaryColorArgb)
        assertEquals(0, BigDecimal("0.05").compareTo(domain.bonusEarnRate))
    }

    @Test fun categoryRowsDecode() {
        val list = MarketJson.decodeFromString<List<CategoryDto>>(fixture("categories.json"))
        assertEquals(13, list.size)                       // 12 seeded + 1 created by the admin test
        assertEquals("Ərzaq", list.first().name)
        assertEquals("erzaq", list.first().slug)
        assertEquals(listOf(10, 20, 30), list.take(3).map { it.sortOrder })
    }
}
