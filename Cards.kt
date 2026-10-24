package com.ayham.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ayham.player.data.Item

@Composable
fun Poster(url: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(url.ifBlank { null }).crossfade(true).build(),
        contentDescription = null,
        contentScale = scale,
        modifier = modifier
    )
}

private val placeholder = Brush.verticalGradient(listOf(Color(0xFF2A1A52), Color(0xFF140B2A)))

@Composable
fun PosterCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    done: Boolean = false
) {
    Column(modifier.width(width)) {
        FocusBox(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { f ->
            Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                Text(
                    item.name, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp), maxLines = 4
                )
            }
            Poster(item.poster, Modifier.fillMaxSize())
            if (done) {
                Text(
                    "✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Purple)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            if (item.rating.isNotBlank() && item.rating != "0" && item.type != "live") {
                Text(
                    "★ " + item.rating.take(4), color = Color.White, fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name, color = Color.White, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun ChannelCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 190.dp) {
    Column(modifier.width(width)) {
        FocusBox(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { f ->
            Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                Poster(item.poster, Modifier.fillMaxSize().padding(18.dp), ContentScale.Fit)
            }
            Text(
                "LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Magenta)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name, color = Color.White, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp), maxLines = 1
    )
}

@Composable
fun ContentRow(
    title: String,
    list: List<Item>,
    onOpen: (Item, Int) -> Unit,
    isDone: (Item) -> Boolean = { false }
) {
    if (list.isEmpty()) return
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(list.size) { i ->
                val entry = list[i]
                if (entry.type == "live") ChannelCard(entry, { onOpen(entry, i) })
                else PosterCard(entry, { onOpen(entry, i) }, done = isDone(entry))
            }
        }
    }
}
