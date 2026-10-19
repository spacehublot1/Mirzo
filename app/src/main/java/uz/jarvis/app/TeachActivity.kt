package uz.jarvis.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TeachActivity : AppCompatActivity() {

    private lateinit var adapter: KnowledgeAdapter
    private lateinit var countView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teach)

        val input = findViewById<EditText>(R.id.teachInput)
        val source = findViewById<EditText>(R.id.sourceInput)
        countView = findViewById(R.id.kCount)

        val list = findViewById<RecyclerView>(R.id.knowledgeList)
        adapter = KnowledgeAdapter(Knowledge.all(this).toMutableList()) { item ->
            Knowledge.remove(this, item.id)
            refresh()
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.addKnowledge).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "Ma'lumot kiriting", Toast.LENGTH_SHORT).show()
            } else {
                Knowledge.add(this, text, source.text.toString())
                input.setText(""); source.setText("")
                Toast.makeText(this, "O'rganildi", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        refresh()
    }

    private fun refresh() {
        val all = Knowledge.all(this)
        adapter.setItems(all)
        countView.text = "O'rgangan ma'lumotlar: ${all.size}"
    }
}
