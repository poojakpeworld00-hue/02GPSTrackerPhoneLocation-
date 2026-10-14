package com.find.phone.gps.location.tracker.screens.dashboard

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.BsheetCountryPickerBinding
import com.find.phone.gps.location.tracker.databinding.CardCountryBinding
import com.find.phone.gps.location.tracker.screens.finder.RegionZones
import com.find.phone.gps.location.tracker.screens.finder.RegionZone
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The country selector sheet (design 3c). Opens expanded to ~81% of the screen (the design's
 * 150px top offset on 780), with the current choice under "Suggested" and every territory
 * under "All countries", filtered by name or dial code.
 */
class NationSelectSheet : BottomSheetDialogFragment() {

    private var _binding: BsheetCountryPickerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NationListAdapter

    override fun getTheme(): Int = R.style.Ds_BottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BsheetCountryPickerBinding.inflate(inflater, container, false)
        val screen = resources.displayMetrics.heightPixels
        binding.root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (screen * SHEET_HEIGHT_FRACTION).toInt())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val currentIso = requireArguments().getString(ARG_ISO).orEmpty()
        val current = RegionZones.byIso(currentIso)
        if (current != null) {
            binding.lblSuggestedFlag.text = RegionZones.flag(current.iso2)
            binding.lblSuggestedName.text = current.name
            binding.lblSuggestedDial.text = "+" + current.dial
            binding.rowSuggestedVw.setOnClickListener { pick(current) }
        } else {
            binding.lblSuggested.visibility = View.GONE
            binding.rowSuggestedVw.visibility = View.GONE
        }

        adapter = NationListAdapter(::pick)
        binding.rollCountries.layoutManager = LinearLayoutManager(requireContext())
        binding.rollCountries.adapter = adapter
        adapter.submit(RegionZones.all.filter { it.iso2 != currentIso })

        binding.searchInputVw.doAfterTextChanged { text ->
            val q = text?.toString()?.trim().orEmpty()
            adapter.submit(
                RegionZones.all.filter { t ->
                    t.iso2 != currentIso &&
                        (q.isEmpty() || t.name.contains(q, ignoreCase = true) || t.dial.contains(q.removePrefix("+")))
                }
            )
        }
        binding.padClose.setOnClickListener { dismiss() }
    }

    private fun pick(territory: RegionZone) {
        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply { putString(ARG_ISO, territory.iso2) })
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** Flag / name / dial rows. */
    private class NationListAdapter(private val onClick: (RegionZone) -> Unit) : RecyclerView.Adapter<NationListAdapter.VH>() {

        private val items = mutableListOf<RegionZone>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<RegionZone>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val binding: CardCountryBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.rowVw.setOnClickListener {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) onClick(items[p])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(CardCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = items[position]
            holder.binding.lblFlag.text = RegionZones.flag(t.iso2)
            holder.binding.lblName.text = t.name
            holder.binding.lblDial.text = "+" + t.dial
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val RESULT_KEY = "country_picked"
        const val ARG_ISO = "iso2"
        private const val SHEET_HEIGHT_FRACTION = 0.81f

        fun newInstance(currentIso: String) = NationSelectSheet().apply {
            arguments = Bundle().apply { putString(ARG_ISO, currentIso) }
        }
    }
}
