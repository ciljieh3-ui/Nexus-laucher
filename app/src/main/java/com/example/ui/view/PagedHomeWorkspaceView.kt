package com.example.ui.view

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.core.ThemePalette
import com.example.data.IconCache
import com.example.model.HomeItem
import com.example.model.LauncherSettings
import com.example.widget.NexusAppWidgetHost
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zero-dependency multi-page Home Workspace ViewGroup.
 * Handles horizontal paging (OverScroller + VelocityTracker), vertical swipe gestures,
 * double-tap lock, long-press edit mode, Canvas edit-grid rendering,
 * drag-and-drop cell move / folder merging, and native AppWidgetHostView placement.
 */
class PagedHomeWorkspaceView(context: Context) : ViewGroup(context) {

    var onPageChanged: ((Int) -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null
    var onDoubleTapEmpty: (() -> Unit)? = null
    var onLongPressEmpty: (() -> Unit)? = null
    var onLaunchApp: ((String, String) -> Unit)? = null
    var onLaunchShortcut: ((String, String) -> Unit)? = null
    var onOpenFolder: ((String) -> Unit)? = null
    var onLongClickHomeApp: ((HomeItem.AppItem) -> Unit)? = null
    var onRemoveHomeItem: ((String) -> Unit)? = null
    var onResizeWidget: ((String, Int, Int) -> Unit)? = null
    var onDropHomeItem: ((draggedId: String, targetPage: Int, targetRow: Int, targetCol: Int) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVel = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val scroller = android.widget.OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    var currentPage: Int = 0
        private set
    private var pageCount: Int = 1
    private var cols: Int = 4
    private var rows: Int = 5
    private var isEditMode: Boolean = false
    private var lockLayout: Boolean = false
    private var animDurationMs: Long = 180L

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragMode = DragMode.NONE

    private var draggingChild: View? = null
    private var draggingItem: HomeItem? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var hoverRow = -1
    private var hoverCol = -1

    private val itemViews = HashMap<String, View>()
    private val itemDataByView = HashMap<View, HomeItem>()

    private val gridSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x10FFFFFF
        style = Paint.Style.FILL
    }
    private val gridStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x26FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val hoverFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4438BDF8
        style = Paint.Style.FILL
    }
    private val hoverStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val slotRectF = RectF()
    private val cornerRadius = 16f * density

