package cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bilibili.community.service.dm.v1.CommandDm
import bilibili.community.service.dm.v1.DanmakuElem
import bilibili.community.service.dm.v1.DmColorfulType
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.COMMAND_ADVERTISE
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.COMMAND_RATE
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.COMMAND_SUBSCRIBE
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.COMMAND_VIDEO
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.COMMAND_VOTE
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.CommandDanmaku
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.DanmakuSegment
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.DisplayDanmakuItem
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.command.AdvertiseExtra
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.command.RateExtra
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.command.SubscribeExtra
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.command.VideoExtra
import cn.spacexc.wearbili.remake.app.player.videoplayer.danmaku.compose.data.command.vote.VoteExtra
import com.google.gson.Gson

/**
 * Created by XC-Qan on 2023/12/10.
 * I'm very cute so please be nice to my code!
 * 给！爷！写！注！释！
 * 给！爷！写！注！释！
 * 给！爷！写！注！释！
 */

/**
 * 因为普通弹幕对应的类型数字有1 2 3，可以说是为了一碗醋包了一锅饺子了
 **/
class DanmakuType(vararg types: Int) {
    private val typeInts = types
    infix fun isEqualTo(other: Int): Boolean {
        return typeInts.contains(other)
    }

    infix fun isNotEqualTo(other: Int): Boolean {
        return !typeInts.contains(other)
    }
}

val DANMAKU_TYPE_NORM = DanmakuType(1, 2, 3)
val DANMAKU_TYPE_REVERSED = DanmakuType(6)
val DANMAKU_TYPE_TOP = DanmakuType(5)
val DANMAKU_TYPE_BOTTOM = DanmakuType(4)
val DANMAKU_TYPE_ADVANCE = DanmakuType(7)
val DANMAKU_TYPE_SCRIPT = DanmakuType(8)

@Composable
fun rememberDanmakuCanvasState(updateTimer: () -> Long) = remember {
    DanmakuCanvasState(updateTimer)
}

class DanmakuCanvasState(val updateTimer: () -> Long) {
    enum class DanmakuCanvasState {
        Idle,
        Playing,
        Paused
        //Completed
    }

    private var danmakuSegments = emptyList<DanmakuSegment>()

    var displayingDanmakus by mutableStateOf(listOf<DisplayDanmakuItem>())

    var state = DanmakuCanvasState.Idle

    private var topDisplayRows = HashMap<Int, List<DisplayDanmakuItem>>()
    private var bottomDisplayRows = HashMap<Int, List<DisplayDanmakuItem>>()

    private var dynamicDanmakuSegments = emptyList<DanmakuSegment>()

    var commandDanmakus by mutableStateOf(listOf<CommandDanmaku>())   //引导关注/推荐视频等
    private var imageDanmakus = mapOf<List<String>, ImageBitmap>()  //如"前方高能"/"ohhhhh"等


    fun start() {
        state = DanmakuCanvasState.Playing
    }

    fun pause() {
        state = DanmakuCanvasState.Paused
    }

    fun seekTo(time: Long) {
        dynamicDanmakuSegments = buildList {
            danmakuSegments.forEach { segment ->
                val newSegmentList = segment.danmakuList.filter { it.progress >= time }
                add(segment.copy(danmakuList = newSegmentList))
            }
        }
        displayingDanmakus = emptyList()
        topDisplayRows = HashMap()
        bottomDisplayRows = HashMap()
    }

