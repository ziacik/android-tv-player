package sk.ziacik.androidtvplayer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sk.ziacik.androidtvplayer.player.Media3PlayerPort
import sk.ziacik.androidtvplayer.player.PlayerController
import sk.ziacik.androidtvplayer.resolver.OkHttpStvrClient
import sk.ziacik.androidtvplayer.resolver.StvrResolver
import sk.ziacik.androidtvplayer.ui.AndroidTvPlayerTheme
import sk.ziacik.androidtvplayer.ui.OverlayController
import sk.ziacik.androidtvplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val playerPort = Media3PlayerPort(this)
        val resolver = StvrResolver(OkHttpStvrClient())
        val overlayController = OverlayController(appScope)
        playerController = PlayerController(
            scope = appScope,
            resolve = resolver::resolve,
            playerPort = playerPort,
        )

        setContent {
            AndroidTvPlayerTheme {
                PlayerScreen(
                    controller = playerController,
                    player = playerPort.player,
                    overlayController = overlayController,
                    onExit = ::finish,
                )
            }
        }

        playerController.start()
    }

    override fun onDestroy() {
        playerController.release()
        appScope.cancel()
        super.onDestroy()
    }
}
