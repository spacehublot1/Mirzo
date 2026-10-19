package uz.jarvis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BrainsAdapter(
    private val items: MutableList<Prefs.Brain>,
    private val onDelete: (Prefs.Brain) -> Unit
) : RecyclerView.Adapter<BrainsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.brainName)
        val sub: TextView = v.findViewById(R.id.brainSub)
        val delete: ImageButton = v.findViewById(R.id.deleteBrain)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_brain, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        holder.name.text = b.displayName()
        holder.sub.text = "${Providers.byId(b.type).label} • ${b.modelOrDefault()} • ${maskKey(b.key)}"
        holder.delete.setOnClickListener { onDelete(b) }
    }

    fun setItems(list: List<Prefs.Brain>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    private fun maskKey(key: String): String =
        if (key.length <= 8) "••••" else key.take(4) + "…" + key.takeLast(4)
}
