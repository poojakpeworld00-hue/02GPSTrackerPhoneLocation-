package com.find.phone.gps.location.tracker.grants.panel

import android.animation.Animator
import android.view.View
import android.widget.TextView
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.dp
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.loop
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.pvh

object PanelArtwork {

    fun bind(page: PanelSlide, root: View) {
        when (page) {
            PanelSlide.CONTACTS -> {

                listOf(R.id.sub1Vw to "2104", R.id.sub2Vw to "2114", R.id.sub3Vw to "2124").forEach { (id, digits) ->
                    root.findViewById<TextView>(id).text = root.context.getString(R.string.ps_contacts_mobile, digits)
                }
            }
            PanelSlide.SIM -> {
                val without = root.findViewById<View>(R.id.lblWithout)
                val with = root.findViewById<View>(R.id.lblWith)

                root.findViewById<UnveilSplitLayout>(R.id.revealVw).onFractionChanged = { f ->
                    without.alpha = 1f - 0.65f * f
                    with.alpha = 0.35f + 0.65f * f
                }
            }
            else -> Unit
        }
    }

    fun start(page: PanelSlide, root: View): List<Animator> {
        if (page == PanelSlide.SIM) {
            root.findViewById<UnveilSplitLayout>(R.id.revealVw).start()
            return emptyList()
        }
        if (VisualTweens.animationsDisabled(root.context)) return emptyList()
        return when (page) {
            PanelSlide.NOTIFICATIONS -> notifications(root)
            PanelSlide.CONTACTS -> contacts(root)
            PanelSlide.LOCATION -> location(root)
            PanelSlide.SIM -> emptyList()
        }
    }

    fun stop(page: PanelSlide, root: View, running: MutableList<Animator>) {
        if (page == PanelSlide.SIM) root.findViewById<UnveilSplitLayout>(R.id.revealVw).stop()
        running.forEach { it.cancel() }
        running.clear()
        resetToRest(root)
    }

    private fun resetToRest(root: View) {
        listOf(R.id.ringVw, R.id.bellVw, R.id.badgeVw, R.id.panelA, R.id.panelB, R.id.row1Vw, R.id.row2Vw, R.id.row3Vw,
            R.id.rowCheckVw, R.id.areaPingVw, R.id.pinVw).forEach { id ->
            root.findViewById<View>(id)?.apply {
                alpha = 1f; scaleX = 1f; scaleY = 1f; rotation = 0f; translationX = 0f; translationY = 0f
            }
        }
    }

    private fun ping(view: View, durationMs: Long): Animator = loop(
        view, durationMs, 0L,

        pvh(View.SCALE_X, VisualTweens.EASE_OUT, 0f to 0.4f, 1f to 1.5f),
        pvh(View.SCALE_Y, VisualTweens.EASE_OUT, 0f to 0.4f, 1f to 1.5f),
        pvh(View.ALPHA, VisualTweens.EASE_OUT, 0f to 0.9f, 1f to 0f),
    )

    private fun pop(view: View, durationMs: Long): Animator = loop(
        view, durationMs, 0L,
        pvh(View.SCALE_X, VisualTweens.EASE_OUT, 0f to 0f, 0.1f to 0f, 0.2f to 1.2f, 0.26f to 1f, 1f to 1f),
        pvh(View.SCALE_Y, VisualTweens.EASE_OUT, 0f to 0f, 0.1f to 0f, 0.2f to 1.2f, 0.26f to 1f, 1f to 1f),
    )

    private fun notifications(root: View): List<Animator> {
        val ctx = root.context
        val bell = root.findViewById<View>(R.id.bellVw)

        bell.post { bell.pivotX = bell.width / 2f; bell.pivotY = bell.height * 0.1f }
        val e = VisualTweens.EASE_IN_OUT
        val up12 = ctx.dp(12f)
        val down6 = ctx.dp(6f)
        return listOf(
            ping(root.findViewById(R.id.ringVw), 2400L),
            loop(
                bell, 2400L, 0L,
                pvh(View.ROTATION, e, 0f to 0f, 0.1f to 14f, 0.2f to -12f, 0.3f to 8f, 0.4f to -4f, 0.5f to 0f, 1f to 0f),
            ),
            pop(root.findViewById(R.id.badgeVw), 2400L),
            loop(
                root.findViewById(R.id.panelA), 4800L, 0L,
                pvh(View.ALPHA, e, 0f to 0f, 0.08f to 0f, 0.18f to 1f, 0.8f to 1f, 0.92f to 0f, 1f to 0f),
                pvh(View.TRANSLATION_Y, e, 0f to up12, 0.08f to up12, 0.18f to 0f, 0.8f to 0f, 0.92f to -down6, 1f to -down6),
            ),
            loop(
                root.findViewById(R.id.panelB), 4800L, 0L,
                pvh(View.ALPHA, e, 0f to 0f, 0.3f to 0f, 0.4f to 1f, 0.84f to 1f, 0.94f to 0f, 1f to 0f),
                pvh(View.TRANSLATION_Y, e, 0f to -up12, 0.3f to -up12, 0.4f to 0f, 1f to 0f),
            ),
        )
    }

    private fun contacts(root: View): List<Animator> {
        val shift = root.context.dp(10f)
        val rows = listOf(R.id.row1Vw, R.id.row2Vw, R.id.row3Vw).mapIndexed { i, id ->
            val row = root.findViewById<View>(id)

            row.alpha = 0f
            loop(
                row, 3600L, i * 200L,
                pvh(View.ALPHA, VisualTweens.EASE_OUT, 0f to 0f, 0.04f to 0f, 0.16f to 1f, 1f to 1f),
                pvh(View.TRANSLATION_X, VisualTweens.EASE_OUT, 0f to -shift, 0.04f to -shift, 0.16f to 0f, 1f to 0f),
            )
        }
        return rows + pop(root.findViewById(R.id.rowCheckVw), 3600L)
    }

    private fun location(root: View): List<Animator> {
        val ctx = root.context
        return listOf(
            ping(root.findViewById(R.id.areaPingVw), 2600L),

            loop(
                root.findViewById(R.id.pinVw), 2600L, 0L,
                pvh(
                    View.TRANSLATION_Y, VisualTweens.EASE_OUT,
                    0f to -ctx.dp(50f), 0.25f to ctx.dp(4f), 0.33f to -ctx.dp(3f), 0.4f to 0f, 1f to 0f,
                ),
                pvh(View.ALPHA, VisualTweens.EASE_OUT, 0f to 0f, 0.25f to 1f, 1f to 1f),
            ),
        )
    }
}
