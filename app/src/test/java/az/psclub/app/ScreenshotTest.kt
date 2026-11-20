package az.psclub.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import az.psclub.app.data.Store
import az.psclub.app.ui.Cat
import az.psclub.app.ui.PsClubApp
import az.psclub.app.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key screens to PNG (app/build/shots) to check the UI without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val store get() = Store.get(ApplicationProvider.getApplicationContext())

    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(3500)
        var bmp: Bitmap? = null
        rule.runOnUiThread {
            val root = rule.activity.window.decorView.rootView
            bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bmp!!))
        }
        val dir = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ps5() = render("01_ps5") { PsClubApp(store, startCat = Cat.PS5) }
    @Test fun ps4() = render("02_ps4") { PsClubApp(store, startCat = Cat.PS4) }
    @Test fun ps3() = render("03_ps3") { PsClubApp(store, startCat = Cat.PS3) }
    @Test fun debt() {
        val s = store
        s.addDebt("Elvin Məmmədov", "050 123 45 67", 12.5, "PS5 · 2 saat")
        s.addDebt("Murad", "", 4.0, "Pizza, Cola")
        s.addDebt("Rauf", "055 222 33 44", 7.0, "Masa 6")
        render("12_debt") { PsClubApp(s, startTab = Tab.DEBT) }
    }
    @Test fun dayReport() {
        val s = store
        val now = System.currentTimeMillis()
        s.closeSession(az.psclub.app.data.Session(1, s.tables[1].id, now - 95 * 60_000L, 0, "", 6.0), now)
        s.closeSession(az.psclub.app.data.Session(2, s.tables[5].id, now - 25 * 60_000L, 0, "", 4.0), now, debtorName = "Elvin")
        s.addDebt("Murad", "", 4.0, "köhnə")
        s.payDebt(s.debtorByName("Murad")!!, 4.0)
        render("13_day") { PsClubApp(s, startTab = Tab.ADMIN) }
    }
    @Test fun repair() = render("06_repair") { PsClubApp(store, startCat = Cat.REPAIR) }
    @Test fun tableSheet() = render("07_table_sheet") { PsClubApp(store, startTable = store.tables[1].id) }
    @Test fun active() {
        val s = store
        s.openTable(s.tables[0], 60, "Elvin"); s.openTable(s.tables[5], 0, "")
        render("09_active") { PsClubApp(s, startTab = Tab.ACTIVE) }
    }
    @Test fun admin() = render("11_admin") { PsClubApp(store, startTab = Tab.ADMIN) }
    @Test fun light() {
        store.setDark(false)
        render("10_light") { PsClubApp(store, startCat = Cat.SERVICES) }
        store.setDark(true)
    }
}
