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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.epg.EpgRepository
import sk.ziacik.androidtvplayer.player.PlayerController
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun PlayerScreen(
    controller: PlayerController,
    player: Player,
    overlayController: OverlayController,
    epgRepository: EpgRepository = EpgRepository { _, _ -> null },
    onSaveMarkizaCredentials: (String, String) -> Unit = { _, _ -> },
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val streamHost by controller.streamHost.collectAsState()
    val overlayVisible by overlayController.visible.collectAsState()
    var focusedControl by remember { mutableStateOf(FocusedControl.TIMELINE) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var miniEpgVisible by remember { mutableStateOf(false) }
    var miniEpgSelectedChannel by remember { mutableStateOf<TvChannel?>(null) }
    var miniEpgProgrammes by remember { mutableStateOf<Map<String, ProgramMetadata>>(emptyMap()) }
    var miniEpgAttemptedChannels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var miniEpgNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val focusRequester = remember { FocusRequester() }
    val commandMapper = remember { RemoteCommandMapper() }
    val numericInputScope = rememberCoroutineScope()
    val numericInput = remember(controller, overlayController, numericInputScope) {
        NumericChannelInput(
            scope = numericInputScope,
            onChannelSelected = { channel ->
                overlayController.showUntilProgramTitleReady()
                controller.selectChannel(channel)
            },
        )
    }
    val numericDigits by numericInput.digits.collectAsState()
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val seekTimeFormat = remember(context) {
        SimpleDateFormat(
            if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
            Locale.getDefault(),
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(numericInput) {
        onDispose { numericInput.cancel() }
    }

    LaunchedEffect(state) {
        if (state.hasResolvedProgramTitle()) {
            overlayController.onProgramTitleReady()
        }
    }

    LaunchedEffect(overlayVisible, state is PlayerUiState.Ready) {
        if (!overlayVisible || state !is PlayerUiState.Ready) return@LaunchedEffect
        while (true) {
            controller.refreshPlaybackSnapshot()
            delay(1_000L)
        }
    }

    LaunchedEffect(seekPreviewMs) {
        if (seekPreviewMs == null) return@LaunchedEffect
        delay(SEEK_PREVIEW_DURATION_MS)
        seekPreviewMs = null
    }

    LaunchedEffect(miniEpgVisible) {
        if (!miniEpgVisible) return@LaunchedEffect
        while (true) {
            miniEpgNowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(
        miniEpgVisible,
        miniEpgSelectedChannel?.storageKey,
        state.channel.storageKey,
    ) {
        if (!miniEpgVisible) return@LaunchedEffect
        val selected = miniEpgSelectedChannel ?: state.channel
        val visibleChannels = buildMiniEpgRows(
            channels = TvChannel.entries,
            currentChannel = state.channel,
            selectedChannel = selected,
            programmes = miniEpgProgrammes,
            nowMs = miniEpgNowMs,
        ).map { it.channel }
        val missingChannels = visibleChannels.filter { channel ->
            channel.storageKey !in miniEpgAttemptedChannels
        }

        for (channel in missingChannels) {
            val programme = try {
                epgRepository.currentProgram(channel, System.currentTimeMillis())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            miniEpgAttemptedChannels = miniEpgAttemptedChannels + channel.storageKey
            if (programme != null && programme.title.isNotBlank()) {
                miniEpgProgrammes = miniEpgProgrammes + (channel.storageKey to programme)
            }
        }
    }

    BackHandler {
        when {
            miniEpgVisible -> {
                miniEpgVisible = false
                miniEpgSelectedChannel = null
            }
            overlayVisible -> {
                seekPreviewMs = null
                overlayController.hide()
            }
            else -> onExit()
        }
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
                    miniEpgVisible = miniEpgVisible,
                )
                if (state is PlayerUiState.CredentialsRequired && !command.isChannelSelection()) {
                    return@onPreviewKeyEvent false
                }
                val retryable = state is PlayerUiState.Unavailable || state is PlayerUiState.Error
                if (!miniEpgVisible && retryable && keyCode.isCenterKey()) {
                    controller.retry()
                    return@onPreviewKeyEvent true
                }

                when (command) {
                    is RemoteCommand.NumericDigit -> {
                        miniEpgVisible = false
                        miniEpgSelectedChannel = null
                        numericInput.append(command.digit)
                    }
                    RemoteCommand.ChannelUp -> {
                        miniEpgVisible = false
                        miniEpgSelectedChannel = null
                        seekPreviewMs = null
                        overlayController.showUntilProgramTitleReady()
                        controller.channelUp()
                    }
                    RemoteCommand.ChannelDown -> {
                        miniEpgVisible = false
                        miniEpgSelectedChannel = null
                        seekPreviewMs = null
                        overlayController.showUntilProgramTitleReady()
                        controller.channelDown()
                    }
                    RemoteCommand.OpenMiniEpg -> {
                        val currentProgramme = state.currentProgramOrNull()
                            ?.takeIf { !it.isEpgLookupPending && it.title.isNotBlank() }
                        miniEpgSelectedChannel = state.channel
                        miniEpgProgrammes = currentProgramme
                            ?.let { mapOf(state.channel.storageKey to it) }
                            ?: emptyMap()
                        miniEpgAttemptedChannels = currentProgramme
                            ?.let { setOf(state.channel.storageKey) }
                            ?: emptySet()
                        miniEpgNowMs = System.currentTimeMillis()
                        miniEpgVisible = true
                        seekPreviewMs = null
                        overlayController.hide()
                    }
                    RemoteCommand.MiniEpgUp -> {
                        miniEpgSelectedChannel = adjacentMiniEpgChannel(
                            channels = TvChannel.entries,
                            selectedChannel = miniEpgSelectedChannel ?: state.channel,
                            direction = -1,
                        )
                    }
                    RemoteCommand.MiniEpgDown -> {
                        miniEpgSelectedChannel = adjacentMiniEpgChannel(
                            channels = TvChannel.entries,
                            selectedChannel = miniEpgSelectedChannel ?: state.channel,
                            direction = 1,
                        )
                    }
                    RemoteCommand.SelectMiniEpgChannel -> {
                        val selected = miniEpgSelectedChannel ?: state.channel
                        miniEpgVisible = false
                        miniEpgSelectedChannel = null
                        if (selected.storageKey != state.channel.storageKey) {
                            overlayController.showUntilProgramTitleReady()
                            controller.selectChannel(selected)
                        }
                    }
                    RemoteCommand.CloseMiniEpg -> {
                        miniEpgVisible = false
                        miniEpgSelectedChannel = null
                    }
                    RemoteCommand.ShowOverlay -> {
                        focusedControl = FocusedControl.TIMELINE
                    }
                    RemoteCommand.SeekBack -> {
                        seekPreviewMs = controller.seekBack()
                        controller.refreshPlaybackSnapshot()
                    }
                    RemoteCommand.SeekForward -> {
                        seekPreviewMs = controller.seekForward()
                        controller.refreshPlaybackSnapshot()
                    }
                    RemoteCommand.TogglePlayback -> {
                        controller.togglePlayback()
                    }
                    RemoteCommand.GoLive -> {
                        seekPreviewMs = null
                        controller.goLive()
                        controller.refreshPlaybackSnapshot()
                    }
                    RemoteCommand.FocusPlayPause -> {
                        focusedControl = FocusedControl.PLAY_PAUSE
                    }
                    RemoteCommand.FocusLive -> {
                        focusedControl = FocusedControl.LIVE
                    }
                    RemoteCommand.FocusTimeline -> {
                        focusedControl = FocusedControl.TIMELINE
                    }
                    RemoteCommand.HideOverlay -> {
                        seekPreviewMs = null
                        overlayController.hide()
                    }
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
                    setKeepContentOnPlayerReset(true)
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
            streamHost = streamHost,
            overlayVisible = overlayVisible,
            focusedControl = focusedControl,
            formatTime = { millis -> timeFormat.format(Date(millis)) },
            onSaveMarkizaCredentials = onSaveMarkizaCredentials,
            modifier = Modifier.align(Alignment.Center),
            seekPreviewMs = seekPreviewMs,
            formatSeekTime = { millis -> seekTimeFormat.format(Date(millis)) },
        )

        if (miniEpgVisible) {
            MiniEpgOverlay(
                rows = buildMiniEpgRows(
                    channels = TvChannel.entries,
                    currentChannel = state.channel,
                    selectedChannel = miniEpgSelectedChannel ?: state.channel,
                    programmes = miniEpgProgrammes,
                    nowMs = miniEpgNowMs,
                ),
            )
        }

        numericDigits?.let { digits ->
            NumericChannelIndicator(
                digits = digits,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 88.dp, end = 36.dp),
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
    streamHost: String? = null,
    overlayVisible: Boolean,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    onSaveMarkizaCredentials: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    seekPreviewMs: Long? = null,
    formatSeekTime: (Long) -> String = formatTime,
) {
    when (val current = state) {
        is PlayerUiState.Resolving -> StateOverlay(
            channel = current.channel,
            program = current.program,
            streamHost = streamHost,
            statusText = "Prepínam…",
            focusedControl = focusedControl,
            formatTime = formatTime,
            modifier = modifier,
        )

        is PlayerUiState.Preparing -> StateOverlay(
            channel = current.channel,
            program = current.program,
            streamHost = streamHost,
            statusText = "Prepínam…",
            focusedControl = focusedControl,
            formatTime = formatTime,
            modifier = modifier,
        )

        is PlayerUiState.Ready -> if (overlayVisible) {
            PlayerOverlay(
                model = PlayerOverlayModel.from(current, System.currentTimeMillis(), streamHost),
                focusedControl = focusedControl,
                timelineFocused = focusedControl == FocusedControl.TIMELINE,
                formatTime = formatTime,
                modifier = modifier,
                seekPreviewMs = seekPreviewMs,
                formatSeekTime = formatSeekTime,
            )
        }

        is PlayerUiState.Unavailable -> StateOverlay(
            channel = current.channel,
            program = current.program,
            streamHost = streamHost,
            statusText = current.nextRetryAtMs?.let { "Program nie je dostupný online · obnovím o ${formatTime(it)}" }
                ?: "Program nie je dostupný online",
            focusedControl = focusedControl,
            formatTime = formatTime,
            modifier = modifier,
        )

        is PlayerUiState.CredentialsRequired -> MarkizaCredentialsPanel(
            onSave = onSaveMarkizaCredentials,
            modifier = modifier,
        )

        is PlayerUiState.Error -> StateOverlay(
            channel = current.channel,
            program = current.program,
            streamHost = streamHost,
            statusText = current.nextRetryAtMs?.let { "Obnovím vysielanie o ${formatTime(it)}" }
                ?: current.message,
            focusedControl = focusedControl,
            formatTime = formatTime,
            modifier = modifier,
        )
    }
}

@Composable
private fun StateOverlay(
    channel: TvChannel,
    program: ProgramMetadata?,
    streamHost: String?,
    statusText: String,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    modifier: Modifier,
) {
    PlayerOverlay(
        model = PlayerOverlayModel.from(
            channel = channel,
            program = program,
            playback = null,
            streamHost = streamHost,
            statusText = statusText,
            nowMs = System.currentTimeMillis(),
        ),
        focusedControl = focusedControl,
        timelineFocused = false,
        formatTime = formatTime,
        modifier = modifier,
    )
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

private fun PlayerUiState.hasResolvedProgramTitle(): Boolean = when (this) {
    is PlayerUiState.Preparing -> program?.let { !it.isEpgLookupPending && it.title.isNotBlank() } == true
    is PlayerUiState.Ready -> !program.isEpgLookupPending && program.title.isNotBlank()
    else -> false
}

private fun PlayerUiState.currentProgramOrNull(): ProgramMetadata? = when (this) {
    is PlayerUiState.Resolving -> program
    is PlayerUiState.Preparing -> program
    is PlayerUiState.Ready -> program
    is PlayerUiState.Unavailable -> program
    is PlayerUiState.Error -> program
    is PlayerUiState.CredentialsRequired -> null
}

private fun Int.isCenterKey(): Boolean =
    this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER

private const val SEEK_PREVIEW_DURATION_MS = 1_800L
