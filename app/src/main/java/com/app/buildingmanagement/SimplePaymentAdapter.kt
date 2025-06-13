package com.app.buildingmanagement.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.R
import com.app.buildingmanagement.model.SimplePayment

class SimplePaymentAdapter(private val paymentList: List<SimplePayment>) :
    RecyclerView.Adapter<SimplePaymentAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val ivStatus: ImageView = itemView.findViewById(R.id.ivStatus) // Thay đổi từ TextView sang ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_payment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val payment = paymentList[position]

        holder.tvDate.text = payment.getFormattedDate()
        holder.tvAmount.text = payment.getFormattedAmount()

        // Set icon vector theo status
        when (payment.status.uppercase()) {
            "PAID" -> {
                holder.ivStatus.setImageResource(R.drawable.ic_check_circle)
            }
            "PENDING" -> {
                holder.ivStatus.setImageResource(R.drawable.ic_pending_circle)
            }
            else -> {
                holder.ivStatus.setImageResource(R.drawable.ic_error_circle)
            }
        }
    }

    override fun getItemCount() = paymentList.size
}
