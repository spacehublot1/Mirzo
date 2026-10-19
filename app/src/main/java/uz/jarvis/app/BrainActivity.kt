package uz.jarvis.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Jarvis miyasining "o'sishi" — nechta qoida/bilim o'rgangani ko'rinadi. */
class BrainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brain)

        findViewById<TextView>(R.id.statBrains).text = Prefs.brains(this).size.toString()
        findViewById<TextView>(R.id.statTurns).text = Memory.turnCount(this).toString()
        findViewById<TextView>(R.id.statLessons).text = Memory.lessonCount(this).toString()
        findViewById<TextView>(R.id.statKnowledge).text = Knowledge.count(this).toString()

        val lessons = Memory.lessonsList(this)
        findViewById<TextView>(R.id.lessonsText).text =
            if (lessons.isEmpty()) "Hali yo'q — savol bergan sari Jarvis qoida o'rganib boradi."
            else lessons.take(30).joinToString("\n\n") { "• $it" }

        val know = Knowledge.all(this)
        findViewById<TextView>(R.id.knowledgeText).text =
            if (know.isEmpty()) "Hali yo'q — \"O'qitish\" bo'limidan ma'lumot bering."
            else know.take(30).joinToString("\n\n") { "• ${it.text.take(200)}" }
    }
}
