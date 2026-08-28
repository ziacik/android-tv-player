package sk.ziacik.androidtvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.ziacik.androidtvplayer.R

private val BrandViolet = Color(0xFF7A5CFF)
private val BrandYellow = Color(0xFFFFD84A)
private val NearBlack = Color(0xFF0C0C11)
private val FocusedBackground = BrandYellow
private val FocusedContent = NearBlack
private val ControlBackground = Color(0xFF1B1A22).copy(alpha = 0.9f)
private val TrackBackground = Color(0xFF464250).copy(alpha = 0.82f)
private val MutedWhite = Color.White.copy(alpha = 0.64f)
private val LiveRed = Color(0xFFFF3347)
private val ProgrammeTitleHeight = 32.dp
private val ChannelInfoMinHeight = 48.dp
private val TimelineSlotHeight = 60.dp

internal fun formatRemainingTimeLabel(currentMs: Long?, endMs: Long?): String? {
    if (currentMs == null || endMs == null) return null
    val remainingMs = (endMs - currentMs).coerceAtLeast(0L)
    val minutes = remainingMs / 60_000L + if (remainingMs % 60_000L == 0L) 0L else 1L
    return "$minutes min"
}

@Composable
fun PlayerOverlay(
    model: PlayerOverlayModel,
    focusedControl: FocusedControl,
    timelineFocused: Boolean = false,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    seekPreviewMs: Long? = null,
    formatSeekTime: (Long) -> String = formatTime,
) {
    val channelParts = model.channelLabel.split("    ", limit = 2)
    val channelName = channelParts.firstOrNull().orEmpty()
    val streamInfo = channelParts.getOrNull(1).orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.38f to Color.Transparent,
                    0.7f to NearBlack.copy(alpha = 0.5f),
                    1f to NearBlack.copy(alpha = 0.97f),
                ),
            ),
    ) {
        BrandMark(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 36.dp, top = 30.dp),
        )

        Clock(
            text = formatTime(model.displayNowMs),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 36.dp, top = 30.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ChannelInfoMinHeight),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = channelName,
                        color = BrandYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = streamInfo,
                        color = MutedWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgrammeTitleHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = model.programTitle,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimelineSlotHeight),
            ) {
                if (model.progress != null) {
                    LiveTimeline(
                        progress = model.progress,
                        programmeStartMs = model.programmeStartMs,
                        programmeNowMs = model.programmeNowMs,
                        programmeEndMs = model.programmeEndMs,
                        seekPreviewMs = seekPreviewMs,
                        formatTime = formatTime,
                        formatSeekTime = formatSeekTime,
                        focused = timelineFocused,
                    )
                } else {
                    EmptyTimeline(
                        seekPreviewMs = seekPreviewMs,
                        formatSeekTime = formatSeekTime,
                        focused = timelineFocused,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            TransportControls(
                model = model,
                focusedControl = focusedControl,
            )
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kanalik_wordmark),
        contentDescription = null,
        modifier = modifier
            .width(118.dp)
            .height(35.dp)
            .testTag("brand-mark"),
    )
}

@Composable
private fun Clock(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("wall-clock"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(2.dp)
                .background(BrandYellow, CircleShape),
        )
    }
}

internal fun shouldShowProgramTimeline(progress: Float?): Boolean = true