    /**
     * 更新列表中的弹幕
     */
    fun updatedDanmaku(
        textMeasurer: TextMeasurer,
        drawScope: DrawScope,
        fontSize: Float,
        playSpeed: Float,
    ) {
        if (state != DanmakuCanvasState.Playing || danmakuSegments.isEmpty()) return
        drawScope.apply {
            var tempList = displayingDanmakus.toMutableList()
            //寻找新的可以被显示的弹幕，要求为 出现时间 > 当前计时器时间 且 不是高级弹幕/脚本弹幕
            val currentSegmentIndex = (updateTimer() / (6 * 60 * 1000)).toInt() + 1
            val currentList =
                dynamicDanmakuSegments.firstOrNull { it.segmentIndex == currentSegmentIndex }?.danmakuList
                    ?: emptyList()
            val newVisibleDanmakus =
                currentList.filter { danmakuItem ->
                    danmakuItem.progress <= updateTimer()
                            && DANMAKU_TYPE_ADVANCE isNotEqualTo danmakuItem.mode
                            && DANMAKU_TYPE_SCRIPT isNotEqualTo danmakuItem.mode
                    //&& DANMAKU_TYPE_NORM isEqualTo danmakuItem.mode
                    //&& danmakuItem.weight > 0   //屏蔽等级
                    //TODO 显示高级弹幕
                }.map { danmakuItem ->
                    //用来获取文字的大小
                    val isHighlyLiked = danmakuItem.attr == 4 //貌似4和0按位与是2（2=高赞）
                    val isGradient = danmakuItem.colorful == DmColorfulType.VipGradualColor
                    val imageBitmap = getImageForExpression(danmakuItem.content)
                    val textLayoutResult: TextLayoutResult =
                        textMeasurer.measure(
                            text = AnnotatedString(danmakuItem.content),
                            style = TextStyle(
                                fontSize = (danmakuItem.fontsize * 0.5 * fontSize).sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    val textSize = textLayoutResult.size

                    val row = getRow(danmakuItem, textSize.width, drawScope)

                    val newItem = DisplayDanmakuItem(
                        appearTime = danmakuItem.progress,
                        content = AnnotatedString(danmakuItem.content),
                        x = when (parseDanmakuType(danmakuItem.mode)) {
                            DANMAKU_TYPE_NORM -> if (isHighlyLiked) size.width + textSize.height else size.width
                            DANMAKU_TYPE_REVERSED -> 0f - textSize.width
                            DANMAKU_TYPE_TOP, DANMAKU_TYPE_BOTTOM -> (size.width / 2) - (textSize.width / 2)
                            else -> 0f
                        },
                        y = when (parseDanmakuType(danmakuItem.mode)) {
                            DANMAKU_TYPE_BOTTOM -> size.height - ((row + 1) * textSize.height.toFloat())
                            else -> row * textSize.height.toFloat()
                        },
                        color = Color(danmakuItem.color).copy(alpha = 1f),
                        fontSize = danmakuItem.fontsize,
                        type = danmakuItem.mode,
                        danmakuId = danmakuItem.id,
                        displayRow = row,
                        textWidth = textSize.width,
                        textHeight = textSize.height,
                        isLiked = isHighlyLiked,
                        image = imageBitmap,
                        isGradient = isGradient
                    )
                    if (DANMAKU_TYPE_BOTTOM isEqualTo danmakuItem.mode) {
                        val danmakusInRow =
                            (bottomDisplayRows[row] ?: emptyList()).toMutableList()
                        if (!danmakusInRow.any { it.danmakuId == danmakuItem.id }) {
                            danmakusInRow.add(newItem)
                        }
                        bottomDisplayRows[row] = danmakusInRow
                    } else {
                        val danmakusInRow =
                            (topDisplayRows[row] ?: emptyList()).toMutableList()
                        if (!danmakusInRow.any { it.danmakuId == danmakuItem.id }) {
                            danmakusInRow.add(newItem)
                        }
                        topDisplayRows[row] = danmakusInRow
                    }
                    newItem
                }

            tempList.addAll(newVisibleDanmakus)
            val tempDanmakuSegmentList = currentList.toMutableList()
            tempDanmakuSegmentList.removeAll { tempDanmakuElem ->
                newVisibleDanmakus.map { it.danmakuId }.contains(tempDanmakuElem.id)
            }
            dynamicDanmakuSegments = dynamicDanmakuSegments.toMutableList().apply {
                set(
                    index = indexOfFirst { it.segmentIndex == currentSegmentIndex },
                    element = DanmakuSegment(currentSegmentIndex, tempDanmakuSegmentList)
                )
            }

            tempList = tempList.map {
                when (parseDanmakuType(it.type)) {
                    DANMAKU_TYPE_NORM -> it.copy(
                        x = it.x - ((2.5f + it.textWidth / 230) * playSpeed)
                    )

                    DANMAKU_TYPE_REVERSED -> it.copy(
                        x = it.x + ((2.5f * it.textWidth / 230) * playSpeed)
                    )
                    //顶端/底端弹幕不需要更新位置
                    else -> it
                }
            }.toMutableList()

            //新的不可见弹幕
            val newInvisibleDanmakus = displayingDanmakus.filter { displayDanmakuItem ->
                val textLayoutResult: TextLayoutResult =
                    textMeasurer.measure(
                        text = displayDanmakuItem.content,
                        style = TextStyle(fontSize = (displayDanmakuItem.fontSize * 0.55 * fontSize).sp),
                    )
                val textSize = textLayoutResult.size
                when (parseDanmakuType(displayDanmakuItem.type)) {
                    DANMAKU_TYPE_NORM -> displayDanmakuItem.x + textSize.width < 0
                    DANMAKU_TYPE_REVERSED -> displayDanmakuItem.x > size.width
                    DANMAKU_TYPE_TOP, DANMAKU_TYPE_BOTTOM -> (updateTimer() - displayDanmakuItem.appearTime) > 5000    //5秒
                    else -> false
                }
            }
            newInvisibleDanmakus.forEach { newInvisibleDanmaku ->
                tempList.removeAll { it.danmakuId == newInvisibleDanmaku.danmakuId }
                if (DANMAKU_TYPE_BOTTOM isEqualTo newInvisibleDanmaku.type) {
                    val list =
                        bottomDisplayRows[newInvisibleDanmaku.displayRow]?.toMutableList()
                    list?.removeAll { it.danmakuId == newInvisibleDanmaku.danmakuId }
                    bottomDisplayRows[newInvisibleDanmaku.displayRow] = list ?: emptyList()
                } else {
                    val list = topDisplayRows[newInvisibleDanmaku.displayRow]?.toMutableList()
                    list?.removeAll { it.danmakuId == newInvisibleDanmaku.danmakuId }
                    topDisplayRows[newInvisibleDanmaku.displayRow] = list ?: emptyList()
                }
            }

            displayingDanmakus = tempList
            updateCommandDanmakus()
        }
    }

    private fun updateCommandDanmakus() {
        commandDanmakus = commandDanmakus.map { danmaku ->
            if (danmaku.isDisplaying) { //如果正在显示
                if (updateTimer() - danmaku.appearTime > 5000) { //如果显示时间超过5秒钟
                    danmaku.copy(isDisplaying = false)  //则停止显示
                } else {
                    danmaku //否则继续显示
                }
            } else {  //如果没有在显示
                if (danmaku.appearTime < updateTimer()) {    //如果到了显示的时间点
                    if (updateTimer() - danmaku.appearTime < 5000) { //如果显示时间没有超过5秒（即没有显示过
                        danmaku.copy(isDisplaying = true)   //那么开始显示
                    } else {
                        danmaku     //否则继续不显示（已经显示过）
                    }
                } else {
                    danmaku //否则继续不显示（还没到时间显示）
                }
            }
        }
    }

    private fun getRow(
        danmaku: DanmakuElem,
        textWidth: Int,
        drawScope: DrawScope
    ): Int {
        if (DANMAKU_TYPE_BOTTOM isNotEqualTo danmaku.mode) {    //如果不是底端弹幕
            topDisplayRows.values.forEachIndexed { row, danmakusInRow ->
                when (parseDanmakuType(danmaku.mode)) {
                    DANMAKU_TYPE_NORM -> {
                        val hasHitDanmaku = danmakusInRow.any {
                            it.x + (it.textWidth / 2) > drawScope.size.width
                        } //|| danmakusInRow.any { DANMAKU_TYPE_REVERSED isEqualTo it.type }
                        if (!hasHitDanmaku) return row
                    }

                    DANMAKU_TYPE_REVERSED -> {
                        val hasHitDanmaku = danmakusInRow.any {
                            textWidth >= it.x
                        }   //文字宽度没有超过上一个弹幕的
                        //|| danmakusInRow.any { DANMAKU_TYPE_NORM isEqualTo it.type }  //同一行不能同时有从左到右或从右到左
                        if (!hasHitDanmaku) return row
                    }

                    DANMAKU_TYPE_TOP -> {
                        val hasHitDanmaku =
                            danmakusInRow.any { DANMAKU_TYPE_TOP isEqualTo it.type }   //同一行只要没有弹幕就行
                        if (!hasHitDanmaku) return row
                    }
                }
            }
            return topDisplayRows.keys.size
        } else {
            bottomDisplayRows.values.forEachIndexed { row, danmakusInRow ->
                val hasHitDanmaku = danmakusInRow.any { DANMAKU_TYPE_BOTTOM isEqualTo it.type }
                if (!hasHitDanmaku) return row
            }
            return bottomDisplayRows.keys.size
        }
    }

    fun setDanmakuList(
        newDanmakuList: List<DanmakuSegment> = emptyList(),
        commandDanmakus: List<CommandDm> = emptyList(),
        imageDanmakus: Map<List<String>, ImageBitmap> = mapOf()
    ) {
        if (newDanmakuList.isNotEmpty()) {
            danmakuSegments = newDanmakuList
            dynamicDanmakuSegments = dynamicDanmakuSegments.toMutableList().apply {
                addAll(newDanmakuList - dynamicDanmakuSegments.toSet())
            }.map {
                val sortedList = it.danmakuList.sortedBy { elem -> elem.progress }
                DanmakuSegment(it.segmentIndex, sortedList)
            }
        }
        if (commandDanmakus.isNotEmpty()) {
            this.commandDanmakus = buildList {
                commandDanmakus.forEach { danmaku ->
                    val extraType = when (danmaku.command) {
                        COMMAND_SUBSCRIBE -> SubscribeExtra::class.java
                        COMMAND_VOTE -> VoteExtra::class.java
                        COMMAND_VIDEO -> VideoExtra::class.java
                        COMMAND_RATE -> RateExtra::class.java
                        COMMAND_ADVERTISE -> AdvertiseExtra::class.java
                        else -> null
                    }
                    if (extraType != null) {
                        try {
                            val extra = Gson().fromJson(danmaku.extra, extraType)
                            val commandDanmaku = CommandDanmaku(
                                type = danmaku.command,
                                appearTime = danmaku.progress,
                                extra = extra,
                                displayedMillisecond = 0,
                                isDisplaying = false
                            )
                            add(commandDanmaku)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

        }
        if (imageDanmakus.isNotEmpty()) {
            this.imageDanmakus = imageDanmakus
        }
    }

    private fun getImageForExpression(content: String): ImageBitmap? {
        return imageDanmakus.entries.find {
            it.key.contains(content)
        }?.value
    }

    private fun parseDanmakuType(type: Int): DanmakuType {
        return when (type) {
            1, 2, 3 -> DANMAKU_TYPE_NORM
            4 -> DANMAKU_TYPE_BOTTOM
            5 -> DANMAKU_TYPE_TOP
            6 -> DANMAKU_TYPE_REVERSED
            7 -> DANMAKU_TYPE_ADVANCE
            8 -> DANMAKU_TYPE_SCRIPT
            else -> DANMAKU_TYPE_NORM
        }
    }
}