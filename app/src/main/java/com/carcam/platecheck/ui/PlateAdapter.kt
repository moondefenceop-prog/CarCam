package com.carcam.platecheck.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.carcam.platecheck.data.PlateEntity
import com.carcam.platecheck.databinding.ItemPlateBinding

class PlateAdapter(
    private val onDelete: (PlateEntity) -> Unit
) : ListAdapter<PlateEntity, PlateAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemPlateBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PlateEntity) {
            binding.tvPlateNumber.text = item.plateNumber
            if (item.note.isNotEmpty()) {
                binding.tvNote.text = item.note
                binding.tvNote.isVisible = true
            } else {
                binding.tvNote.isVisible = false
            }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPlateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PlateEntity>() {
            override fun areItemsTheSame(a: PlateEntity, b: PlateEntity) = a.id == b.id
            override fun areContentsTheSame(a: PlateEntity, b: PlateEntity) = a == b
        }
    }
}
