package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.max

@RequiresOptIn(message = "FlexBox API is experimental and subject to change.")
annotation class ExperimentalFlexBoxApi

@RequiresOptIn(message = "Grid API is experimental and subject to change.")
annotation class ExperimentalGridApi

// ============================================================================
// FLEXBOX ENUMS & DATA CLASSES
// ============================================================================

enum class FlexDirection { Row, RowReverse, Column, ColumnReverse }
enum class FlexWrap { NoWrap, Wrap, WrapReverse }
enum class FlexJustifyContent { Start, Center, End, SpaceBetween, SpaceAround, SpaceEvenly }
enum class FlexAlignItems { Start, End, Center, Stretch, Baseline }
enum class FlexAlignContent { Start, End, Center, Stretch, SpaceBetween, SpaceAround }
enum class FlexAlignSelf { Auto, Start, End, Center, Stretch, Baseline }

sealed class FlexBasis {
    object Auto : FlexBasis()
    data class FixedDp(val value: Dp) : FlexBasis()
    data class Percentage(val value: Float) : FlexBasis()
}

class FlexItemConfig {
    var basis: FlexBasis = FlexBasis.Auto
    var grow: Float = 0f
    var shrink: Float = 1f
    var alignSelf: FlexAlignSelf = FlexAlignSelf.Auto
    var order: Int = 0

    fun basis(b: FlexBasis) { basis = b }
    fun basis(dp: Dp) { basis = FlexBasis.FixedDp(dp) }
    fun basis(pct: Float) { basis = FlexBasis.Percentage(pct) }
    fun grow(g: Float) { grow = g }
    fun shrink(s: Float) { shrink = s }
    fun alignSelf(a: FlexAlignSelf) { alignSelf = a }
    fun order(o: Int) { order = o }
}

internal data class FlexParentData(
    val basis: FlexBasis = FlexBasis.Auto,
    val grow: Float = 0f,
    val shrink: Float = 1f,
    val alignSelf: FlexAlignSelf = FlexAlignSelf.Auto,
    val order: Int = 0
)

class FlexBoxConfig {
    var direction: FlexDirection = FlexDirection.Row
    var wrap: FlexWrap = FlexWrap.NoWrap
    var justifyContent: FlexJustifyContent = FlexJustifyContent.Start
    var alignItems: FlexAlignItems = FlexAlignItems.Start
    var alignContent: FlexAlignContent = FlexAlignContent.Start
    var rowGap: Dp = 0.dp
    var columnGap: Dp = 0.dp

    fun direction(d: FlexDirection) { direction = d }
    fun wrap(w: FlexWrap) { wrap = w }
    fun justifyContent(j: FlexJustifyContent) { justifyContent = j }
    fun alignItems(a: FlexAlignItems) { alignItems = a }
    fun alignContent(a: FlexAlignContent) { alignContent = a }
    fun gap(d: Dp) {
        rowGap = d
        columnGap = d
    }
    fun rowGap(d: Dp) { rowGap = d }
    fun columnGap(d: Dp) { columnGap = d }
}

fun Modifier.flex(block: FlexItemConfig.() -> Unit): Modifier {
    val cfg = FlexItemConfig().apply(block)
    return this.then(
        FlexParentDataElement(
            FlexParentData(
                basis = cfg.basis,
                grow = cfg.grow,
                shrink = cfg.shrink,
                alignSelf = cfg.alignSelf,
                order = cfg.order
            )
        )
    )
}

private data class FlexParentDataElement(val data: FlexParentData) : ModifierNodeElement<FlexParentDataNode>() {
    override fun create(): FlexParentDataNode = FlexParentDataNode(data)
    override fun update(node: FlexParentDataNode) { node.data = data }
    override fun InspectorInfo.inspectableProperties() {
        name = "flex"
        properties["basis"] = data.basis
        properties["grow"] = data.grow
        properties["shrink"] = data.shrink
        properties["alignSelf"] = data.alignSelf
        properties["order"] = data.order
    }
}

private class FlexParentDataNode(var data: FlexParentData) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

// ============================================================================
// FLEXBOX COMPOSABLE
// ============================================================================

