package com.music.bitchord.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.music.bitchord.data.model.Song

fun Modifier.thumbnailBorder(shape: Shape): Modifier = composed {
    this.border(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape
    )
}

@Composable
fun ExplicitBadge(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("E", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color,
            modifier = Modifier.border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp))
    }
}

/** A song title with the catalogue-standard outlined E for explicit audio. */
@Composable
fun ExplicitSongTitle(
    song: Song,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    ExplicitSongTitle(
        title = song.title,
        isExplicit = song.isExplicit,
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
    )
}

/** The same catalogue-standard explicit title treatment for lightweight song rows. */
@Composable
fun ExplicitSongTitle(
    title: String,
    isExplicit: Boolean?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isExplicit == true) {
            Text(
                text = "E",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier
                    .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
