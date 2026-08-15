package sk.ziacik.androidtvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerOverlay(
    isPlaying: Boolean,
    isSeekable: Boolean,
    focusedControl: FocusedControl,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.9f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 64.dp, vertical = 44.dp),
        ) {
            Text(
                text = "Jednotka",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(15.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                ControlChip(
                    text = "−10",
                    enabled = isSeekable,
                    focused = false,
                )
                Spacer(Modifier.width(22.dp))
                ControlChip(
                    text = if (isPlaying) "Ⅱ" else "▶",
                    enabled = true,
                    focused = focusedControl == FocusedControl.PLAY_PAUSE,
                )
                Spacer(Modifier.width(22.dp))
                ControlChip(
                    text = "+10",
                    enabled = isSeekable,
                    focused = false,
                )
                Spacer(Modifier.weight(1f))
                ControlChip(
                    text = "LIVE",
                    enabled = true,
                    focused = focusedControl == FocusedControl.LIVE,
                )
            }
        }
    }
}

@Composable
private fun ControlChip(
    text: String,
    enabled: Boolean,
    focused: Boolean,
) {
    val background = when {
        focused -> MaterialTheme.colorScheme.primary
        else -> Color.White.copy(alpha = 0.14f)
    }
    val content = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        modifier = Modifier
            .scale(if (focused) 1.08f else 1f)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 25.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

