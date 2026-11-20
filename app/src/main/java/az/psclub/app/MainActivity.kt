package az.psclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import az.psclub.app.data.Store
import az.psclub.app.ui.PsClubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store.get(applicationContext)
        setContent { PsClubApp(store) }
    }
}
