package uz.jarvis.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var adapter: BrainsAdapter
    private lateinit var aggregatorSpinner: Spinner
    private var aggregatorIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val list = findViewById<RecyclerView>(R.id.brainsList)
        adapter = BrainsAdapter(Prefs.brains(this).toMutableList()) { brain ->
            Prefs.removeBrain(this, brain.id)
            refresh()
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        aggregatorSpinner = findViewById(R.id.aggregatorSpinner)
        findViewById<CheckBox>(R.id.voiceReply).isChecked = Prefs.isVoiceReply(this)
        findViewById<CheckBox>(R.id.webEnabled).isChecked = Prefs.isWebEnabled(this)

        // Gapirish tezligi: SeekBar 0..30 -> 0.5x..2.0x
        val rateBar = findViewById<android.widget.SeekBar>(R.id.speechRate)
        val rateLabel = findViewById<TextView>(R.id.rateLabel)
        fun rateFromProgress(p: Int) = 0.5f + p / 20f   // 0->0.5, 10->1.0, 30->2.0
        rateBar.progress = ((Prefs.getSpeechRate(this) - 0.5f) * 20f).toInt().coerceIn(0, 30)
        rateLabel.text = "Gapirish tezligi: ${"%.1f".format(rateFromProgress(rateBar.progress))}x"
        rateBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                rateLabel.text = "Gapirish tezligi: ${"%.1f".format(rateFromProgress(p))}x"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // Bulut server
        val serverUrl = findViewById<EditText>(R.id.serverUrl)
        val serverToken = findViewById<EditText>(R.id.serverToken)
        serverUrl.setText(Prefs.getServerUrl(this))
        serverToken.setText(Prefs.getServerToken(this))
        findViewById<Button>(R.id.syncButton).setOnClickListener {
            Prefs.setServer(this, serverUrl.text.toString(), serverToken.text.toString())
            if (!Cloud.isConfigured(this)) {
                Toast.makeText(this, "Avval server manzilini kiriting", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sinxronlanmoqda...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val res = Cloud.sync(this@SettingsActivity)
                    Toast.makeText(this@SettingsActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.addBrainButton).setOnClickListener { showAddDialog() }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val pos = aggregatorSpinner.selectedItemPosition
            if (pos in aggregatorIds.indices) Prefs.setAggregator(this, aggregatorIds[pos])
            Prefs.setVoiceReply(this, findViewById<CheckBox>(R.id.voiceReply).isChecked)
            Prefs.setWebEnabled(this, findViewById<CheckBox>(R.id.webEnabled).isChecked)
            val rb = findViewById<android.widget.SeekBar>(R.id.speechRate)
            Prefs.setSpeechRate(this, (0.5f + rb.progress / 20f))
            Prefs.setServer(this, serverUrl.text.toString(), serverToken.text.toString())
            if (!Prefs.isConfigured(this)) {
                Toast.makeText(this, "Kamida bitta miya qo'shing", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        refresh()
    }

    private fun refresh() {
        val brains = Prefs.brains(this)
        adapter.setItems(brains)
        aggregatorIds = brains.map { it.id }
        val names = brains.map { it.displayName() }
        val spAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        aggregatorSpinner.adapter = spAdapter
        val current = Prefs.getAggregator(this)?.id
        val idx = aggregatorIds.indexOf(current)
        if (idx >= 0) aggregatorSpinner.setSelection(idx)
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_brain, null)
        val typeSpinner = view.findViewById<Spinner>(R.id.typeSpinner)
        val labelInput = view.findViewById<EditText>(R.id.labelInput)
        val keyInput = view.findViewById<EditText>(R.id.keyInput)
        val modelInput = view.findViewById<EditText>(R.id.modelInput)

        typeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Providers.labels
        )

        AlertDialog.Builder(this)
            .setTitle("Miya qo'shish")
            .setView(view)
            .setPositiveButton("Qo'shish") { _, _ ->
                val type = Providers.ids[typeSpinner.selectedItemPosition]
                val key = keyInput.text.toString().trim()
                if (key.isBlank()) {
                    Toast.makeText(this, "Kalit kiritilmadi", Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.addBrain(this, type, labelInput.text.toString(), key, modelInput.text.toString())
                    refresh()
                }
            }
            .setNegativeButton("Bekor", null)
            .show()
    }
}
