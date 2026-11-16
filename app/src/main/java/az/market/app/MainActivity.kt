package az.market.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.core.designsystem.theme.MarketTheme
import az.market.app.core.model.SessionState
import az.market.app.navigation.MarketNavHost
import az.market.app.navigation.RootViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash visible until the session (and later the role) is resolved.
        splash.setKeepOnScreenCondition { rootViewModel.session.value is SessionState.Loading }
        enableEdgeToEdge()

        setContent {
            MarketTheme {
                val session by rootViewModel.session.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (session !is SessionState.Loading) {
                        MarketNavHost(session)
                    }
                }
            }
        }
    }
}
