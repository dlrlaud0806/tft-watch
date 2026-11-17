package com.gm.tftwatch

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** 시계 화면을 옆으로 넘기면 보이는 "TFT 덱 순위" 타일. 덱을 누르면 상세 화면이 열려요. */
class DeckTileService : TileService() {

    override fun onTileRequest(req: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val data = DeckRepository.load(this@DeckTileService)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(60 * 60 * 1000L) // 1시간마다 새로고침
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(data)))
                .build()
        }

    override fun onTileResourcesRequest(req: RequestBuilders.ResourcesRequest):
        ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
        }

    private fun layout(data: DeckData?): LayoutElementBuilders.LayoutElement {
        val col = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)

        col.addContent(text(data?.title?.ifBlank { null } ?: "TFT 덱 순위", 13f, GOLD, bold = true))
        col.addContent(Spacer.Builder().setHeight(dp(4f)).build())

        if (data == null || data.decks.isEmpty()) {
            col.addContent(text("데이터를 불러오지 못했어요", 12f, GRAY))
        } else {
            data.decks.take(5).forEach { col.addContent(deckRow(it)) }
            col.addContent(Spacer.Builder().setHeight(dp(4f)).build())
            col.addContent(text("업데이트 ${data.updatedAt}", 10f, GRAY))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder()
                        .setStart(dp(30f)).setEnd(dp(22f)).setTop(dp(24f)).setBottom(dp(18f)).build())
                    .build()
            )
            .addContent(col.build())
            .build()
    }

    private fun deckRow(d: Deck): LayoutElementBuilders.LayoutElement =
        Row.Builder()
            .setWidth(expand()).setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openDeck(d.rank))
                    .setPadding(ModifiersBuilders.Padding.Builder().setTop(dp(2f)).setBottom(dp(2f)).build())
                    .build()
            )
            .addContent(text("${d.rank}", 13f, WHITE, bold = true))
            .addContent(Spacer.Builder().setWidth(dp(5f)).build())
            .addContent(text(d.tier.ifBlank { "-" }, 13f, tierColor(d.tier), bold = true))
            .addContent(Spacer.Builder().setWidth(dp(6f)).build())
            .addContent(text(d.name, 13f, WHITE))
            .build()

    private fun openDeck(rank: Int) = ModifiersBuilders.Clickable.Builder()
        .setId("deck_$rank")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping(
                            MainActivity.EXTRA_RANK,
                            ActionBuilders.AndroidIntExtra.Builder().setValue(rank).build()
                        )
                        .build()
                )
                .build()
        )
        .build()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        Text.Builder()
            .setText(s)
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    companion object {
        private const val RES_VERSION = "1"
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GRAY = 0xFF9E9E9E.toInt()
        private const val GOLD = 0xFFF2C14E.toInt()
        fun tierColor(t: String) = when (t.uppercase()) {
            "OP", "HOT" -> 0xFFFF5C8A.toInt()
            "S" -> 0xFFFF8A3D.toInt()
            "A" -> 0xFFF2C14E.toInt()
            "B" -> 0xFF5CC8FF.toInt()
            else -> GRAY
        }
    }
}
