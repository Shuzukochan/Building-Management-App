package com.app.buildingmanagement.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.R
import com.google.android.material.color.MaterialColors

class MonthPickerDialog(
    private val context: Context,
    private var selectedMonth: Int = 0,
    private var selectedYear: Int = 2024,
    private val onMonthYearSelected: (month: Int, year: Int) -> Unit
) {

    private lateinit var dialog: AlertDialog
    private val months = listOf(
        "Th1", "Th2", "Th3", "Th4",
        "Th5", "Th6", "Th7", "Th8",
        "Th9", "Th10", "Th11", "Th12"
    )

    private val colorPrimary: Int by lazy {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        typedValue.data
    }

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_month_picker, null)

        val txtYear = dialogView.findViewById<TextView>(R.id.text_year)
        val titleText = dialogView.findViewById<TextView>(R.id.title)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_view)
        val btnPrev = dialogView.findViewById<ImageView>(R.id.btn_previous)
        val btnNext = dialogView.findViewById<ImageView>(R.id.btn_next)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_n)
        val btnOk = dialogView.findViewById<TextView>(R.id.btn_p)

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)
        dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))




        fun updateHeader() {
            txtYear.text = selectedYear.toString()
            titleText.text = "Tháng ${selectedMonth + 1} năm $selectedYear"
        }

        updateHeader()

        val adapter = MonthAdapter(selectedMonth) { month ->
            selectedMonth = month
            updateHeader()
        }

        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.adapter = adapter

        btnPrev.setOnClickListener {
            selectedYear--
            updateHeader()
        }

        btnNext.setOnClickListener {
            selectedYear++
            updateHeader()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnOk.setOnClickListener {
            onMonthYearSelected(selectedMonth, selectedYear)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.60).toInt() // Hoặc 320.dp.toPx()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setDimAmount(0.3f)
    }

    private inner class MonthAdapter(
        private var selected: Int,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

        inner class MonthViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(120, 120)
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(8, 8, 8, 8)
                setBackgroundResource(android.R.color.transparent)
            }
            return MonthViewHolder(tv)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            holder.textView.text = months[position]

            val context = holder.textView.context

            if (position == selected) {
                holder.textView.setBackgroundResource(R.drawable.bg_month_selected)

                val textColor = MaterialColors.getColor(holder.textView, com.google.android.material.R.attr.colorOnSecondary)
                holder.textView.setTextColor(textColor)
            } else {
                holder.textView.setBackgroundResource(android.R.color.transparent)

                val textColor = MaterialColors.getColor(holder.textView, com.google.android.material.R.attr.colorOnSurface)
                holder.textView.setTextColor(textColor)
            }


            holder.textView.setOnClickListener {
                val old = selected
                selected = position
                notifyItemChanged(old)
                notifyItemChanged(selected)
                onClick(position)
            }
        }


        override fun getItemCount(): Int = months.size
    }
}