package io.github.dwicki.tidytudo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.ui.theme.TidytudoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TidytudoTheme {
                AppNavigation()
            }
        }
    }

    /** The service can be gone (killed, not restarted); opening the app is a safe moment to bring it back. */
    override fun onStart() {
        super.onStart()
        if (appContainer.settings.backgroundMonitoring.value) appContainer.startMonitoring()
    }
}
