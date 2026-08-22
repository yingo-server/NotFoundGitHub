package io.nggit.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

class DragDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startLeftWeight = 0f
    private var startRightWeight = 0f
    private val minWeight = 0.2f
    private val maxWeight = 0.8f

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

    private fun getWeight(view: View): Float {
        val lp = view.layoutParams
        return if (lp is LinearLayout.LayoutParams) lp.weight else 1f
    }

    private fun setWeight(view: View, weight: Float) {
        val lp = view.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            lp.weight = weight
            view.layoutParams = lp
        }
    }

    companion object {
        var leftPaneId = 0
        var rightPaneId = 0
    }
}
