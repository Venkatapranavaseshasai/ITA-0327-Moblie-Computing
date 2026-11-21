package dev.netstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.netstream.app.ui.*
import dev.netstream.app.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as NetStreamApp
        val factory = viewModelFactory {
            initializer { CatalogueViewModel(app.data.catalogue) }
            initializer { PlayerViewModel(app, app.data.telemetry, createSavedStateHandle()) }
        }
        setContent {
            NetStreamTheme {
                val catalogue: CatalogueViewModel = viewModel(factory = factory)
                val playback: PlayerViewModel = viewModel(factory = factory)
                val selected by playback.selected.collectAsStateWithLifecycle()
                val holder = rememberSaveableStateHolder()
                val video = selected
                if (video == null)
                    holder.SaveableStateProvider("library") {
                        val state by catalogue.ui.collectAsStateWithLifecycle()
                        CatalogueScreen(
                            state,
                            app.data.cloudEnabled,
                            playback::open,
                            catalogue::reload,
                        )
                    }
                else PlayerScreen(video, playback, this, app.data.cloudEnabled)
            }
        }
    }
}