    private val emptyGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (findItemChildAt(e.x, e.y) == null) {
                    onDoubleTapEmpty?.invoke()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (findItemChildAt(e.x, e.y) == null) {
                    onLongPressEmpty?.invoke()
                }
            }
        }
    )

    private enum class DragMode { NONE, HORIZONTAL_PAGE, VERTICAL_GESTURE, ITEM_DRAG }

    init {
        tag = "home_workspace"
        setWillNotDraw(false)
        clipChildren = true
    }

    fun render(
        pageCount: Int,
        homeItems: List<HomeItem>,
        settings: LauncherSettings,
        iconCache: IconCache,
        widgetHost: NexusAppWidgetHost,
        badgeCounts: Map<String, Int>,
        isEditMode: Boolean,
        accentColor: Int,
        animDurationMs: Long
    ) {
        this.pageCount = pageCount.coerceAtLeast(1)
        this.cols = settings.homeColumns.coerceIn(3, 6)
        this.rows = settings.homeRows.coerceIn(4, 6)
        this.isEditMode = isEditMode
        this.lockLayout = settings.lockHomeLayout
        this.animDurationMs = animDurationMs
        this.hoverStrokePaint.color = accentColor

        if (currentPage >= this.pageCount) {
            scrollToPage((this.pageCount - 1).coerceAtLeast(0), animate = false)
        }

        val activeIds = homeItems.map { it.id }.toSet()
        val toRemove = itemViews.keys.filter { it !in activeIds }
        for (id in toRemove) {
            val view = itemViews.remove(id)
            if (view != null) {
                itemDataByView.remove(view)
                removeView(view)
            }
        }

        for (item in homeItems) {
            when (item) {
                is HomeItem.AppItem -> {
                    val iconView = (itemViews[item.id] as? NexusIconView) ?: NexusIconView(context).also {
                        itemViews[item.id] = it
                        addView(it)
                    }
                    itemDataByView[iconView] = item
                    val badges = if (settings.notificationDotsEnabled) badgeCounts[item.packageName] ?: 0 else 0
                    iconView.bindApp(
                        packageName = item.packageName,
                        activityName = item.activityName,
                        label = item.label,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        badgeCount = badges,
                        accentColor = accentColor,
                        showRemoveCross = isEditMode
                    )
                    iconView.setOnClickListener {
                        if (isEditMode) {
                            onRemoveHomeItem?.invoke(item.id)
                        } else {
                            onLaunchApp?.invoke(item.packageName, item.activityName)
                        }
                    }
                    iconView.setOnLongClickListener {
                        if (!isEditMode) onLongClickHomeApp?.invoke(item)
                        true
                    }
                }
                is HomeItem.ShortcutItem -> {
                    val iconView = (itemViews[item.id] as? NexusIconView) ?: NexusIconView(context).also {
                        itemViews[item.id] = it
                        addView(it)
                    }
                    itemDataByView[iconView] = item
                    iconView.bindApp(
                        packageName = item.packageName,
                        activityName = "",
                        label = item.label,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        badgeCount = 0,
                        isShortcut = true,
                        accentColor = accentColor,
                        showRemoveCross = isEditMode
                    )
                    iconView.setOnClickListener {
                        if (isEditMode) onRemoveHomeItem?.invoke(item.id)
                        else onLaunchShortcut?.invoke(item.packageName, item.shortcutId)
                    }
                    iconView.setOnLongClickListener {
                        onRemoveHomeItem?.invoke(item.id)
                        true
                    }
                }
                is HomeItem.FolderItem -> {
                    val iconView = (itemViews[item.id] as? NexusIconView) ?: NexusIconView(context).also {
                        itemViews[item.id] = it
                        addView(it)
                    }
                    itemDataByView[iconView] = item
                    val totalBadges = if (settings.notificationDotsEnabled) {
                        item.apps.sumOf { badgeCounts[it.packageName] ?: 0 }
                    } else 0
                    iconView.bindFolder(
                        folderId = item.id,
                        title = item.title,
                        apps = item.apps,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        totalBadges = totalBadges,
                        accentColor = accentColor,
                        showRemoveCross = isEditMode
                    )
                    iconView.setOnClickListener {
                        if (isEditMode) onRemoveHomeItem?.invoke(item.id)
                        else onOpenFolder?.invoke(item.id)
                    }
                    iconView.setOnLongClickListener {
                        onOpenFolder?.invoke(item.id)
                        true
                    }
                }
                is HomeItem.WidgetItem -> {
                    val wrapper = (itemViews[item.id] as? FrameLayout) ?: createWidgetWrapper(
                        item,
                        widgetHost
                    ).also {
                        itemViews[item.id] = it
                        addView(it)
                    }
                    itemDataByView[wrapper] = item
                    updateWidgetOverlay(wrapper, item, isEditMode, accentColor)
                }
            }
        }

        requestLayout()
        invalidate()
    }

    private fun createWidgetWrapper(
        item: HomeItem.WidgetItem,
        widgetHost: NexusAppWidgetHost
    ): FrameLayout {
        val frame = FrameLayout(context)
        val hostView: AppWidgetHostView? = widgetHost.createHostView(item.appWidgetId)
        if (hostView != null) {
            (hostView.parent as? ViewGroup)?.removeView(hostView)
            frame.addView(
                hostView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            frame.tag = hostView to widgetHost
        } else {
            val fallback = TextView(context).apply {
                text = "Widget tidak tersedia (${item.label})"
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            frame.addView(fallback)
        }

        // Edit Mode controls bar
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = ThemePalette.dp(context, 4f)
            setPadding(pad, pad, pad, pad)
            background = ThemePalette.roundedCard(0xE0111A2C.toInt(), ThemePalette.dpF(context, 12f))
            addView(makeMiniBtn("−W") { onResizeWidget?.invoke(item.id, -1, 0) })
            addView(makeMiniBtn("+W") { onResizeWidget?.invoke(item.id, 1, 0) })
            addView(makeMiniBtn("+H") { onResizeWidget?.invoke(item.id, 0, 1) })
            addView(makeMiniBtn("✕", 0xFFEF4444.toInt()) { onRemoveHomeItem?.invoke(item.id) })
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            val m = ThemePalette.dp(context, 6f)
            setMargins(m, m, m, m)
        }
        controls.id = WIDGET_CONTROLS_ID
        frame.addView(controls, lp)
        return frame
    }

    private fun makeMiniBtn(label: String, textColor: Int = Color.WHITE, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 11f
            setPadding(
                ThemePalette.dp(context, 8f),
                ThemePalette.dp(context, 5f),
                ThemePalette.dp(context, 8f),
                ThemePalette.dp(context, 5f)
            )
            setOnClickListener { onClick() }
        }
    }

    private fun updateWidgetOverlay(
        frame: FrameLayout,
        item: HomeItem.WidgetItem,
        editMode: Boolean,
        accentColor: Int
    ) {
        val controls = frame.findViewById<View>(WIDGET_CONTROLS_ID)
        controls?.visibility = if (editMode) View.VISIBLE else View.GONE
        frame.background = if (editMode) {
            ThemePalette.roundedCard(
                fillColor = 0x22000000,
                radiusPx = ThemePalette.dpF(context, 16f),
                strokeColor = accentColor,
                strokeWidthPx = ThemePalette.dp(context, 1.5f)
            )
        } else {
            null
        }
    }

    fun scrollToPage(page: Int, animate: Boolean = true) {
        val clamped = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (clamped != currentPage) {
            currentPage = clamped
            onPageChanged?.invoke(currentPage)
        }
        val targetX = clamped * width
        if (animate && animDurationMs > 0L && width > 0) {
            scroller.startScroll(scrollX, 0, targetX - scrollX, 0, animDurationMs.toInt())
            postInvalidateOnAnimation()
        } else {
            scroller.abortAnimation()
            scrollTo(targetX, 0)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        val cellW = (w - paddingLeft - paddingRight) / cols.coerceAtLeast(1)
        val cellH = (h - paddingTop - paddingBottom) / rows.coerceAtLeast(1)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val item = itemDataByView[child]
            val cw = if (item is HomeItem.WidgetItem) cellW * item.colSpan else cellW
            val ch = if (item is HomeItem.WidgetItem) cellH * item.rowSpan else cellH
            child.measure(
                MeasureSpec.makeMeasureSpec(cw.coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch.coerceAtLeast(1), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (w <= 0 || h <= 0) return

        val innerW = w - paddingLeft - paddingRight
        val innerH = h - paddingTop - paddingBottom
        val cellW = innerW.toFloat() / cols
        val cellH = innerH.toFloat() / rows

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val item = itemDataByView[child] ?: continue
            if (child === draggingChild) continue

            val pageOffset = item.page * w
            val left = (pageOffset + paddingLeft + item.col * cellW).roundToInt()
            val top = (paddingTop + item.row * cellH).roundToInt()
            val right = (left + child.measuredWidth)
            val bottom = (top + child.measuredHeight)
            child.layout(left, top, right, bottom)

            if (item is HomeItem.WidgetItem) {
                @Suppress("UNCHECKED_CAST")
                val pair = child.tag as? Pair<AppWidgetHostView, NexusAppWidgetHost>
                if (pair != null) {
                    val wDp = ((right - left) / density).toInt().coerceAtLeast(60)
                    val hDp = ((bottom - top) / density).toInt().coerceAtLeast(60)
                    pair.second.updateWidgetSize(pair.first, wDp, hDp)
                }
            }
        }

        val expectedScrollX = currentPage * w
        if (scroller.isFinished && scrollX != expectedScrollX && dragMode != DragMode.HORIZONTAL_PAGE) {
            scrollTo(expectedScrollX, 0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isEditMode || width <= 0 || height <= 0) return

        val innerW = width - paddingLeft - paddingRight
        val innerH = height - paddingTop - paddingBottom
        val cellW = innerW.toFloat() / cols
        val cellH = innerH.toFloat() / rows
        val pad = 4f * density

        val pageStartX = currentPage * width + paddingLeft
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = pageStartX + c * cellW + pad
                val top = paddingTop + r * cellH + pad
                val right = pageStartX + (c + 1) * cellW - pad
                val bottom = paddingTop + (r + 1) * cellH - pad
                slotRectF.set(left, top, right, bottom)
                val hovered = r == hoverRow && c == hoverCol
                canvas.drawRoundRect(
                    slotRectF,
                    cornerRadius,
                    cornerRadius,
                    if (hovered) hoverFillPaint else gridSlotPaint
                )
                canvas.drawRoundRect(
                    slotRectF,
                    cornerRadius,
                    cornerRadius,
                    if (hovered) hoverStrokePaint else gridStrokePaint
                )
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                dragMode = DragMode.NONE
                draggingChild = null
                draggingItem = null
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (isEditMode && !lockLayout && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    val hit = findItemChildAt(downX, downY)
                    if (hit != null) {
                        val item = itemDataByView[hit]
                        if (item != null) {
                            draggingChild = hit
                            draggingItem = item
                            dragMode = DragMode.ITEM_DRAG
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            hit.bringToFront()
                            return true
                        }
                    }
                }
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragMode = DragMode.HORIZONTAL_PAGE
                    return true
                }
                if (!isEditMode && abs(dy) > touchSlop * 1.4f && abs(dy) > abs(dx)) {
                    dragMode = DragMode.VERTICAL_GESTURE
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        emptyGestureDetector.onTouchEvent(event)
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                if (dragMode == DragMode.NONE) {
                    val totalDx = event.x - downX
                    val totalDy = event.y - downY
                    if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) {
                        dragMode = DragMode.HORIZONTAL_PAGE
                    } else if (!isEditMode && abs(totalDy) > touchSlop * 1.4f && abs(totalDy) > abs(totalDx)) {
                        dragMode = DragMode.VERTICAL_GESTURE
                    }
                }

                when (dragMode) {
                    DragMode.HORIZONTAL_PAGE -> {
                        val maxScroll = (pageCount - 1).coerceAtLeast(0) * width
                        val nextScroll = (scrollX - dx).toInt().coerceIn(0, maxScroll)
                        scrollTo(nextScroll, 0)
                    }
                    DragMode.ITEM_DRAG -> {
                        val child = draggingChild
                        val item = draggingItem
                        if (child != null && item != null) {
                            dragOffsetX += dx
                            dragOffsetY += dy
                            child.translationX = dragOffsetX
                            child.translationY = dragOffsetY

                            val cellW = (width - paddingLeft - paddingRight).toFloat() / cols
                            val cellH = (height - paddingTop - paddingBottom).toFloat() / rows
                            val centerX = child.left + dragOffsetX + child.width / 2f - currentPage * width - paddingLeft
                            val centerY = child.top + dragOffsetY + child.height / 2f - paddingTop
                            hoverCol = (centerX / cellW).toInt().coerceIn(0, cols - 1)
                            hoverRow = (centerY / cellH).toInt().coerceIn(0, rows - 1)
                            invalidate()
                        }
                    }
                    DragMode.VERTICAL_GESTURE, DragMode.NONE -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (dragMode) {
                    DragMode.HORIZONTAL_PAGE -> {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val targetPage = when {
                            vx < -minFlingVel && currentPage < pageCount - 1 -> currentPage + 1
                            vx > minFlingVel && currentPage > 0 -> currentPage - 1
                            width > 0 -> ((scrollX + width / 2) / width).coerceIn(0, pageCount - 1)
                            else -> currentPage
                        }
                        scrollToPage(targetPage, animate = true)
                    }
                    DragMode.VERTICAL_GESTURE -> {
                        val totalDy = event.y - downY
                        if (totalDy < -55f * density) {
                            onSwipeUp?.invoke()
                        } else if (totalDy > 55f * density) {
                            onSwipeDown?.invoke()
                        }
                    }
                    DragMode.ITEM_DRAG -> {
                        val child = draggingChild
                        val item = draggingItem
                        child?.translationX = 0f
                        child?.translationY = 0f
                        val targetRow = hoverRow
                        val targetCol = hoverCol
                        hoverRow = -1
                        hoverCol = -1
                        draggingChild = null
                        draggingItem = null
                        if (item != null && targetRow >= 0 && targetCol >= 0) {
                            onDropHomeItem?.invoke(item.id, currentPage, targetRow, targetCol)
                        }
                        requestLayout()
                        invalidate()
                    }
                    DragMode.NONE -> Unit
                }
                dragMode = DragMode.NONE
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    private fun findItemChildAt(x: Float, y: Float): View? {
        val worldX = x + scrollX
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (worldX >= child.left && worldX <= child.right && y >= child.top && y <= child.bottom) {
                return child
            }
        }
        return null
    }

    companion object {
        private const val WIDGET_CONTROLS_ID = 0x7F010099
    }
}
