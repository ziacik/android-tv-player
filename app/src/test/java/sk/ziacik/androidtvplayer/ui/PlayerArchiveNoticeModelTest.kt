package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerArchiveNoticeModelTest {
	@Test
	fun `archive failure notice does not replace programme title`() {
		val state = PlayerUiState.Ready(
			channel = TvChannel.JEDNOTKA,
			program = ProgramMetadata(
				title = "Večerný program",
				startsAtMs = 0L,
				endsAtMs = 100_000L,
				internetAllowed = true,
			),
			playback = PlaybackSnapshot(
				currentPositionMs = 90_000L,
				durationMs = 100_000L,
				liveOffsetMs = 10_000L,
				isSeekable = true,
				isPlaying = true,
			),
			noticeText = "Program nie je dostupný v archíve",
		)

		val model = PlayerOverlayModel.from(state, nowMs = 90_000L)

		assertEquals("Večerný program", model.programTitle)
		assertEquals("Program nie je dostupný v archíve", model.noticeText)
	}
}
