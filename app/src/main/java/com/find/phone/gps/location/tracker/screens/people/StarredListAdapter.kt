package com.find.phone.gps.location.tracker.screens.people

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.find.phone.gps.location.tracker.databinding.CardContactFavBinding

/** The Favorites row (design 8a): 56dp gradient avatars with a locate badge and the first name. */
class StarredListAdapter(
    private val onLocate: (PersonPhone) -> Unit,
) : ListAdapter<PersonPhone, StarredListAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(CardContactFavBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: CardContactFavBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: PersonPhone) {
            binding.lblInitials.text = contact.initials
            binding.lblInitials.setBackgroundResource(PeopleListAdapter.AVATARS[contact.avatar])
            binding.lblFirstName.text = contact.firstName
            binding.root.setOnClickListener { onLocate(contact) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PersonPhone>() {
        override fun areItemsTheSame(oldItem: PersonPhone, newItem: PersonPhone): Boolean =
            oldItem.contactId == newItem.contactId && oldItem.number == newItem.number

        override fun areContentsTheSame(oldItem: PersonPhone, newItem: PersonPhone): Boolean = oldItem == newItem
    }
}
