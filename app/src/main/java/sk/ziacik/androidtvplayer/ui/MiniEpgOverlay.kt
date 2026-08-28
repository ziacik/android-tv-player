package sk.ziacik.androidtvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MiniEpgBrandViolet = Color(0xFF7A5CFF)
private val MiniEpgBrandYellow = Color(0xFFFFD84A)
private val MiniEpgNearBlack = Color(0xFF0C0C11)
private val MiniEpgMuted = Color.White.copy(alpha = 0.58f)
private val MiniEpgTrack = Color.White.copy(alpha = 0.16f)

@Composable
fun MiniEpgOverlay(
    rows: List<MiniEpgRow>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to MiniEpgNearBlack.copy(alpha = 0.94f),
                    0.42f to MiniEpgNearBlack.copy(alpha = 0.80f),
                    0.76f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
            .testTag("mini-epg"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(660.dp)
                .padding(start = 44.dp, end = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "TERAZ V TV",
                color = MiniEpgBrandYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
            )
            rows.forEach { row ->
                MiniEpgRowItem(row)
            }
            Text(
                text = "↑ ↓ vybrať    OK prepnúť    BACK zavrieť",
                color = MiniEpgMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun MiniEpgRowItem(row: MiniEpgRow) {
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        row.isSelected -> MiniEpgBrandViolet.copy(alpha = 0.86f)
        row.isCurrent -> Color.White.copy(alpha = 0.10f)
        else -> Color.Black.copy(alpha = 0.28f)
    }
    val borderColor = if (row.isSelected) {
        MiniEpgBrandYellow.copy(alpha = 0.92f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(if (row.isSelected) "mini-epg-selected" else "mini-epg-row-${row.channelNumber}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.channelNumber.toString().padStart(2, '0'),
            color = if (row.isSelected) MiniEpgBrandYellow else MiniEpgMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(38.dp),
        )
        Column(
            modifier = Modifier.width(154.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.channel.displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.isCurrent) {
                Text(
                    text = "● LIVE",
                    color = MiniEpgBrandYellow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = row.programmeTitle.ifBlank { "Bez EPG" },
                color = if (row.programmeTitle.isBlank()) MiniEpgMuted else Color.White,
                fontSize = 15.sp,
                fontWeight = if (row.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MiniEpgProgress(row.progress)
        }
    }
}

@Composable
private fun MiniEpgProgress(progress: Float?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MiniEpgTrack),
    ) {
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MiniEpgBrandYellow),
            )
        }
    }
}
