package uz.jarvis.app

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(val text: String, val fromUser: Boolean, val sender: String = "")

class ChatAdapter(private val items: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val sender: TextView = root.findViewById(R.id.sender)
        val bubble: TextView = root.findViewById(R.id.bubble)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false) as LinearLayout
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val ctx = holder.root.context
        holder.bubble.text = m.text

        val bubbleParams = holder.bubble.layoutParams as LinearLayout.LayoutParams
        if (m.fromUser) {
            holder.root.gravity = Gravity.END
            bubbleParams.gravity = Gravity.END
            holder.bubble.setBackgroundResource(R.drawable.bubble_user_bg)
            holder.sender.visibility = View.GONE
        } else {
            holder.root.gravity = Gravity.START
            bubbleParams.gravity = Gravity.START
            holder.bubble.setBackgroundResource(R.drawable.bubble_ai_bg)
            holder.sender.visibility = if (m.sender.isBlank()) View.GONE else View.VISIBLE
            holder.sender.text = m.sender
        }
        holder.bubble.layoutParams = bubbleParams
    }

    fun add(m: ChatMessage): Int {
        items.add(m)
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }

    fun update(index: Int, text: String, sender: String? = null) {
        if (index !in items.indices) return
        val old = items[index]
        items[index] = old.copy(text = text, sender = sender ?: old.sender)
        notifyItemChanged(index)
    }
}
