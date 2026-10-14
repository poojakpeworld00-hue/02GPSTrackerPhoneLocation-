package io.homedeck.grid.panes

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import io.homedeck.grid.screens.HomeDeskPane

abstract class HomeDeskFragment<BINDING : ViewBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {
    protected var activity: HomeDeskPane? = null
    protected lateinit var binding: BINDING

    abstract fun setupFragment(activity: HomeDeskPane)
}
