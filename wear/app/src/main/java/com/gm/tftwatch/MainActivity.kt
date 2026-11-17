package com.gm.tftwatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tiles.TileService

class MainActivity : ComponentActivity() {

    private var openRank by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRank = intent.getIntExtra(EXTRA_RANK, 0)
        setContent { MaterialTheme { App() } }
    }

    // 앱이 열린 상태에서 타일의 다른 덱을 눌렀을 때
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRank = intent.getIntExtra(EXTRA_RANK, 0)
    }

    @Composable
    private fun App() {
        var data by remember { mutableStateOf(DeckRepository.cached(this)) }
        var loaded by remember { mutableStateOf(data != null) }

        LaunchedEffect(Unit) {
            DeckRepository.load(this@MainActivity)?.let { data = it }
            loaded = true
            // 새 데이터를 받았으니 타일도 갱신
            TileService.getUpdater(this@MainActivity).requestUpdate(DeckTileService::class.java)
        }

        val decks = data?.decks.orEmpty()
        val selected = decks.firstOrNull { it.rank == openRank }
        BackHandler(enabled = selected != null) { openRank = 0 }

        Scaffold(timeText = { TimeText() }) {
            ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
                when {
                    selected != null -> deckDetail(selected)
                    decks.isEmpty() -> item {
                        Text(if (loaded) "데이터를 불러오지 못했어요" else "불러오는 중…", textAlign = TextAlign.Center)
                    }
                    else -> deckList(decks, data?.updatedAt.orEmpty()) { openRank = it }
                }
            }
        }
    }

    private fun ScalingLazyListScope.deckList(decks: List<Deck>, updatedAt: String, onClick: (Int) -> Unit) {
        item { ListHeader { Text("TFT 덱 순위", color = GOLD, fontWeight = FontWeight.Bold) } }
        decks.forEach { d ->
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onClick(d.rank) },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = {
                        Text("${d.rank}. ${d.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            "${d.tier.ifBlank { "-" }} · " + d.carries.joinToString(", ") { it.name },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = Color(DeckTileService.tierColor(d.tier))
                        )
                    },
                )
            }
        }
        item { Text("업데이트 $updatedAt", fontSize = 11.sp, color = Color.Gray) }
    }

    private fun ScalingLazyListScope.deckDetail(d: Deck) {
        item {
            Text(
                "${d.rank}. ${d.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // 롤체지지 추천 덱에는 S/A 티어가 없고 HOT 태그만 있어요
        if (d.tier.isNotBlank()) {
            val label = if (d.tier.length <= 2) "${d.tier} 티어" else d.tier
            item { Text(label, color = Color(DeckTileService.tierColor(d.tier)), fontSize = 12.sp) }
        }

        // 캐리 + 아이템
        item { Section("캐리 아이템") }
        val carries = d.carries.ifEmpty { d.units.filter { it.items.isNotEmpty() } }
        if (carries.isEmpty()) item { Body("아이템 정보 없음") }
        carries.forEach { u ->
            item {
                Column2(
                    title = u.name + (u.cost?.let { " (${it}코)" } ?: ""),
                    body = u.items.joinToString("\n") { "· $it" }
                )
            }
        }

        // 초반 빌드업
        item { Section("초반 빌드업") }
        if (d.early.isEmpty()) item { Body("초반 빌드 정보 없음") }
        d.early.forEach { s ->
            item { Column2(title = s.label, body = s.units.joinToString(", ")) }
        }

        // 최종 배치
        item { Section("최종 덱") }
        item { Body(d.units.joinToString(", ") { it.name }) }
    }

    @Composable private fun Section(t: String) =
        Text(t, color = GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

    @Composable private fun Body(t: String) =
        Text(t, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp))

    @Composable private fun Column2(title: String, body: String) =
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(body, fontSize = 12.sp, color = Color(0xFFCFCFCF))
        }

    companion object {
        const val EXTRA_RANK = "rank"
        private val GOLD = Color(0xFFF2C14E)
    }
}
