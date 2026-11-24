package az.magaza.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import az.magaza.pos.data.db.Customer
import az.magaza.pos.data.db.Product
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bütün ekranları açır və əsas satış axınını UI üzərindən yoxlayır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as PosApp

    private fun seed() = runBlocking {
        app.repo.saveProduct(Product(name = "iPhone kabeli", barcode = "111", purchasePrice = 500, salePrice = 1000, stock = 20, minStock = 2,
            category = "Kabellər"))
        app.repo.saveProduct(Product(name = "Silikon qab", purchasePrice = 300, salePrice = 800, stock = 1, minStock = 3,
            category = "Telefon qabları"))
        app.repo.saveCustomer(Customer(name = "Test Müştəri", phone = "0501112233"))
    }

    private fun openDrawerItem(label: String) {
        rule.onNodeWithContentDescription("Menyu").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun click(text: String, substring: Boolean = false) {
        val node = rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun back() {
        rule.onNodeWithContentDescription("Geri").performClick()
        rule.waitForIdle()
    }

    private fun bottom(label: String) {
        rule.onAllNodesWithText(label).filterToOne(hasClickAction()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun allScreensOpenAndSaleFlowWorks() {
        seed()
        rule.waitForIdle()
        // Ana səhifə
        rule.onNodeWithText("Kassa balansı").assertExists()
        rule.onNodeWithText("Anbar alış dəyəri", useUnmergedTree = true).performScrollTo()

        // Anbar
        bottom("Anbar")
        rule.onNodeWithText("iPhone kabeli").assertExists()
        rule.onNodeWithText("iPhone kabeli").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Anbar qalığı").assertExists()
        back()
        click("Məhsul")
        rule.waitForIdle()
        rule.onNodeWithText("Yeni məhsul").assertExists()
        back()

        // Satış: məhsul əlavə et, nağd ödə
        bottom("Satış")
        rule.onAllNodesWithContentDescription("Əlavə et")[0].performClick()
        rule.onAllNodesWithContentDescription("Əlavə et")[0].performClick()
        rule.waitForIdle()
        click("Ödəniş")
        rule.waitForIdle()
        click("Satışı tamamla", substring = true)
        rule.waitForIdle()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Satış tamamlandı").fetchSemanticsNodes().isNotEmpty() }
        click("Yeni satış")
        rule.waitForIdle()
        runBlocking {
            val sales = app.repo.dao.allSales()
            assertEquals(1, sales.size)
            assertEquals(2000L, sales[0].total)
            assertEquals(18, app.repo.dao.allProducts().first { it.name == "iPhone kabeli" }.stock)
        }

        // Kassa və Hesabat
        bottom("Kassa")
        rule.onNodeWithText("Cari kassa balansı").assertExists()
        rule.onNodeWithText("Pul əlavə et").assertExists()
        bottom("Hesabat")
        listOf("Qazanc", "Anbar", "Kassa", "Borc", "Satış").forEach { t ->
            rule.onAllNodesWithText(t).filter(hasClickAction()).onFirst().performClick()
            rule.waitForIdle()
        }
        bottom("Ana səhifə")

        // Menyu bölmələri
        listOf(
            "Mədaxil (anbara daxil)" to "Yeni mədaxil",
            "Məxaric (xərclər)" to "Yeni xərc",
            "Borclar" to "Borc əlavə et",
            "Müştərilər" to "Test Müştəri",
            "Təchizatçılar" to "Təchizatçılara ümumi borc",
            "Satış tarixçəsi" to "#1",
            "Əməliyyat tarixçəsi" to "Satış #1",
            "Backup və bərpa" to "Backup faylı yarat (ZIP)",
            "Ayarlar" to "PIN kod ilə qoruma",
        ).forEach { (menu, expect) ->
            openDrawerItem(menu)
            try {
                rule.waitUntil(5000) { rule.onAllNodesWithText(expect, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            } catch (e: Throwable) { throw AssertionError("'$menu' ekranında '$expect' tapılmadı", e) }
            back()
        }

        // Satış detalı və ləğv
        openDrawerItem("Satış tarixçəsi")
        rule.waitUntil(5000) { rule.onAllNodesWithText("#1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        click("#1")
        rule.waitForIdle()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Satış #1").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Satışı ləğv et"))
        rule.onNodeWithText("Satışdan qazanc").assertExists()
        click("Satışı ləğv et")
        rule.onNodeWithText("Ləğv səbəbi *").performTextInput("Test")
        rule.onAllNodesWithText("Ləğv et", useUnmergedTree = true).onLast().performClick()
        rule.waitUntil(5000) { runBlocking { app.repo.dao.allSales()[0].status == "CANCELLED" } }
        runBlocking { assertEquals(20, app.repo.dao.allProducts().first { it.name == "iPhone kabeli" }.stock) }
    }

    @Test
    fun unknownBarcodeIsAssignedFromSalesScreen() {
        seed()
        rule.waitForIdle()
        bottom("Satış")
        // Kənar skaner kimi: barkodu yazıb Enter
        rule.onNode(hasSetTextAction()).performTextInput("8690000000017")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Barkod tapılmadı").fetchSemanticsNodes().isNotEmpty() }
        // Barkodsuz "Silikon qab" siyahıdadır, barkodlu "iPhone kabeli" isə yox
        rule.onAllNodesWithText("iPhone kabeli", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Təyin et", useUnmergedTree = true).onFirst().performClick()
        rule.waitUntil(5000) { runBlocking { app.repo.dao.findByBarcode("8690000000017") != null } }
        rule.waitForIdle()
        runBlocking { assertEquals("Silikon qab", app.repo.dao.findByBarcode("8690000000017")!!.name) }
        // Məhsul dərhal səbətə düşdü
        rule.waitUntil(5000) { rule.onAllNodesWithText("Ödəniş", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("1 məhsul").assertExists()
    }
}
