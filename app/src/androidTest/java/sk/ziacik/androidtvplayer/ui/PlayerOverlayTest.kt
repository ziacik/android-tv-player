package sk.ziacik.androidtvplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cinematicOverlayExposesProgramTimelineAndActions() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerOverlay(
                    model = PlayerOverlayModel(
                        channelLabel = "JEDNOTKA · NAŽIVO",
                        programTitle = "Večerný program",
                        progress = 0.4f,
                        delayText = "−1:00",
                        isLive = false,
                        isPlaying = true,
                        isSeekable = true,
                        liveActionText = "NA LIVE",
                    ),
                    focusedControl = FocusedControl.PLAY_PAUSE,
                )
            }
        }

        compose.onNodeWithText("JEDNOTKA · NAŽIVO").assertIsDisplayed()
        compose.onNodeWithText("Večerný program").assertIsDisplayed()
        compose.onNodeWithText("−1:00").assertIsDisplayed()
        compose.onNodeWithText("↶ 10").assertIsDisplayed()
        compose.onNodeWithText("10 ↷").assertIsDisplayed()
        compose.onNodeWithText("NA LIVE").assertIsDisplayed()
        compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
    }

    @Test
    fun restrictedPanelShowsProgramAndRetryTime() {
        compose.setContent {
            AndroidTvPlayerTheme {
                RestrictedProgramPanel(
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
    fun unavailableStateRendersRestrictedPanel() {
        compose.setContent {
            AndroidTvPlayerTheme {
                PlayerStateLayer(
                    state = PlayerUiState.Unavailable(
                        ProgramMetadata(
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
    fun readyStateHidesOverlayWhenVisibilityIsFalse() {
        val ready = PlayerUiState.Ready(
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
