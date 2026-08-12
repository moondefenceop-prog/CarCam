package com.carcam.platecheck

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.carcam.platecheck.data.VisitEntity
import com.carcam.platecheck.databinding.ActivityVisitListBinding
import com.carcam.platecheck.ui.VisitAdapter
import com.carcam.platecheck.ui.VisitListViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.File

class VisitListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisitListBinding
    private val viewModel: VisitListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisitListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_visit_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_export) { exportCsv(); true } else false
        }

        val adapter = VisitAdapter { visit -> showVisitActions(visit) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.visits.observe(this) { list ->
            adapter.submitList(list)
            binding.tvEmpty.isVisible = list.isEmpty()
            binding.tvSummary.text = viewModel.summaryOf(list)
        }
        viewModel.dayLabel.observe(this) { binding.tvDate.text = it }

        binding.btnPrevDay.setOnClickListener { viewModel.shiftDay(-1) }
        binding.btnNextDay.setOnClickListener {
            // Records cannot exist in the future; paging past today is only ever a mis-tap.
            if (viewModel.isToday()) {
                Snackbar.make(binding.root, "오늘 이후는 볼 수 없습니다", Snackbar.LENGTH_SHORT).show()
            } else viewModel.shiftDay(1)
        }
    }

    private fun showVisitActions(visit: VisitEntity) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (visit.isOpen) {
            actions += getString(R.string.close_manually) to { viewModel.closeManually(visit) }
        }
        actions += getString(R.string.delete) to {
            AlertDialog.Builder(this)
                .setTitle("기록 삭제")
                .setMessage("${visit.canonicalPlate} 기록을 삭제하시겠습니까?")
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(visit) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            Unit
        }
        AlertDialog.Builder(this)
            .setTitle(visit.canonicalPlate)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /**
     * Write the CSV to app-external files and hand it off with a share sheet rather than
     * writing to Downloads: no storage permission is involved, and sharing is what the
     * records are for — they end up in mail or a messenger on the way to whoever bills.
     */
    private fun exportCsv() {
        viewModel.exportCsv().observe(this) { text ->
            if (text == null) return@observe
            runCatching {
                val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(dir, "입출차기록.csv")
                file.writeText(text, Charsets.UTF_8)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", file
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, getString(R.string.export_csv)))
            }.onFailure {
                Snackbar.make(binding.root, "내보내기 실패: ${it.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
