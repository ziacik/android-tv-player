package sk.ziacik.androidtvplayer.ui

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Date
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(overlayVisible, state is PlayerUiState.Ready) {
        if (!overlayVisible || state !is PlayerUiState.Ready) return@LaunchedEffect
        while (true) {
            controller.refreshPlaybackSnapshot()
            delay(1_000L)
        }
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
                val retryable = state is PlayerUiState.Unavailable || state is PlayerUiState.Error
                if (retryable && keyCode.isCenterKey()) {
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

        PlayerStateLayer(
            state = state,
            overlayVisible = overlayVisible,
            focusedControl = focusedControl,
            formatTime = { millis -> timeFormat.format(Date(millis)) },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
internal fun PlayerStateLayer(
    state: PlayerUiState,
    overlayVisible: Boolean,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    when (val current = state) {
        is PlayerUiState.Resolving,
        is PlayerUiState.Preparing,
        -> CircularProgressIndicator(
            modifier = modifier,
            color = MaterialTheme.colorScheme.primary,
        )

        is PlayerUiState.Ready -> if (overlayVisible) {
            PlayerOverlay(
                model = PlayerOverlayModel.from(current),
                focusedControl = focusedControl,
                modifier = modifier,
            )
        }

        is PlayerUiState.Unavailable -> RestrictedProgramPanel(
            programTitle = current.program.title,
            retryTime = current.program.endsAtMs?.let(formatTime),
            modifier = modifier,
        )

        is PlayerUiState.Error -> ErrorPanel(
            message = current.message,
            actionText = "Skúsiť znova",
            modifier = modifier,
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    actionText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(text = message, color = Color.White, fontSize = 24.sp)
        Text(
            text = actionText,
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