@ExperimentalFlexBoxApi
@Composable
fun FlexBox(
    modifier: Modifier = Modifier,
    config: FlexBoxConfig.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val flexConfig = remember(config) { FlexBoxConfig().apply(config) }

    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val isHorizontal = flexConfig.direction == FlexDirection.Row || flexConfig.direction == FlexDirection.RowReverse
        val isReverseMain = flexConfig.direction == FlexDirection.RowReverse || flexConfig.direction == FlexDirection.ColumnReverse
        val isReverseCross = flexConfig.wrap == FlexWrap.WrapReverse

        val mainGapPx = (if (isHorizontal) flexConfig.columnGap else flexConfig.rowGap).roundToPx()
        val crossGapPx = (if (isHorizontal) flexConfig.rowGap else flexConfig.columnGap).roundToPx()

        val maxMainPx = if (isHorizontal) constraints.maxWidth else constraints.maxHeight
        val maxCrossPx = if (isHorizontal) constraints.maxHeight else constraints.maxWidth

        // 1. Gather item data & sort by order
        val indexedMeasurables = measurables.mapIndexed { idx, m ->
            val pData = m.parentData as? FlexParentData ?: FlexParentData()
            Triple(idx, m, pData)
        }.sortedWith(compareBy({ it.third.order }, { it.first }))

        // 2. Measure intrinsic/base sizes
        class MeasurableItem(
            val originalIndex: Int,
            val measurable: Measurable,
            val pData: FlexParentData
        ) {
            var baseSizePx: Int = 0
            var placeable: Placeable? = null
            var mainSizePx: Int = 0
            var crossSizePx: Int = 0
        }

        val items = indexedMeasurables.map { triple ->
            val item = MeasurableItem(triple.first, triple.second, triple.third)
            val basis = item.pData.basis
            when (basis) {
                is FlexBasis.FixedDp -> {
                    item.baseSizePx = basis.value.roundToPx()
                }
                is FlexBasis.Percentage -> {
                    val available = if (maxMainPx != Constraints.Infinity) maxMainPx else 0
                    item.baseSizePx = (available * basis.value).toInt()
                }
                FlexBasis.Auto -> {
                    item.baseSizePx = if (isHorizontal) {
                        item.measurable.maxIntrinsicWidth(if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity)
                    } else {
                        item.measurable.maxIntrinsicHeight(if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity)
                    }
                }
            }
            item
        }

        // 3. Build lines
        class FlexLine {
            val lineItems = mutableListOf<MeasurableItem>()
            var mainSizeSum: Int = 0
            var crossSizeMax: Int = 0
        }

        val lines = mutableListOf<FlexLine>()
        var currentLine = FlexLine()

        items.forEach { item ->
            val required = if (currentLine.lineItems.isEmpty()) item.baseSizePx else item.baseSizePx + mainGapPx
            val fits = maxMainPx == Constraints.Infinity || flexConfig.wrap == FlexWrap.NoWrap || (currentLine.mainSizeSum + required <= maxMainPx)

            if (fits || currentLine.lineItems.isEmpty()) {
                currentLine.lineItems.add(item)
                currentLine.mainSizeSum += required
            } else {
                lines.add(currentLine)
                currentLine = FlexLine()
                currentLine.lineItems.add(item)
                currentLine.mainSizeSum = item.baseSizePx
            }
        }
        if (currentLine.lineItems.isNotEmpty()) {
            lines.add(currentLine)
        }

        // 4. Measure items in main & cross axes per line
        lines.forEach { line ->
            val totalBase = line.lineItems.fold(0) { acc, it -> acc + it.baseSizePx }
            val gapsTotal = max(0, line.lineItems.size - 1) * mainGapPx
            val freeSpace = if (maxMainPx != Constraints.Infinity) maxMainPx - totalBase - gapsTotal else 0

            var totalGrow = 0f
            var totalShrink = 0f
            line.lineItems.forEach {
                totalGrow += it.pData.grow
                totalShrink += it.pData.shrink
            }

            line.lineItems.forEach { item ->
                var targetMainPx = item.baseSizePx
                if (freeSpace > 0 && totalGrow > 0f && item.pData.grow > 0f) {
                    targetMainPx += (freeSpace * (item.pData.grow / totalGrow)).toInt()
                } else if (freeSpace < 0 && totalShrink > 0f && item.pData.shrink > 0f) {
                    val deficit = -freeSpace
                    targetMainPx = max(0, item.baseSizePx - (deficit * (item.pData.shrink / totalShrink)).toInt())
                }

                val childConstraints = if (isHorizontal) {
                    Constraints(
                        minWidth = targetMainPx,
                        maxWidth = targetMainPx,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight
                    )
                } else {
                    Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                        minHeight = targetMainPx,
                        maxHeight = targetMainPx
                    )
                }
                val placeable = item.placeable ?: item.measurable.measure(childConstraints)
                item.placeable = placeable
                item.mainSizePx = if (isHorizontal) placeable.width else placeable.height
                item.crossSizePx = if (isHorizontal) placeable.height else placeable.width
                line.crossSizeMax = max(line.crossSizeMax, item.crossSizePx)
            }

            line.mainSizeSum = line.lineItems.sumOf { it.mainSizePx } + gapsTotal
        }

        val totalLinesCrossPx = lines.sumOf { it.crossSizeMax } + max(0, lines.size - 1) * crossGapPx
        val layoutWidth = if (isHorizontal) {
            if (constraints.hasBoundedWidth) constraints.maxWidth else max(lines.maxOfOrNull { it.mainSizeSum } ?: 0, constraints.minWidth)
        } else {
            if (constraints.hasBoundedWidth) constraints.maxWidth else max(totalLinesCrossPx, constraints.minWidth)
        }
        val layoutHeight = if (isHorizontal) {
            if (constraints.hasBoundedHeight) constraints.maxHeight else max(totalLinesCrossPx, constraints.minHeight)
        } else {
            if (constraints.hasBoundedHeight) constraints.maxHeight else max(lines.maxOfOrNull { it.mainSizeSum } ?: 0, constraints.minHeight)
        }

        val containerMainPx = if (isHorizontal) layoutWidth else layoutHeight
        val containerCrossPx = if (isHorizontal) layoutHeight else layoutWidth

        layout(layoutWidth, layoutHeight) {
            // Line positioning along cross axis
            val remainingCrossPx = containerCrossPx - totalLinesCrossPx
            var crossOffset = when (flexConfig.alignContent) {
                FlexAlignContent.End -> remainingCrossPx
                FlexAlignContent.Center -> remainingCrossPx / 2
                else -> 0
            }

            val lineCrossGapExtra = if (lines.size > 1 && remainingCrossPx > 0) {
                when (flexConfig.alignContent) {
                    FlexAlignContent.SpaceBetween -> remainingCrossPx / (lines.size - 1)
                    FlexAlignContent.SpaceAround -> remainingCrossPx / lines.size
                    else -> 0
                }
            } else 0

            if (flexConfig.alignContent == FlexAlignContent.SpaceAround) {
                crossOffset += lineCrossGapExtra / 2
            }

            val effectiveLines = if (isReverseCross) lines.reversed() else lines

            effectiveLines.forEach { line ->
                val remainingMainPx = containerMainPx - line.mainSizeSum
                val itemCount = line.lineItems.size

                var mainOffset = when (flexConfig.justifyContent) {
                    FlexJustifyContent.End -> remainingMainPx
                    FlexJustifyContent.Center -> remainingMainPx / 2
                    else -> 0
                }

                val itemGapExtra = if (itemCount > 1 && remainingMainPx > 0) {
                    when (flexConfig.justifyContent) {
                        FlexJustifyContent.SpaceBetween -> remainingMainPx / (itemCount - 1)
                        FlexJustifyContent.SpaceAround -> remainingMainPx / itemCount
                        FlexJustifyContent.SpaceEvenly -> remainingMainPx / (itemCount + 1)
                        else -> 0
                    }
                } else 0

                if (flexConfig.justifyContent == FlexJustifyContent.SpaceAround) {
                    mainOffset += itemGapExtra / 2
                } else if (flexConfig.justifyContent == FlexJustifyContent.SpaceEvenly) {
                    mainOffset += itemGapExtra
                }

                val effectiveItems = if (isReverseMain) line.lineItems.reversed() else line.lineItems

                effectiveItems.forEachIndexed { _, item ->
                    val placeable = item.placeable ?: return@forEachIndexed
                    val effectiveAlign = if (item.pData.alignSelf != FlexAlignSelf.Auto) {
                        item.pData.alignSelf
                    } else {
                        when (flexConfig.alignItems) {
                            FlexAlignItems.Start -> FlexAlignSelf.Start
                            FlexAlignItems.End -> FlexAlignSelf.End
                            FlexAlignItems.Center -> FlexAlignSelf.Center
                            FlexAlignItems.Stretch -> FlexAlignSelf.Stretch
                            FlexAlignItems.Baseline -> FlexAlignSelf.Baseline
                        }
                    }

                    val crossItemOffset = when (effectiveAlign) {
                        FlexAlignSelf.End -> line.crossSizeMax - item.crossSizePx
                        FlexAlignSelf.Center -> (line.crossSizeMax - item.crossSizePx) / 2
                        else -> 0
                    }

                    val x = if (isHorizontal) mainOffset else crossOffset + crossItemOffset
                    val y = if (isHorizontal) crossOffset + crossItemOffset else mainOffset

                    placeable.placeRelative(x, y)

                    mainOffset += item.mainSizePx + mainGapPx + (
                        if (flexConfig.justifyContent == FlexJustifyContent.SpaceBetween ||
                            flexConfig.justifyContent == FlexJustifyContent.SpaceAround ||
                            flexConfig.justifyContent == FlexJustifyContent.SpaceEvenly) itemGapExtra else 0
                    )
                }

                crossOffset += line.crossSizeMax + crossGapPx + (
                    if (flexConfig.alignContent == FlexAlignContent.SpaceBetween ||
                        flexConfig.alignContent == FlexAlignContent.SpaceAround) lineCrossGapExtra else 0
                )
            }
        }
    }
}

