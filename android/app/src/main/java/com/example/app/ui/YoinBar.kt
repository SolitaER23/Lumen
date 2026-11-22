package com.example.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gpo.yoin.symbols.YoinSymbols

/** 底部迷你播放条：封面 + 曲名 / 艺人 + 播放键。 */
@Composable
fun YoinBar(
    modifier: Modifier = Modifier,
    pulseKey: Int = 0,
    title: String,
    artist: String,
    coverUrl: String,
    playing: Boolean,
    enabled: Boolean,
    onHome: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        pulse.snapTo(1.02f)
        pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 480f))
    }
    Row(
        modifier.fillMaxWidth().graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        }.padding(horizontal = 12.dp).height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HomeCard)
                .clickable(enabled = enabled, onClick = onOpenPlayer)
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enabled) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    },
                    label = "track",
                    modifier = Modifier.weight(1f),
                ) { t ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = HomeText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            artist, fontSize = 11.sp, color = HomeSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    "还没有播放",
                    color = HomeSub,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = enabled, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color(0xFF202126),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
