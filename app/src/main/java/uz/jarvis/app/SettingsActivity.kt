package uz.jarvis.app

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val openai = findViewById<EditText>(R.id.keyOpenai)
        val gemini = findViewById<EditText>(R.id.keyGemini)
        val deepseek = findViewById<EditText>(R.id.keyDeepseek)
        val group = findViewById<RadioGroup>(R.id.aggregatorGroup)
        val voice = findViewById<CheckBox>(R.id.voiceReply)

        // Mavjud qiymatlarni ko'rsatamiz
        openai.setText(Prefs.getKey(this, Prefs.OPENAI))
        gemini.setText(Prefs.getKey(this, Prefs.GEMINI))
        deepseek.setText(Prefs.getKey(this, Prefs.DEEPSEEK))
        voice.isChecked = Prefs.isVoiceReply(this)
        when (Prefs.getAggregator(this)) {
            Prefs.OPENAI -> group.check(R.id.aggOpenai)
            Prefs.GEMINI -> group.check(R.id.aggGemini)
            Prefs.DEEPSEEK -> group.check(R.id.aggDeepseek)
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            Prefs.setKey(this, Prefs.OPENAI, openai.text.toString())
            Prefs.setKey(this, Prefs.GEMINI, gemini.text.toString())
            Prefs.setKey(this, Prefs.DEEPSEEK, deepseek.text.toString())
            Prefs.setVoiceReply(this, voice.isChecked)
            val agg = when (group.checkedRadioButtonId) {
                R.id.aggGemini -> Prefs.GEMINI
                R.id.aggDeepseek -> Prefs.DEEPSEEK
                else -> Prefs.OPENAI
            }
            Prefs.setAggregator(this, agg)

            if (!Prefs.isConfigured(this)) {
                Toast.makeText(this, "Kamida bitta AI kalitini kiriting", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
