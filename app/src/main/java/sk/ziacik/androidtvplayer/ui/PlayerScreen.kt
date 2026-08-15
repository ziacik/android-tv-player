package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import sk.ziacik.androidtvplayer.player.PlayerController
import sk.ziacik.androidtvplayer.player.PlayerUiState

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun PlayerScreen(
    controller: PlayerController,
    player: Player,
    overlayController: OverlayController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val overlayVisible by overlayController.visible.collectAsState()
    var focusedControl by remember { mutableStateOf(FocusedControl.PLAY_PAUSE) }
    val focusRequester = remember { FocusRequester() }
    val commandMapper = remember { RemoteCommandMapper() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler {
        if (overlayVisible) overlayController.hide() else onExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent false
                }

                val keyCode = event.nativeKeyEvent.keyCode
                if (state is PlayerUiState.Error && keyCode.isCenterKey()) {
                    controller.retry()
                    return@onPreviewKeyEvent true
                }

                when (
                    commandMapper.map(
                        keyCode = keyCode,
                        overlayVisible = overlayVisible,
                        focusedControl = focusedControl,
                    )
                ) {
                    RemoteCommand.ShowOverlay -> overlayController.show()
                    RemoteCommand.SeekBack -> {
                        controller.seekBack()
                        overlayController.show()
                    }
                    RemoteCommand.SeekForward -> {
                        controller.seekForward()
                        overlayController.show()
                    }
                    RemoteCommand.TogglePlayback -> {
                        controller.togglePlayback()
                        overlayController.show()
                    }
                    RemoteCommand.GoLive -> {
                        controller.goLive()
                        overlayController.show()
                    }
                    RemoteCommand.FocusPlayPause -> {
                        focusedControl = FocusedControl.PLAY_PAUSE
                        overlayController.show()
                    }
                    RemoteCommand.FocusLive -> {
                        focusedControl = FocusedControl.LIVE
                        overlayController.show()
                    }
                    RemoteCommand.HideOverlay -> overlayController.hide()
                    RemoteCommand.Exit -> onExit()
                    RemoteCommand.Ignore -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.player = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        when (val current = state) {
            PlayerUiState.Resolving,
            PlayerUiState.Preparing,
            -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
            is PlayerUiState.Ready -> if (overlayVisible) {
                PlayerOverlay(
                    isPlaying = current.isPlaying,
                    isSeekable = current.isSeekable,
                    focusedControl = focusedControl,
                )
            }
            is PlayerUiState.Unavailable -> ErrorPanel(
                message = "Tento program nie je dostupný online",
                modifier = Modifier.align(Alignment.Center),
            )
            is PlayerUiState.Error -> ErrorPanel(
                message = current.message,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(text = message, color = Color.White, fontSize = 24.sp)
        Text(
            text = "Retry",
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 28.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 20.sp,
        )
    }
}

private fun Int.isCenterKey(): Boolean =
    this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER
