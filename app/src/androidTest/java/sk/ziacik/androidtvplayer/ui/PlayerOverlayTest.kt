package sk.ziacik.androidtvplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

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
}
