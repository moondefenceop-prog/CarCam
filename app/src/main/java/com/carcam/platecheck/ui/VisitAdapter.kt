package com.carcam.platecheck.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.carcam.platecheck.R
import com.carcam.platecheck.data.VisitEntity
import com.carcam.platecheck.databinding.ItemVisitBinding
import com.carcam.platecheck.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VisitAdapter(
    private val onClick: (VisitEntity) -> Unit
) : ListAdapter<VisitEntity, VisitAdapter.VH>(DIFF) {

    private val time = SimpleDateFormat("HH:mm", Locale.KOREA)

    class VH(val binding: ItemVisitBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVisitBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.tvPlate.text = v.canonicalPlate
        b.statusBar.setBackgroundColor(
            ContextCompat.getColor(
                ctx,
                if (v.isRegistered) R.color.registered_green else R.color.not_registered_red
            )
        )

        val entry = v.entryAt?.let { time.format(Date(it)) } ?: ctx.getString(R.string.entry_unknown)
        val exit = v.exitAt?.let { time.format(Date(it)) }
        b.tvTimes.text = if (exit != null) "$entry → $exit" else "$entry →"

        // Three states worth telling apart at a glance: finished, still parked, and a stay
        // that has been open long enough to be an exit nobody scanned.
        val duration = v.durationMs
        when {
            duration != null -> {
                b.tvDuration.text = formatDuration(duration)
                b.tvDuration.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            }
            v.isOpen -> {
                val openFor = v.entryAt?.let { System.currentTimeMillis() - it }
                val stale = openFor != null && openFor > 24 * 60 * 60 * 1000L
                b.tvDuration.text = if (stale) "미확인" else ctx.getString(R.string.still_parked)
                b.tvDuration.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (stale) R.color.not_registered_red else R.color.registered_green
                    )
                )
            }
            else -> {
                b.tvDuration.text = "—"
                b.tvDuration.setTextColor(ContextCompat.getColor(ctx, R.color.primary_dark))
            }
        }

        b.root.setOnClickListener { onClick(v) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VisitEntity>() {
            override fun areItemsTheSame(a: VisitEntity, b: VisitEntity) = a.id == b.id
            override fun areContentsTheSame(a: VisitEntity, b: VisitEntity) = a == b
        }
    }
}
