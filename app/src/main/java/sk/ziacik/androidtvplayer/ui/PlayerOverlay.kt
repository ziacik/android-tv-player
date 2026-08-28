package sk.ziacik.androidtvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FocusedBackground = Color(0xFFF4F4F5)
private val FocusedContent = Color(0xFF111113)
private val GlassBackground = Color.White.copy(alpha = 0.14f)
private val MutedWhite = Color.White.copy(alpha = 0.68f)
private val LiveRed = Color(0xFFFF3347)

internal fun formatRemainingTimeLabel(currentMs: Long?, endMs: Long?): String? {
    if (currentMs == null || endMs == null) return null
    val remainingMs = (endMs - currentMs).coerceAtLeast(0L)
    val minutes = remainingMs / 60_000L + if (remainingMs % 60_000L == 0L) 0L else 1L
    return "$minutes min left"
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.42f to Color.Transparent,
                    0.7f to Color.Black.copy(alpha = 0.52f),
                    1f to Color.Black.copy(alpha = 0.94f),
                ),
            ),
    ) {
        Text(
            text = formatTime(model.displayNowMs),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(36.dp)
                .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                .padding(horizontal = 15.dp, vertical = 8.dp)
                .testTag("wall-clock"),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = model.channelLabel,
                color = MutedWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = model.programTitle,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (shouldShowProgramTimeline(model.progress)) {
                Spacer(Modifier.height(3.dp))
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
            }
            Spacer(Modifier.height(4.dp))
            TransportControls(
                model = model,
                focusedControl = focusedControl,
            )
        }
    }
}

internal fun shouldShowProgramTimeline(progress: Float?): Boolean = progress != null

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live-window-progress"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        ) {
            val markerSize = if (focused) 14.dp else 12.dp
            val trackHeight = if (focused) 8.dp else 6.dp
            val markerOffset = (maxWidth - markerSize) * animatedProgress

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = (-3).dp)
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = (-3).dp)
                    .fillMaxWidth(animatedProgress)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.94f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = markerOffset)
                    .size(markerSize)
                    .shadow(if (focused) 7.dp else 5.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .testTag("programme-progress-marker"),
            )

            seekPreviewMs?.let { previewMs ->
                val previewWidth = 94.dp
                val previewOffset = (markerOffset + markerSize / 2 - previewWidth / 2)
                    .coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = previewOffset)
                        .width(previewWidth)
                        .height(25.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                        .testTag("seek-preview-time"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatSeekTime(previewMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            programmeStartMs?.let { startMs ->
                Text(
                    text = formatTime(startMs),
                    modifier = Modifier.testTag("programme-start-boundary"),
                    color = MutedWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            remainingLabel?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier.testTag("programme-time-remaining"),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlPill(
            text = "↶ 10",
            enabled = model.isSeekable,
            focused = false,
        )
        ControlPill(
            text = if (model.isPlaying) "Ⅱ" else "▶",
            enabled = true,
            focused = focusedControl == FocusedControl.PLAY_PAUSE,
            compact = true,
        )
        ControlPill(
            text = "10 ↷",
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
                    color = Color.White,
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
private fun ControlPill(
    text: String,
    enabled: Boolean,
    focused: Boolean,
    compact: Boolean = false,
) {
    val background = if (focused) FocusedBackground else GlassBackground
    val content = when {
        focused -> FocusedContent
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.34f)
    }

    Box(
        modifier = Modifier
            .scale(if (focused) 1.06f else 1f)
            .then(if (focused) Modifier.shadow(8.dp, RoundedCornerShape(22.dp)) else Modifier)
            .height(40.dp)
            .background(background, RoundedCornerShape(22.dp))
            .padding(horizontal = if (compact) 20.dp else 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LivePill(
    text: String,
    isLive: Boolean,
    focused: Boolean,
) {
    Row(
        modifier = Modifier
            .scale(if (focused) 1.06f else 1f)
            .then(if (focused) Modifier.shadow(8.dp, RoundedCornerShape(22.dp)) else Modifier)
            .height(40.dp)
            .background(
                if (focused) FocusedBackground else GlassBackground,
                RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 17.dp),
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
