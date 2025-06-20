package com.app.buildingmanagement

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import java.util.Locale

class MonthPickerDialog(
    private val context: Context,
    private var selectedMonth: Int,
    private var selectedYear: Int,
    private val onMonthYearSelected: (month: Int, year: Int) -> Unit
) {

    private lateinit var dialog: AlertDialog
    private val months = listOf(
        context.getString(R.string.month_jan),
        context.getString(R.string.month_feb),
        context.getString(R.string.month_mar),
        context.getString(R.string.month_apr),
        context.getString(R.string.month_may),
        context.getString(R.string.month_jun),
        context.getString(R.string.month_jul),
        context.getString(R.string.month_aug),
        context.getString(R.string.month_sep),
        context.getString(R.string.month_oct),
        context.getString(R.string.month_nov),
        context.getString(R.string.month_dec)
    )

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

        var currentViewYear = selectedYear

        @SuppressLint("SetTextI18n")
        fun updateHeader() {
            txtYear.text = String.format(Locale.getDefault(), "%d", currentViewYear)
            titleText.text = context.getString(R.string.month_year_title, selectedMonth + 1, selectedYear)
        }

        fun updateMonthAdapter() {
            recyclerView.adapter = MonthAdapter(
                selected = selectedMonth,
                yearToHighlight = selectedYear,
                currentViewYear = currentViewYear
            ) { month ->
                selectedMonth = month
                selectedYear = currentViewYear
                updateHeader()
                updateMonthAdapter()
            }
        }

        fun animateRecycler(direction: String) {
            val distance = recyclerView.width.toFloat()
            val toX = if (direction == "left") -distance else distance

            recyclerView.animate()
                .translationX(toX)
                .setDuration(100)
                .withEndAction {
                    recyclerView.translationX = -toX
                    updateMonthAdapter()
                    recyclerView.animate()
                        .translationX(0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }

        recyclerView.layoutManager = GridLayoutManager(context, 4)
        updateHeader()
        updateMonthAdapter()

        btnPrev.setOnClickListener {
            currentViewYear--
            txtYear.text = String.format(Locale.getDefault(), "%d", currentViewYear)
            animateRecycler("right")
        }

        btnNext.setOnClickListener {
            currentViewYear++
            txtYear.text = String.format(Locale.getDefault(), "%d", currentViewYear)
            animateRecycler("left")
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnOk.setOnClickListener {
            onMonthYearSelected(selectedMonth, selectedYear)
            dialog.dismiss()
        }

        dialog.show()
        val width = (context.resources.displayMetrics.widthPixels * 0.60).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setDimAmount(0.3f)
    }

    private inner class MonthAdapter(
        private var selected: Int,
        private val yearToHighlight: Int,
        private val currentViewYear: Int,
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

            if (position == selected && currentViewYear == yearToHighlight) {
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
