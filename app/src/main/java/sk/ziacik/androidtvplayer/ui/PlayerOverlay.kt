package sk.ziacik.androidtvplayer.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun PlayerOverlay(
    model: PlayerOverlayModel,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
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
                    formatTime = formatTime,
                )
                Spacer(Modifier.height(4.dp))
            }
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
    formatTime: (Long) -> String,
) {
    val clampedProgress = progress?.coerceIn(0f, 1f) ?: return
    val startMs = programmeStartMs ?: return
    val nowMs = programmeNowMs ?: return
    val endMs = programmeEndMs ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live-window-progress"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            var currentTimeWidthPx by remember(nowMs) { mutableStateOf(0) }
            val markerSize = 8.dp
            val markerOffset = (maxWidth - markerSize) * clampedProgress
            val markerCenterPx = with(density) {
                markerOffset.toPx() + markerSize.toPx() / 2f
            }
            val labelOffsetPx = (markerCenterPx - currentTimeWidthPx / 2f)
                .coerceIn(
                    minimumValue = 0f,
                    maximumValue = with(density) {
                        (maxWidth.toPx() - currentTimeWidthPx).coerceAtLeast(0f)
                    },
                )

            Text(
                text = formatTime(nowMs),
                modifier = Modifier.offset(x = with(density) { labelOffsetPx.toDp() })
                    .align(Alignment.TopStart),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                onTextLayout = { currentTimeWidthPx = it.size.width },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.26f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clampedProgress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = markerOffset)
                    .size(markerSize)
                    .shadow(5.dp, CircleShape)
                    .background(Color.White, CircleShape),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(startMs),
                color = MutedWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(endMs),
                color = MutedWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
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
        LivePill(
            text = model.liveActionText,
            isLive = model.isLive,
            focused = focusedControl == FocusedControl.LIVE,
        )
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

@Composable
fun RestrictedProgramPanel(
    channelLabel: String,
    programTitle: String,
    retryTime: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .background(Color(0xE6111114), RoundedCornerShape(22.dp))
            .padding(horizontal = 42.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = channelLabel,
            color = MutedWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(LiveRed, CircleShape),
        )
        Text(
            text = "Tento program nie je dostupný online",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = programTitle,
            color = MutedWhite,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = retryTime?.let { "Vysielanie skúsime obnoviť o $it" }
                ?: "Vysielanie budeme skúšať obnoviť automaticky",
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(40.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp))
                .background(FocusedBackground, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Skúsiť znova",
                color = FocusedContent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
