package sk.ziacik.androidtvplayer.ui

import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    onSaveMarkizaCredentials: (String, String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val overlayVisible by overlayController.visible.collectAsState()
    var focusedControl by remember { mutableStateOf(FocusedControl.PLAY_PAUSE) }
    val focusRequester = remember { FocusRequester() }
    val commandMapper = remember { RemoteCommandMapper() }
    val numericInputScope = rememberCoroutineScope()
    val numericInput = remember(controller, numericInputScope) {
        NumericChannelInput(
            scope = numericInputScope,
            onChannelSelected = controller::selectChannel,
        )
    }
    val numericDigits by numericInput.digits.collectAsState()
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(numericInput) {
        onDispose { numericInput.cancel() }
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
                val command = commandMapper.map(
                    keyCode = keyCode,
                    overlayVisible = overlayVisible,
                    focusedControl = focusedControl,
                )
                if (state is PlayerUiState.CredentialsRequired && !command.isChannelSelection()) {
                    return@onPreviewKeyEvent false
                }
                val retryable = state is PlayerUiState.Unavailable || state is PlayerUiState.Error
                if (retryable && keyCode.isCenterKey()) {
                    controller.retry()
                    return@onPreviewKeyEvent true
                }

                when (command) {
                    is RemoteCommand.NumericDigit -> numericInput.append(command.digit)
                    RemoteCommand.ChannelUp -> {
                        controller.channelUp()
                    }
                    RemoteCommand.ChannelDown -> {
                        controller.channelDown()
                    }
                    RemoteCommand.ShowOverlay -> Unit
                    RemoteCommand.SeekBack -> {
                        controller.seekBack()
                    }
                    RemoteCommand.SeekForward -> {
                        controller.seekForward()
                    }
                    RemoteCommand.TogglePlayback -> {
                        controller.togglePlayback()
                    }
                    RemoteCommand.GoLive -> {
                        controller.goLive()
                    }
                    RemoteCommand.FocusPlayPause -> {
                        focusedControl = FocusedControl.PLAY_PAUSE
                    }
                    RemoteCommand.FocusLive -> {
                        focusedControl = FocusedControl.LIVE
                    }
                    RemoteCommand.HideOverlay -> overlayController.hide()
                    RemoteCommand.Exit -> onExit()
                    RemoteCommand.Ignore -> return@onPreviewKeyEvent false
                }
                command.overlayTimeoutMs()?.let(overlayController::show)
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
            onSaveMarkizaCredentials = onSaveMarkizaCredentials,
            modifier = Modifier.align(Alignment.Center),
        )

        numericDigits?.let { digits ->
            NumericChannelIndicator(
                digits = digits,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(36.dp),
            )
        }
    }
}

@Composable
internal fun NumericChannelIndicator(
    digits: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = digits,
        modifier = modifier
            .testTag("numeric-channel-indicator")
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 9.dp),
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun PlayerStateLayer(
    state: PlayerUiState,
    overlayVisible: Boolean,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    onSaveMarkizaCredentials: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    when (val current = state) {
        is PlayerUiState.Resolving,
        is PlayerUiState.Preparing,
        -> LoadingChannelPanel(
            channelLabel = current.channel.displayName,
            modifier = modifier,
        )

        is PlayerUiState.Ready -> if (overlayVisible) {
            PlayerOverlay(
                model = PlayerOverlayModel.from(current, System.currentTimeMillis()),
                focusedControl = focusedControl,
                formatTime = formatTime,
                modifier = modifier,
            )
        }

        is PlayerUiState.Unavailable -> RestrictedProgramPanel(
            channelLabel = current.channel.displayName,
            programTitle = current.program.title,
            retryTime = current.program.endsAtMs?.let(formatTime),
            modifier = modifier,
        )

        is PlayerUiState.CredentialsRequired -> MarkizaCredentialsPanel(
            onSave = onSaveMarkizaCredentials,
            modifier = modifier,
        )

        is PlayerUiState.Error -> ErrorPanel(
            channelLabel = current.channel.displayName,
            message = current.message,
            reason = current.reason,
            actionText = "Skúsiť znova",
            modifier = modifier,
        )
    }
}

@Composable
private fun MarkizaCredentialsPanel(
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .background(Color(0xE6111114), RoundedCornerShape(22.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("MARKÍZA", color = Color.White.copy(alpha = 0.68f), fontWeight = FontWeight.Bold)
        Text("Prihlásenie k bezplatnému účtu", color = Color.White, fontSize = 22.sp)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Heslo") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Button(
            onClick = { onSave(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
        ) {
            Text("Prihlásiť sa")
        }
    }
}

@Composable
private fun LoadingChannelPanel(
    channelLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "$channelLabel · NAČÍTAVAM",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ErrorPanel(
    channelLabel: String,
    message: String,
    reason: String,
    actionText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            text = channelLabel,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Text(text = message, color = Color.White, fontSize = 24.sp)
        Text(
            text = reason,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
        )
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