// ============================================================================
// GRID ENUMS, TYPES & COMPOSABLE
// ============================================================================

enum class GridFlow { Row, Column }

@JvmInline
value class Fr(val value: Float)
val Int.fr: Fr get() = Fr(this.toFloat())
val Float.fr: Fr get() = Fr(this)

sealed class GridTrackSize {
    object Auto : GridTrackSize()
    object MinContent : GridTrackSize()
    object MaxContent : GridTrackSize()
    data class Fixed(val dp: Dp) : GridTrackSize()
    data class Percent(val percentage: Float) : GridTrackSize()
    data class Flex(val fr: Fr) : GridTrackSize()
    data class MinMax(val minDp: Dp, val flexFr: Fr) : GridTrackSize()
}

class GridConfigurationScope {
    val columns = mutableListOf<GridTrackSize>()
    val rows = mutableListOf<GridTrackSize>()
    var flow: GridFlow = GridFlow.Row
    var rowGap: Dp = 0.dp
    var columnGap: Dp = 0.dp

    fun column(size: Dp) { columns.add(GridTrackSize.Fixed(size)) }
    fun column(percentage: Float) { columns.add(GridTrackSize.Percent(percentage)) }
    fun column(fr: Fr) { columns.add(GridTrackSize.Flex(fr)) }
    fun column(trackSize: GridTrackSize) { columns.add(trackSize) }

