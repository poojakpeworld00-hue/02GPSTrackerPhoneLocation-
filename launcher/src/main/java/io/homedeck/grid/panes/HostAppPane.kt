package io.homedeck.grid.panes

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import io.homedeck.grid.R
import io.homedeck.grid.screens.HomeDeskPane
import io.homedeck.grid.bridge.HomeDeskContent
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.databinding.HdkHostPanelBinding
import kotlin.math.abs

class HostAppPane(
    context: Context,
    attributeSet: AttributeSet,
) : HomeDeskFragment<HdkHostPanelBinding>(context, attributeSet) {

    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityX < 0 && abs(velocityX) > abs(velocityY)) {
                activity?.concealHostPanel()
                return true
            }
            return false
        }
    })

    val panelContent: Fragment?
        get() = activity?.supportFragmentManager?.findFragmentById(R.id.host_panel_containerView)

    private val panelCallbacks: HomeDeskContent?
        get() = panelContent as? HomeDeskContent

    override fun setupFragment(activity: HomeDeskPane) {
        this.activity = activity
        this.binding = HdkHostPanelBinding.bind(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    fun refresh() {
        val activity = activity ?: return
        val fm = activity.supportFragmentManager

        if (fm.isStateSaved) return
        if (fm.findFragmentById(R.id.host_panel_containerView) == null) {

            val fragment = HomeServiceLocator.bridge.panelFragment() ?: return

            fm.beginTransaction()
                .replace(R.id.host_panel_containerView, fragment)
                .commitNow()
        }
    }

    fun warmUp() = refresh()

    fun setPanelVisible(visible: Boolean) {
        panelCallbacks?.setPanelVisible(visible)
    }

    fun handleBack(): Boolean = panelCallbacks?.handleBack() == true
}