@Composable
private fun EmptyTimeline(
    seekPreviewMs: Long?,
    formatSeekTime: (Long) -> String,
    focused: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineSlotHeight)
            .testTag("live-window-progress"),
    ) {
        val trackHeight = if (focused) 7.dp else 5.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = (-8).dp)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(TrackBackground.copy(alpha = 0.58f)),
        )

        seekPreviewMs?.let { previewMs ->
            SeekPreview(
                text = formatSeekTime(previewMs),
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun LiveTimeline(
    progress: Float?,
    programmeStartMs: Long?,
    programmeNowMs: Long?,
    programmeEndMs: Long?,
    seekPreviewMs: Long?,
    formatTime: (Long) -> String,
    formatSeekTime: (Long) -> String,
    focused: Boolean,
) {
    val clampedProgress = progress?.coerceIn(0f, 1f) ?: return
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 240),
        label = "programme-progress",
    )
    val remainingLabel = formatRemainingTimeLabel(programmeNowMs, programmeEndMs)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineSlotHeight)
            .testTag("live-window-progress"),
    ) {
        val markerSize = if (focused) 15.dp else 13.dp
        val trackHeight = if (focused) 7.dp else 5.dp
        val markerOffset = (maxWidth - markerSize) * animatedProgress

        programmeStartMs?.let { startMs ->
            Text(
                text = formatTime(startMs),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .testTag("programme-start-boundary"),
                color = MutedWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        remainingLabel?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("programme-time-remaining"),
                color = MutedWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = (-8).dp)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(TrackBackground),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = (-8).dp)
                .fillMaxWidth(animatedProgress)
                .height(trackHeight)
                .clip(CircleShape)
                .background(BrandViolet),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = markerOffset, y = (-4).dp)
                .size(markerSize)
                .shadow(if (focused) 8.dp else 5.dp, CircleShape)
                .background(BrandYellow, CircleShape)
                .testTag("programme-progress-marker"),
        )

        seekPreviewMs?.let { previewMs ->
            val previewWidth = 94.dp
            val previewOffset = (markerOffset + markerSize / 2 - previewWidth / 2)
                .coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            SeekPreview(
                text = formatSeekTime(previewMs),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = previewOffset, y = 16.dp),
            )
        }
    }
}

@Composable
private fun SeekPreview(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(94.dp)
            .height(26.dp)
            .background(Color(0xFF17111F).copy(alpha = 0.96f), RoundedCornerShape(8.dp))
            .border(1.dp, BrandYellow.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .testTag("seek-preview-time"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = BrandYellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TransportControls(
    model: PlayerOverlayModel,
    focusedControl: FocusedControl,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ControlButton(
            text = "↶",
            enabled = model.isSeekable,
            focused = false,
        )
        ControlButton(
            text = if (model.isPlaying) "Ⅱ" else "▶",
            enabled = true,
            focused = focusedControl == FocusedControl.PLAY_PAUSE,
        )
        ControlButton(
            text = "↷",
            enabled = model.isSeekable,
            focused = false,
        )
        Spacer(Modifier.weight(1f))
        when (model.stateIndicator) {
            PlayerOverlayStateIndicator.LIVE -> LivePill(
                text = model.liveActionText,
                isLive = model.isLive,
                focused = focusedControl == FocusedControl.LIVE,
            )
            PlayerOverlayStateIndicator.SWITCHING -> Box(
                modifier = Modifier
                    .size(40.dp)
                    .testTag("switching-indicator"),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = BrandYellow,
                    strokeWidth = 2.dp,
                )
            }
            PlayerOverlayStateIndicator.RETRYING -> LivePill(
                text = "↻",
                isLive = false,
                focused = focusedControl == FocusedControl.LIVE,
            )
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    enabled: Boolean,
    focused: Boolean,
) {
    val background = if (focused) FocusedBackground else ControlBackground
    val content = when {
        focused -> FocusedContent
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .scale(if (focused) 1.08f else 1f)
            .then(if (focused) Modifier.shadow(8.dp, CircleShape) else Modifier)
            .size(40.dp)
            .background(background, CircleShape)
            .border(
                width = 1.dp,
                color = if (focused) BrandYellow else Color.White.copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LivePill(
    text: String,
    isLive: Boolean,
    focused: Boolean,
) {
    val accent = if (isLive) LiveRed else BrandYellow
    val background = when {
        focused -> FocusedBackground
        isLive -> Color(0xFF2A1115).copy(alpha = 0.9f)
        else -> Color(0xFF21172F).copy(alpha = 0.9f)
    }

    Row(
        modifier = Modifier
            .scale(if (focused) 1.06f else 1f)
            .then(if (focused) Modifier.shadow(8.dp, RoundedCornerShape(20.dp)) else Modifier)
            .height(40.dp)
            .background(background, RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (focused) BrandYellow else accent.copy(alpha = 0.78f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(LiveRed, CircleShape),
            )
        }
        Text(
            text = text,
            color = if (focused) FocusedContent else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}
