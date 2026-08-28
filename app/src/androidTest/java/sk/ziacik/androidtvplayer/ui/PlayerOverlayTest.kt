package sk.ziacik.androidtvplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun numericChannelIndicatorShowsPendingDigits() {
        compose.setContent {
            AndroidTvPlayerTheme {
                NumericChannelIndicator(digits = "12")
            }
        }

        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithTag("numeric-channel-indicator").assertIsDisplayed()
    }

    @Test
    fun cinematicOverlayExposesModernTimelineClockAndSeekPreview() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerOverlay(
                    model = PlayerOverlayModel(
                        channelLabel = "1 · JEDNOTKA",
                        programTitle = "Večerný program",
                        progress = 0.4f,
                        displayNowMs = 2_000L,
                        programmeStartMs = 1_000L,
                        programmeNowMs = 2_000L,
                        programmeEndMs = 3_000L,
                        isLive = false,
                        isPlaying = true,
                        isSeekable = true,
                        liveActionText = "NAŽIVO",
                    ),
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { milliseconds ->
                        mapOf(
                            1_000L to "20:15",
                            2_000L to "20:42",
                        ).getValue(milliseconds)
                    },
                    seekPreviewMs = 2_500L,
                    formatSeekTime = { "20:42:37" },
                )
            }
        }

        compose.onNodeWithText("1 · JEDNOTKA").assertIsDisplayed()
        compose.onNodeWithText("Večerný program").assertIsDisplayed()
        compose.onNodeWithText("20:15").assertIsDisplayed()
        compose.onNodeWithText("20:42").assertIsDisplayed()
        compose.onNodeWithText("20:42:37").assertIsDisplayed()
        compose.onNodeWithText("1 min left").assertIsDisplayed()
        compose.onNodeWithText("↶ 10").assertIsDisplayed()
        compose.onNodeWithText("10 ↷").assertIsDisplayed()
        compose.onNodeWithText("NAŽIVO").assertIsDisplayed()
        compose.onNodeWithTag("wall-clock").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
        compose.onNodeWithTag("seek-preview-time").assertIsDisplayed()
        compose.onNodeWithTag("programme-progress-marker").assertIsDisplayed()
        compose.onNodeWithTag("programme-start-boundary").assertIsDisplayed()
        compose.onNodeWithTag("programme-time-remaining").assertIsDisplayed()
        compose.onNodeWithTag("current-programme-time").assertDoesNotExist()
        compose.onNodeWithTag("programme-end-boundary").assertDoesNotExist()
    }

    @Test
    fun readyDvojkaStateRendersChannelAndProgramWhenOverlayIsVisible() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = PlayerUiState.Ready(
                        channel = TvChannel.DVOJKA,
                        program = ProgramMetadata(
                            title = "Večerný program",
                            startsAtMs = null,
                            endsAtMs = null,
                            internetAllowed = true,
                        ),
                        playback = PlaybackSnapshot(
                            currentPositionMs = 90_000L,
                            durationMs = 100_000L,
                            liveOffsetMs = 10_000L,
                            isPlaying = true,
                            isSeekable = true,
                        ),
                    ),
                    overlayVisible = true,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("2 · DVOJKA").assertIsDisplayed()
        compose.onNodeWithText("Večerný program").assertIsDisplayed()
        compose.onNodeWithTag("wall-clock").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
        compose.onNodeWithTag("programme-progress-marker").assertIsDisplayed()
        compose.onNodeWithTag("programme-start-boundary").assertDoesNotExist()
    }

    @Test
    fun resolvingDvojkaStateRendersStandardOverlay() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = PlayerUiState.Resolving(TvChannel.DVOJKA),
                    overlayVisible = false,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("2 · DVOJKA").assertIsDisplayed()
        compose.onNodeWithTag("switching-indicator").assertIsDisplayed()
        compose.onNodeWithTag("wall-clock").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
        compose.onNodeWithText("PREPÍNAM…").assertDoesNotExist()
    }

    @Test
    fun unavailableStateRendersStandardOverlay() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = PlayerUiState.Unavailable(
                        channel = TvChannel.JEDNOTKA,
                        program = ProgramMetadata(
                            title = "Ordinácia v Eifeli: Šance",
                            startsAtMs = null,
                            endsAtMs = 20_000L,
                            internetAllowed = false,
                        ),
                        nextRetryAtMs = 0L,
                    ),
                    overlayVisible = false,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("1 · JEDNOTKA").assertIsDisplayed()
        compose.onNodeWithText("↻").assertIsDisplayed()
        compose.onNodeWithTag("wall-clock").assertIsDisplayed()
    }

    @Test
    fun noEpgSwitchingKeepsTimelineSlotWithoutInventingMarker() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerOverlay(
                    model = PlayerOverlayModel(
                        channelLabel = "2 · DVOJKA",
                        programTitle = "",
                        progress = null,
                        displayNowMs = 2_000L,
                        programmeStartMs = null,
                        programmeNowMs = null,
                        programmeEndMs = null,
                        isLive = false,
                        isPlaying = false,
                        isSeekable = false,
                        liveActionText = "PREPÍNAM…",
                        stateIndicator = PlayerOverlayStateIndicator.SWITCHING,
                    ),
                    focusedControl = FocusedControl.TIMELINE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithTag("wall-clock").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
        compose.onNodeWithTag("seek-preview-time").assertDoesNotExist()
        compose.onNodeWithTag("programme-start-boundary").assertDoesNotExist()
        compose.onNodeWithTag("programme-progress-marker").assertDoesNotExist()
        compose.onNodeWithTag("switching-indicator").assertIsDisplayed()
        compose.onNodeWithText("PREPÍNAM…").assertDoesNotExist()
    }

    @Test
    fun readyStateHidesOverlayWhenVisibilityIsFalse() {
        val ready = PlayerUiState.Ready(
            channel = TvChannel.JEDNOTKA,
            program = ProgramMetadata("Večerný program", null, null, true),
            playback = PlaybackSnapshot(90_000L, 100_000L, 10_000L, true, true),
        )
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = ready,
                    overlayVisible = false,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("Večerný program").assertDoesNotExist()
        compose.onNodeWithTag("wall-clock").assertDoesNotExist()
    }
}
