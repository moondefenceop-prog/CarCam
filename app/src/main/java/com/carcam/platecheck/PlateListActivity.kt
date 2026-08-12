package com.carcam.platecheck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.carcam.platecheck.databinding.ActivityPlateListBinding
import com.carcam.platecheck.databinding.DialogAddPlateBinding
import com.carcam.platecheck.ui.PlateAdapter
import com.carcam.platecheck.ui.PlateListViewModel
import com.carcam.platecheck.util.KoreanPlateRecognizer
import com.google.android.material.snackbar.Snackbar

class PlateListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlateListBinding
    private val viewModel: PlateListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlateListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = PlateAdapter { plate ->
            AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("${plate.plateNumber} 을(를) 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ -> viewModel.deletePlate(plate) }
                .setNegativeButton("취소", null)
                .show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.allPlates.observe(this) { plates ->
            adapter.submitList(plates)
            binding.tvEmpty.isVisible = plates.isEmpty()
        }

        binding.fabAdd.setOnClickListener { showAddDialog() }

        binding.toolbar.inflateMenu(R.menu.menu_plate_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import -> {
                    // Let the picker show everything: providers label .xlsx and .csv with a
                    // range of MIME types, and a filter tight enough to be correct hides real
                    // files.
                    importLauncher.launch(arrayOf("*/*"))
                    true
                }
                R.id.action_visits -> {
                    startActivity(Intent(this, VisitListActivity::class.java))
                    true
                }
                else -> false
            }
        }

        viewModel.importStatus.observe(this) { message ->
            if (message == null) return@observe
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            viewModel.clearImportStatus()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

    private fun showAddDialog() {
        val dialogBinding = DialogAddPlateBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setTitle("차량 추가")
            .setView(dialogBinding.root)
            .setPositiveButton("추가") { _, _ ->
                val plate = dialogBinding.etPlate.text?.toString()?.trim() ?: ""
                val note = dialogBinding.etNote.text?.toString()?.trim() ?: ""
                when {
                    plate.isEmpty() ->
                        Snackbar.make(binding.root, "번호판을 입력하세요", Snackbar.LENGTH_SHORT).show()
                    !KoreanPlateRecognizer.isValidPlate(plate) ->
                        Snackbar.make(binding.root, "올바른 번호판 형식이 아닙니다", Snackbar.LENGTH_SHORT).show()
                    else -> viewModel.addPlate(plate, note)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