    fun row(size: Dp) { rows.add(GridTrackSize.Fixed(size)) }
    fun row(percentage: Float) { rows.add(GridTrackSize.Percent(percentage)) }
    fun row(fr: Fr) { rows.add(GridTrackSize.Flex(fr)) }
    fun row(trackSize: GridTrackSize) { rows.add(trackSize) }

    fun gap(dp: Dp) {
        rowGap = dp
        columnGap = dp
    }
    fun rowGap(dp: Dp) { rowGap = dp }
    fun columnGap(dp: Dp) { columnGap = dp }

    fun repeat(count: Int, block: GridConfigurationScope.() -> Unit) {
        val tempScope = GridConfigurationScope()
        tempScope.block()
        kotlin.repeat(count) {
            columns.addAll(tempScope.columns)
            rows.addAll(tempScope.rows)
        }
    }
}

@ExperimentalGridApi
@Composable
fun Grid(
    modifier: Modifier = Modifier,
    config: GridConfigurationScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val gridScope = remember(config) { GridConfigurationScope().apply(config) }

    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val colGapPx = gridScope.columnGap.roundToPx()
        val rowGapPx = gridScope.rowGap.roundToPx()

        // Establish grid dimensions
        val definedColsCount = if (gridScope.columns.isNotEmpty()) gridScope.columns.size else 2
        val itemCount = measurables.size
        val numCols = max(1, definedColsCount)
        val numRows = if (gridScope.rows.isNotEmpty()) gridScope.rows.size else ((itemCount + numCols - 1) / numCols)

        val cols = if (gridScope.columns.isNotEmpty()) gridScope.columns else List(numCols) { GridTrackSize.Flex(1.fr) }
        val rows = if (gridScope.rows.isNotEmpty()) gridScope.rows else List(numRows) { GridTrackSize.Flex(1.fr) }

        val totalColGaps = max(0, numCols - 1) * colGapPx
        val totalRowGaps = max(0, numRows - 1) * rowGapPx

        val availWidth = if (constraints.hasBoundedWidth) max(0, constraints.maxWidth - totalColGaps) else 0
        val availHeight = if (constraints.hasBoundedHeight) max(0, constraints.maxHeight - totalRowGaps) else 0

        // Calculate Column Widths
        val colWidths = IntArray(numCols) { 0 }
        var sumColFr = 0f
        var usedColWidth = 0

        cols.forEachIndexed { i, track ->
            when (track) {
                is GridTrackSize.Fixed -> {
                    colWidths[i] = track.dp.roundToPx()
                    usedColWidth += colWidths[i]
                }
                is GridTrackSize.Percent -> {
                    colWidths[i] = (availWidth * track.percentage).toInt()
                    usedColWidth += colWidths[i]
                }
                is GridTrackSize.Flex -> {
                    sumColFr += track.fr.value
                }
                is GridTrackSize.MinMax -> {
                    sumColFr += track.flexFr.value
                }
                GridTrackSize.Auto, GridTrackSize.MinContent, GridTrackSize.MaxContent -> {
                    val defaultW = (availWidth / numCols).coerceAtLeast(80.dp.roundToPx())
                    colWidths[i] = defaultW
                    usedColWidth += colWidths[i]
                }
            }
        }

        val remainingColWidth = max(0, availWidth - usedColWidth)
        if (sumColFr > 0f) {
            cols.forEachIndexed { i, track ->
                when (track) {
                    is GridTrackSize.Flex -> {
                        colWidths[i] = (remainingColWidth * (track.fr.value / sumColFr)).toInt()
                    }
                    is GridTrackSize.MinMax -> {
                        val minW = track.minDp.roundToPx()
                        val flexW = (remainingColWidth * (track.flexFr.value / sumColFr)).toInt()
                        colWidths[i] = max(minW, flexW)
                    }
                    else -> {}
                }
            }
        }

        // Calculate Row Heights
        val rowHeights = IntArray(numRows) { 0 }
        var sumRowFr = 0f
        var usedRowHeight = 0

        rows.forEachIndexed { j, track ->
            when (track) {
                is GridTrackSize.Fixed -> {
                    rowHeights[j] = track.dp.roundToPx()
                    usedRowHeight += rowHeights[j]
                }
                is GridTrackSize.Percent -> {
                    rowHeights[j] = (availHeight * track.percentage).toInt()
                    usedRowHeight += rowHeights[j]
                }
                is GridTrackSize.Flex -> {
                    sumRowFr += track.fr.value
                }
                is GridTrackSize.MinMax -> {
                    sumRowFr += track.flexFr.value
                }
                GridTrackSize.Auto, GridTrackSize.MinContent, GridTrackSize.MaxContent -> {
                    val defaultH = 80.dp.roundToPx()
                    rowHeights[j] = defaultH
                    usedRowHeight += rowHeights[j]
                }
            }
        }

        val remainingRowHeight = max(0, availHeight - usedRowHeight)
        if (sumRowFr > 0f) {
            rows.forEachIndexed { j, track ->
                when (track) {
                    is GridTrackSize.Flex -> {
                        rowHeights[j] = (remainingRowHeight * (track.fr.value / sumRowFr)).toInt()
                    }
                    is GridTrackSize.MinMax -> {
                        val minH = track.minDp.roundToPx()
                        val flexH = (remainingRowHeight * (track.flexFr.value / sumRowFr)).toInt()
                        rowHeights[j] = max(minH, flexH)
                    }
                    else -> {}
                }
            }
        }

        // Measure children
        val placeables = measurables.mapIndexed { idx, measurable ->
            val colIdx = if (gridScope.flow == GridFlow.Row) idx % numCols else idx / numRows
            val rowIdx = if (gridScope.flow == GridFlow.Row) idx / numCols else idx % numRows

            val w = if (colIdx in colWidths.indices) colWidths[colIdx] else 100.dp.roundToPx()
            val h = if (rowIdx in rowHeights.indices) rowHeights[rowIdx] else 100.dp.roundToPx()

            measurable.measure(
                Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0))
            )
        }

        val totalW = colWidths.sum() + totalColGaps
        val totalH = rowHeights.sum() + totalRowGaps

        val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else totalW
        val layoutHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else totalH

        layout(layoutWidth, layoutHeight) {
            var currentY = 0
            for (r in 0 until numRows) {
                var currentX = 0
                for (c in 0 until numCols) {
                    val index = if (gridScope.flow == GridFlow.Row) r * numCols + c else c * numRows + r
                    if (index in placeables.indices) {
                        placeables[index].placeRelative(currentX, currentY)
                    }
                    if (c in colWidths.indices) {
                        currentX += colWidths[c] + colGapPx
                    }
                }
                if (r in rowHeights.indices) {
                    currentY += rowHeights[r] + rowGapPx
                }
            }
        }
    }
}
