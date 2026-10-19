package uz.jarvis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KnowledgeAdapter(
    private val items: MutableList<Knowledge.Item>,
    private val onDelete: (Knowledge.Item) -> Unit
) : RecyclerView.Adapter<KnowledgeAdapter.VH>() {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.kText)
        val source: TextView = v.findViewById(R.id.kSource)
        val delete: ImageButton = v.findViewById(R.id.kDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_knowledge, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.text.text = it.text
        holder.source.text = "${it.source} • ${fmt.format(Date(it.ts))}"
        holder.delete.setOnClickListener { onDelete(it) }
    }

    fun setItems(list: List<Knowledge.Item>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
}
