package io.homedeck.grid.canvas

import android.content.Context
import android.util.AttributeSet
import io.homedeck.grid.R
import org.fossify.commons.views.MyRecyclerView

class DissolveGridView(context: Context, attrs: AttributeSet) : MyRecyclerView(context, attrs) {

    init {

        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(resources.getDimensionPixelSize(R.dimen.launcher_drawer_fade))
        setWillNotDraw(false)
    }

    override fun setVerticalFadingEdgeEnabled(verticalFadingEdgeEnabled: Boolean) {
        super.setVerticalFadingEdgeEnabled(true)
    }

    override fun setWillNotDraw(willNotDraw: Boolean) {
        super.setWillNotDraw(false)
    }

    override fun getBottomFadingEdgeStrength(): Float {
        val last = getChildAt(childCount - 1) ?: return 0f
        val more = getChildAdapterPosition(last) < (adapter?.itemCount ?: 0) - 1
        return if (more || last.bottom > height) 1f else 0f
    }

    override fun getTopFadingEdgeStrength(): Float {
        val first = getChildAt(0) ?: return 0f
        return if (getChildAdapterPosition(first) > 0 || first.top < 0) 1f else 0f
    }

    override fun isPaddingOffsetRequired() = true

    override fun getTopPaddingOffset() = -paddingTop

    override fun getBottomPaddingOffset() = paddingBottom
}
