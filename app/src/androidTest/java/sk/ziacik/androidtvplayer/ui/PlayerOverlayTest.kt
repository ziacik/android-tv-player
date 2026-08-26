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
    fun cinematicOverlayExposesProgramTimelineAndActions() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerOverlay(
                    model = PlayerOverlayModel(
                        channelLabel = "1 · JEDNOTKA · NAŽIVO",
                        programTitle = "Večerný program",
                        progress = 0.4f,
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
                            3_000L to "21:20",
                        ).getValue(milliseconds)
                    },
                )
            }
        }

        compose.onNodeWithText("1 · JEDNOTKA · NAŽIVO").assertIsDisplayed()
        compose.onNodeWithText("Večerný program").assertIsDisplayed()
        compose.onNodeWithText("20:15").assertIsDisplayed()
        compose.onNodeWithText("20:42").assertIsDisplayed()
        compose.onNodeWithText("21:20").assertIsDisplayed()
        compose.onNodeWithText("↶ 10").assertIsDisplayed()
        compose.onNodeWithText("10 ↷").assertIsDisplayed()
        compose.onNodeWithText("NAŽIVO").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
    }

    @Test
    fun restrictedPanelShowsProgramAndRetryTime() {
        compose.setContent {
            AndroidTvPlayerTheme {
                RestrictedProgramPanel(
                    channelLabel = "JEDNOTKA",
                    programTitle = "Ordinácia v Eifeli: Šance",
                    retryTime = "12:54",
                )
            }
        }

        compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
        compose.onNodeWithText("Ordinácia v Eifeli: Šance").assertIsDisplayed()
        compose.onNodeWithText("Vysielanie skúsime obnoviť o 12:54").assertIsDisplayed()
        compose.onNodeWithText("Skúsiť znova").assertIsDisplayed()
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

        compose.onNodeWithText("2 · DVOJKA · NAŽIVO").assertIsDisplayed()
        compose.onNodeWithText("Večerný program").assertIsDisplayed()
    }

    @Test
    fun resolvingDvojkaStateShowsChannelWhileOverlayIsHidden() {
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

        compose.onNodeWithText("DVOJKA · NAČÍTAVAM").assertIsDisplayed()
    }

    @Test
    fun unavailableStateRendersRestrictedPanel() {
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
                    ),
                    overlayVisible = false,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
        compose.onNodeWithText("Vysielanie skúsime obnoviť o 12:54").assertIsDisplayed()
    }

    @Test
    fun unavailableDvojkaStateRendersChannelRestrictionAndRetryTime() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = PlayerUiState.Unavailable(
                        channel = TvChannel.DVOJKA,
                        program = ProgramMetadata(
                            title = "Ordinácia v Eifeli: Šance",
                            startsAtMs = null,
                            endsAtMs = 20_000L,
                            internetAllowed = false,
                        ),
                    ),
                    overlayVisible = false,
                    focusedControl = FocusedControl.PLAY_PAUSE,
                    formatTime = { "12:54" },
                )
            }
        }

        compose.onNodeWithText("DVOJKA").assertIsDisplayed()
        compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
        compose.onNodeWithText("Vysielanie skúsime obnoviť o 12:54").assertIsDisplayed()
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
    }
}
