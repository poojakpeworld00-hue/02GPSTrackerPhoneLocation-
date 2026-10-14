package com.find.phone.gps.location.tracker.screens.walkthrough

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.find.phone.gps.location.tracker.R

data class TourSlide(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int,
    @param:LayoutRes val sceneRes: Int,

    val showsAd: Boolean = false,
)

object TourSlides {
    val all: List<TourSlide> = listOf(
        TourSlide(R.string.ds_intro_title_1, R.string.ds_intro_body_1, R.layout.stage_intro_find),
        TourSlide(R.string.ds_intro_title_2, R.string.ds_intro_body_2, R.layout.stage_intro_map),
        TourSlide(R.string.ds_intro_title_3, R.string.ds_intro_body_3, R.layout.stage_intro_search, showsAd = true),
        TourSlide(R.string.ds_intro_title_4, R.string.ds_intro_body_4, R.layout.stage_intro_connected),
    )
}
