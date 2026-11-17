package com.gm.tftwatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
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

        // openRank = 모든 섹션을 이어 붙인 목록에서의 순번(1부터). 타일의 1~5위는 첫 섹션이라 그대로 맞아요.
        val sections = data?.sections.orEmpty()
        val decks = sections.flatMap { it.decks }
        val selected = decks.getOrNull(openRank - 1)
        val icons = data?.icons.orEmpty()
        // 초반 빌드업에는 이름만 있어서, 모든 덱의 유닛에서 코스트를 찾아 둬요
        val costs = remember(data) {
            decks.flatMap { it.units + it.carries }.mapNotNull { u -> u.cost?.let { u.name to it } }.toMap()
        }
        BackHandler(enabled = selected != null) { openRank = 0 }

        Scaffold(timeText = { TimeText() }) {
            ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
                when {
                    selected != null -> deckDetail(selected, icons, costs)
                    decks.isEmpty() -> item {
                        Text(if (loaded) "데이터를 불러오지 못했어요" else "불러오는 중…", textAlign = TextAlign.Center)
                    }
                    else -> deckList(sections, data?.updatedAt.orEmpty()) { openRank = it }
                }
            }
        }
    }

    private fun ScalingLazyListScope.deckList(sections: List<DeckSection>, updatedAt: String, onClick: (Int) -> Unit) {
        var index = 0
        sections.forEach { s ->
            item { ListHeader { Text(s.title, color = GOLD, fontWeight = FontWeight.Bold) } }
            s.decks.forEach { d ->
                val position = ++index
                deckChip(d) { onClick(position) }
            }
        }
        item { Text("업데이트 $updatedAt", fontSize = 11.sp, color = Color.Gray) }
    }

    private fun ScalingLazyListScope.deckChip(d: Deck, onClick: () -> Unit) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick,
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

    private fun ScalingLazyListScope.deckDetail(d: Deck, icons: Map<String, String>, costs: Map<String, Int>) {
        item {
            Text(
                "${d.rank}. ${d.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (d.tier.isNotBlank()) {
            val label = if (d.tier.length <= 2) "${d.tier} 티어" else d.tier
            item { Text(label, color = Color(DeckTileService.tierColor(d.tier)), fontSize = 12.sp) }
        }
        if (d.stat.isNotBlank()) item { Body(d.stat) }

        // 캐리 + 아이템
        item { Section("캐리 아이템") }
        val carries = d.carries.ifEmpty { d.units.filter { it.items.isNotEmpty() } }
        if (carries.isEmpty()) item { Body("아이템 정보 없음") }
        carries.forEach { u -> item { CarryRow(u, icons) } }

        // 초반 빌드업
        item { Section("초반 빌드업") }
        if (d.early.isEmpty()) item { Body("초반 빌드 정보 없음") }
        d.early.forEach { s ->
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Portraits(s.units.map { it to costs[it] }, icons)
                }
            }
        }

        // 최종 배치
        item { Section("최종 덱") }
        item { Portraits(d.units.map { it.name to it.cost }, icons) }
    }

    /** 캐리 한 명: 초상화 + 이름 + 아이템 아이콘 3개 */
    @Composable private fun CarryRow(u: Champ, icons: Map<String, String>) =
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val portrait = icons[u.name]
            if (portrait != null) Icon(u.name, portrait, 38.dp, costColor(u.cost))
            Column(Modifier.padding(start = if (portrait != null) 8.dp else 0.dp)) {
                Text(u.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (u.items.all { icons[it] != null }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                        u.items.forEach { Icon(it, icons[it], 24.dp, ITEM_BORDER) }
                    }
                } else {
                    Text(u.items.joinToString("\n") { "· $it" }, fontSize = 12.sp, color = Color(0xFFCFCFCF))
                }
            }
        }

    /** 챔피언 초상화 여러 개를 줄바꿈하며 가운데 정렬 */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Portraits(units: List<Pair<String, Int?>>, icons: Map<String, String>) =
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            units.forEach { (name, cost) -> Icon(name, icons[name], 30.dp, costColor(cost)) }
        }

    /** 이미지 주소가 없으면 이름을 글자로 보여줘요 */
    @Composable private fun Icon(name: String, url: String?, size: Dp, border: Color) {
        if (url == null) {
            Text(name, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
        } else {
            val shape = RoundedCornerShape(5.dp)
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape).background(Color(0xFF2A2A2A)).border(1.5.dp, border, shape)
            )
        }
    }

    @Composable private fun Section(t: String) =
        Text(t, color = GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

    @Composable private fun Body(t: String) =
        Text(t, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp))

    companion object {
        const val EXTRA_RANK = "rank"
        private val GOLD = Color(0xFFF2C14E)
        private val ITEM_BORDER = Color(0xFF555555)

        /** 코스트별 테두리 색 (게임 내 색과 비슷하게) */
        private fun costColor(cost: Int?) = when (cost) {
            1 -> Color(0xFF9AA4AF)
            2 -> Color(0xFF3FB56B)
            3 -> Color(0xFF3F8CFF)
            4 -> Color(0xFFC04DFF)
            5, 6 -> Color(0xFFF2C14E)
            else -> Color(0xFF555555)
        }
    }
}
