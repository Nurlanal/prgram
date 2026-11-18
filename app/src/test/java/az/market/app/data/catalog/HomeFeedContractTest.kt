package az.market.app.data.catalog

import az.market.app.core.common.Money
import az.market.app.testing.fixtureHomeFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes the real home_feed() JSON produced by the migrations (see supabase/tests). */
class HomeFeedContractTest {
    private val feed = fixtureHomeFeed()

    @Test fun pricesComputedByTheServerArriveExactly() {
        val tea = feed.discounted.first()
        assertEquals("Çay Azərçay 100q", tea.name)
        assertEquals(Money(500), tea.price)
        assertEquals(Money(349), tea.finalPrice)
        assertEquals(30, tea.discountPercent)
        assertEquals(ProductUnit.PACK, tea.unit)
        assertTrue(tea.hasDiscount)
        assertTrue(feed.discounted.all { it.hasDiscount })
    }

    @Test fun sectionsAndCategories() {
        assertEquals(1, feed.banners.size)
        assertEquals(BannerLink.Category(1), feed.banners.single().link)
        assertEquals(13, feed.categories.size)
        assertEquals("Ərzaq", feed.categories.first().name)
        assertEquals(listOf("Süd 1L"), feed.bestSellers.map { it.name })
        assertTrue(feed.newArrivals.any { !it.inStock })        // Limonad has no stock
        assertEquals(2L, feed.forYou.first().categoryId)        // customer A bought milk
    }

    @Test fun bonusCardOfTheSignedInCustomer() {
        val bonus = assertNotNull(feed.bonus).let { feed.bonus!! }
        assertTrue(bonus.cardNumber.startsWith("7700"))
        assertEquals(Money.ZERO, bonus.balance)
    }

    @Test fun unsafeBannerLinksAreIgnored() {
        assertEquals(BannerLink.None, BannerDto("b", "t", imageUrl = "x", linkType = "url", linkValue = "http://evil.test").toDomain().link)
        assertEquals(BannerLink.Url("https://ok.test"), BannerDto("b", "t", imageUrl = "x", linkType = "url", linkValue = "https://ok.test").toDomain().link)
        assertEquals(BannerLink.None, BannerDto("b", "t", imageUrl = "x", linkType = "category", linkValue = "abc").toDomain().link)
    }

    @Test fun moneyParsingToleratesServerFormats() {
        assertEquals(Money(500), moneyOf("5"))
        assertEquals(Money(500), moneyOf("5.0"))
        assertEquals(Money(349), moneyOf("3.49"))
    }
}
