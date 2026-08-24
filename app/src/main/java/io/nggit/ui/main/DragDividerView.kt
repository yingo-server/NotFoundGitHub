// 可拖拽分隔条视图，用于双栏布局的实时调整，支持触摸拖拽改变左右面板的宽度比例，限制最小最大比例范围
package io.nggit.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * 可拖拽分隔条自定义视图，嵌入在左右双栏布局之间，用户通过触摸拖拽实时调整左右面板的宽度占比。
 * 内部维护拖拽起始坐标和初始权重值，移动过程中按比例计算新权重并应用到左右面板的布局参数。
 */
class DragDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 触摸按下时手指的横坐标（绝对像素值），用于计算移动偏移量 */
    private var startX = 0f

    /** 拖拽开始时左侧面板的布局权重，作为计算新权重的基准值 */
    private var startLeftWeight = 0f

    /** 拖拽开始时右侧面板的布局权重，保留以备后续计算使用 */
    private var startRightWeight = 0f

    /** 左侧面板允许的最小权重值，防止拖拽后左侧区域过小 */
    private val minWeight = 0.2f

    /** 左侧面板允许的最大权重值，防止拖拽后右侧区域过小 */
    private val maxWeight = 0.8f

    /**
     * 处理触摸事件，根据手指移动距离实时计算并更新左右面板的布局权重比例。
     * 按下时记录起始状态，移动时按偏移量计算新权重，抬起或取消时结束拖拽。
     *
     * @param event 触摸事件对象
     * @return 是否消费了该触摸事件
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parent = parent as? ViewGroup ?: return false
        val leftPane = parent.findViewById<View>(leftPaneId)
        val rightPane = parent.findViewById<View>(rightPaneId)
        if (leftPane == null || rightPane == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startLeftWeight = getWeight(leftPane)
                startRightWeight = getWeight(rightPane)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val totalWidth = parent.width.toFloat()
                val weightDelta = dx / totalWidth
                val newLeft = (startLeftWeight + weightDelta).coerceIn(minWeight, maxWeight)
                val newRight = 1f - newLeft
                setWeight(leftPane, newLeft)
                setWeight(rightPane, newRight)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * 获取指定视图的 LinearLayout 布局权重值。
     *
     * @param view 需要获取权重的目标视图
     * @return 视图的布局权重，若非 LinearLayout 布局则返回默认值 1f
     */
    private fun getWeight(view: View): Float {
        val lp = view.layoutParams
        return if (lp is LinearLayout.LayoutParams) lp.weight else 1f
    }

    /**
     * 设置指定视图的 LinearLayout 布局权重值，并触发布局更新。
     *
     * @param view 需要设置权重的目标视图
     * @param weight 新的权重值
     */
    private fun setWeight(view: View, weight: Float) {
        val lp = view.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            lp.weight = weight
            view.layoutParams = lp
        }
    }

    /**
     * 伴生对象，存放左右面板视图的资源ID，需在使用前由外部设置对应的面板ID值。
     */
    companion object {
        /** 左侧面板的视图资源ID */
        var leftPaneId = 0

        /** 右侧面板的视图资源ID */
        var rightPaneId = 0
    }
}
