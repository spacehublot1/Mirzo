package uz.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var messages: RecyclerView
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private lateinit var adapter: ChatAdapter

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false

    private val thinkHandler = Handler(Looper.getMainLooper())
    private var thinkRunnable: Runnable? = null

    private val sttLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                input.setText(text)
                send()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        messages = findViewById(R.id.messages)
        input = findViewById(R.id.input)
        statusText = findViewById(R.id.statusText)

        adapter = ChatAdapter(mutableListOf())
        messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messages.adapter = adapter

        findViewById<ImageButton>(R.id.sendButton).setOnClickListener { send() }
        findViewById<ImageButton>(R.id.micButton).setOnClickListener { startVoice() }

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) applyTtsLanguage()
        }

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            Toast.makeText(this, "Avval API kalitlarini kiriting", Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val brains = Prefs.brains(this)
        statusText.text = if (brains.isEmpty()) {
            "Miya yo'q — Sozlamalardan qo'shing"
        } else {
            val names = brains.joinToString(", ") { it.displayName() }
            val lessons = Memory.lessonCount(this)
            val know = Knowledge.count(this)
            "Miyalar (${brains.size}): $names  •  qoidalar: $lessons  •  bilim: $know"
        }
    }

    private fun send() {
        val q = input.text.toString().trim()
        if (q.isBlank()) return
        if (busy) { Toast.makeText(this, "Miyalar hali o'ylayapti...", Toast.LENGTH_SHORT).show(); return }
        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SettingsActivity::class.java)); return
        }
        hideKeyboard()
        input.setText("")
        adapter.add(ChatMessage(q, fromUser = true))
        val thinkingIdx = adapter.add(ChatMessage("Jarvis o'ylayapti", fromUser = false, sender = "Jarvis"))
        scrollDown()

        busy = true
        statusText.text = "Jarvis o'ylayapti..."
        // "o'ylayapti" nuqtalari animatsiyasi
        var dots = 0
        val r = object : Runnable {
            override fun run() {
                dots = (dots + 1) % 4
                adapter.update(thinkingIdx, "Jarvis o'ylayapti" + ".".repeat(dots), "Jarvis")
                thinkHandler.postDelayed(this, 450)
            }
        }
        thinkRunnable = r
        thinkHandler.postDelayed(r, 450)

        lifecycleScope.launch {
            // Xotira (suhbat + o'rganilgan qoidalar) + savolga mos bilim bazasi
            val memory = buildString {
                val mem = Memory.buildContext(this@MainActivity)
                if (mem.isNotBlank()) append(mem).append("\n\n")
                val know = Knowledge.retrieve(this@MainActivity, q)
                if (know.isNotBlank()) append(know)
            }.trim()
            val council = AiClient.council(this@MainActivity, q, memory)

            // animatsiyani to'xtatamiz
            thinkRunnable?.let { thinkHandler.removeCallbacks(it) }
            thinkRunnable = null
            // Hammasi bitta "Jarvis" — miyalarni alohida ko'rsatmaymiz
            adapter.update(thinkingIdx, council.finalAnswer, "Jarvis")
            scrollDown()

            // Xotira: suhbat + o'rganilgan saboq
            Memory.addTurn(this@MainActivity, q, council.finalAnswer)
            if (council.lesson.isNotBlank()) Memory.addLesson(this@MainActivity, council.lesson)

            if (Prefs.isVoiceReply(this@MainActivity) && ttsReady) speak(council.finalAnswer)

            busy = false
            refreshStatus()
        }
    }

    // ---------------- Ovoz kiritish (STT) ----------------
    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Gapiring...")
        }
        try {
            sttLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ovozli kiritish mavjud emas", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    // ---------------- Javobni o'qish (TTS) ----------------
    /** Eng mos ovoz tilini tanlaydi: o'zbek bo'lmasa rus, u ham bo'lmasa ingliz. */
    private fun applyTtsLanguage() {
        val engine = tts ?: return
        val candidates = listOf(Locale("uz"), Locale("ru"), Locale.getDefault(), Locale.ENGLISH)
        for (loc in candidates) {
            val res = engine.setLanguage(loc)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) break
        }
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        engine.setSpeechRate(Prefs.getSpeechRate(this))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    // ---------------- yordamchi ----------------
    private fun scrollDown() {
        messages.post { messages.scrollToPosition(adapter.itemCount - 1) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_brain -> { startActivity(Intent(this, BrainActivity::class.java)); return true }
            R.id.action_teach -> { startActivity(Intent(this, TeachActivity::class.java)); return true }
            R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); return true }
            R.id.action_clear -> {
                Memory.clear(this)
                Toast.makeText(this, "Xotira tozalandi", Toast.LENGTH_SHORT).show()
                refreshStatus()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        thinkHandler.removeCallbacksAndMessages(null)
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}
