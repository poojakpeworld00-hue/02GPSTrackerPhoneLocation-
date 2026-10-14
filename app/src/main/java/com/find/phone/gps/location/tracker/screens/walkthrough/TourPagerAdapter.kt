package com.find.phone.gps.location.tracker.screens.walkthrough

import android.animation.Animator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.find.phone.gps.location.tracker.databinding.CardOnboardingBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

class TourPagerAdapter(
    private val pages: List<TourSlide>,
) : RecyclerView.Adapter<TourPagerAdapter.VH>() {

    inner class VH(val binding: CardOnboardingBinding) : RecyclerView.ViewHolder(binding.root) {

        private val anims = mutableListOf<Animator>()

        fun bind(page: TourSlide) {
            VisualTweens.cancelAll(anims)
            val container = binding.artContainerVw
            container.removeAllViews()
            LayoutInflater.from(container.context).inflate(page.sceneRes, container, true)

            anims += TourTransitions.attach(container)
            binding.lblTitle.setText(page.titleRes)
            binding.lblDesc.setText(page.descRes)
        }

        fun recycle() = VisualTweens.cancelAll(anims)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(CardOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(pages[position])

    override fun onViewRecycled(holder: VH) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = pages.size
}
