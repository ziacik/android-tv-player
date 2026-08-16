package sk.ziacik.androidtvplayer.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.resolver.StreamManifest

class Media3PlayerPortTest {
    @Test
    fun `maps HLS and DASH manifests to their Media3 MIME types`() {
        assertEquals(MimeTypes.APPLICATION_M3U8, StreamManifest.HLS.mediaMimeType())
        assertEquals(MimeTypes.APPLICATION_MPD, StreamManifest.DASH.mediaMimeType())
    }
}
