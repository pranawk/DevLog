package com.matrix.devlog.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.matrix.devlog.data.PlatformAccount
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object WidgetDrawingHelper {

    fun drawWidgetBitmap(context: Context, account: PlatformAccount, isDarkMode: Boolean = true): Bitmap {
        val scale = 3.0f
        val baseWidth = 600
        val baseHeight = 300
        val width = (baseWidth * scale).toInt()
        val height = (baseHeight * scale).toInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background
        val bgResId = context.resources.getIdentifier("bg_${account.id}", "drawable", context.packageName)
        val bgDrawable = if (bgResId != 0) ContextCompat.getDrawable(context, bgResId) else null

        if (bgDrawable != null) {
            val bgBitmap = if (bgDrawable is BitmapDrawable) {
                bgDrawable.bitmap
            } else {
                val b = Bitmap.createBitmap(bgDrawable.intrinsicWidth, bgDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                bgDrawable.setBounds(0, 0, c.width, c.height)
                bgDrawable.draw(c)
                b
            }
            val destRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val path = Path().apply { addRoundRect(destRect, 48f * scale, 48f * scale, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bgBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val bgColor = if (isDarkMode) Color.parseColor("#121214") else Color.parseColor("#F5F5F7")
            val bgPaint = Paint().apply { isAntiAlias = true; color = bgColor; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 48f * scale, 48f * scale, bgPaint)
        }

        val mainTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val subTextColor = if (isDarkMode) Color.parseColor("#80808C") else Color.parseColor("#6E6E73")
        val gridLabelColor = if (isDarkMode) Color.parseColor("#60606C") else Color.parseColor("#86868B")
        val emptyCellColor = if (isDarkMode) Color.parseColor("#1E1F22") else Color.parseColor("#E5E5EA")

        val contributions = mutableMapOf<String, Int>()
        try {
            if (account.cachedDataJson.isNotBlank()) {
                val json = JSONObject(account.cachedDataJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    contributions[key] = json.optInt(key, 0)
                }
            }
        } catch (e: Exception) {}

        val colors = getColorPalette(account.colorTheme, isDarkMode)
        val padding = 32f * scale
        
        val labelPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor(colors.accentColorHex); textSize = 22f * scale; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        canvas.drawText("Progress", padding, padding + 15f * scale, labelPaint)

        val totalPaint = Paint().apply { isAntiAlias = true; color = mainTextColor; textSize = 50f * scale; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val solvedText = if (account.totalProblems > 0) "${account.totalSolved}" else "${account.totalContributions}"
        canvas.drawText(solvedText, padding, padding + 65f * scale, totalPaint)
        
        val solvedWidth = totalPaint.measureText(solvedText)
        val subtextPaint = Paint().apply { isAntiAlias = true; color = subTextColor; textSize = 22f * scale; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val totalSuffix = if (account.totalProblems > 0) "/${account.totalProblems}" else " total"
        canvas.drawText(totalSuffix, padding + solvedWidth + 6f * scale, padding + 65f * scale, subtextPaint)

        val userPaint = Paint().apply { isAntiAlias = true; color = subTextColor; textSize = 16f * scale; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) }
        canvas.drawText("${account.id.replaceFirstChar { it.uppercase() }}: @${account.username}", padding, padding + 90f * scale, userPaint)

        // 4. Draw Circular Platform Logo (Top Right)
        val logoRadius = 38f * scale
        val logoCenterX = width - padding - logoRadius
        val logoCenterY = padding + logoRadius - 5f * scale
        
        val logoResId = context.resources.getIdentifier("logo_${account.id}", "drawable", context.packageName)
        val logoDrawable = if (logoResId != 0) ContextCompat.getDrawable(context, logoResId) else null
        
        if (logoDrawable != null) {
            drawCircularLogo(canvas, logoDrawable, logoCenterX, logoCenterY, logoRadius, isDarkMode)
        }

        // HEATMAP LOGIC
        val squareSize = 13f * scale
        val spacing = 4f * scale
        val monthGap = 12f * scale
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Calendar.getInstance()
        
        val monthsToShow = 6
        val monthsList = mutableListOf<Calendar>()
        for (i in (monthsToShow - 1) downTo 0) {
            val m = Calendar.getInstance()
            m.add(Calendar.MONTH, -i)
            monthsList.add(m)
        }

        // Calculation Phase for dynamic width (Only counting columns that have at least one visible square)
        var totalGridWidth = 0f
        val monthVisibleColCounts = mutableListOf<Int>()
        for (i in 0 until monthsList.size) {
            val mCal = monthsList[i]
            val tempCal = Calendar.getInstance()
            tempCal.set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), 1, 0, 0, 0)
            val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            
            // Days in month up to today (if current month)
            val isCurrentMonth = mCal.get(Calendar.MONTH) == now.get(Calendar.MONTH) && mCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            val maxDaysToConsider = if (isCurrentMonth) now.get(Calendar.DAY_OF_MONTH) else mCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            // Columns containing at least one day up to today
            val visibleColsInMonth = Math.ceil((firstDayOfWeek - 1 + maxDaysToConsider).toDouble() / 7).toInt()
            monthVisibleColCounts.add(visibleColsInMonth)
            
            totalGridWidth += visibleColsInMonth * (squareSize + spacing) - spacing
            if (i < monthsList.size - 1) totalGridWidth += monthGap
        }

        val gridStartX = (width - totalGridWidth) / 2f
        val monthLabelHeight = 22f * scale
        val gridHeight = 7 * (squareSize + spacing) - spacing
        val gridStartY = height - padding - monthLabelHeight - gridHeight

        val cellPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val monthLabelPaint = Paint().apply { isAntiAlias = true; color = gridLabelColor; textSize = 11f * scale; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

        var currentX = gridStartX
        for (i in 0 until monthsList.size) {
            val mCal = monthsList[i]
            val colsToShow = monthVisibleColCounts[i]
            if (colsToShow <= 0) continue

            val tempCal = Calendar.getInstance()
            tempCal.set(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH), 1, 0, 0, 0)
            val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            // Month Label centered under visible columns
            val monthLabel = SimpleDateFormat("MMM", Locale.US).format(tempCal.time).uppercase()
            val visibleMonthWidth = (colsToShow * (squareSize + spacing) - spacing)
            canvas.drawText(monthLabel, currentX + visibleMonthWidth / 2f, height - padding + 5f * scale, monthLabelPaint)

            for (day in 1..daysInMonth) {
                tempCal.set(Calendar.DAY_OF_MONTH, day)
                if (tempCal.after(now)) continue // STRICT HIDE FUTURE

                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                val colInMonth = (day + firstDayOfWeek - 2) / 7
                val row = dayOfWeek - 1
                
                val x = currentX + colInMonth * (squareSize + spacing)
                val y = gridStartY + row * (squareSize + spacing)
                
                val dateStr = dateFormat.format(tempCal.time)
                val count = contributions[dateStr] ?: 0
                val level = determineLevel(count)
                
                cellPaint.color = if (level == 0) emptyCellColor else Color.parseColor(colors.levelColorsHex[level])
                canvas.drawRoundRect(RectF(x, y, x + squareSize, y + squareSize), 3.5f * scale, 3.5f * scale, cellPaint)
            }
            currentX += colsToShow * (squareSize + spacing) + monthGap
        }
        return bitmap
    }

    private fun determineLevel(count: Int): Int {
        return when {
            count <= 0 -> 0
            count <= 2 -> 1
            count <= 5 -> 2
            count <= 9 -> 3
            else -> 4
        }
    }

    private fun drawCircularLogo(canvas: Canvas, drawable: Drawable, cx: Float, cy: Float, r: Float, isDarkMode: Boolean) {
        val bgPaint = Paint().apply { isAntiAlias = true; color = if (isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"); style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, r, bgPaint)
        val bitmap = if (drawable is BitmapDrawable) { drawable.bitmap } else {
            val b = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            drawable.setBounds(0, 0, c.width, c.height)
            drawable.draw(c)
            b
        }
        val destRect = RectF(cx - r, cy - r, cx + r, cy + r)
        val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path); canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)); canvas.restore()
    }

    private fun getColorPalette(theme: String, isDarkMode: Boolean): ThemeColors {
        val baseTheme = theme.uppercase()
        return when (baseTheme) {
            "BLUE" -> ThemeColors(accentColorHex = if (isDarkMode) "#3399FF" else "#007AFF", levelColorsHex = if (isDarkMode) listOf("#1E1F22", "#0A3055", "#004F9F", "#0077E6", "#3399FF") else listOf("#E5E5EA", "#B2D7FF", "#66B2FF", "#007AFF", "#0056B3"))
            "RED" -> ThemeColors(accentColorHex = if (isDarkMode) "#EF4444" else "#FF3B30", levelColorsHex = if (isDarkMode) listOf("#1E1F22", "#4C1010", "#801A1A", "#C02626", "#EF4444") else listOf("#E5E5EA", "#FFC7C3", "#FF8E85", "#FF3B30", "#C60000"))
            "ORANGE" -> ThemeColors(accentColorHex = if (isDarkMode) "#F97316" else "#FF9500", levelColorsHex = if (isDarkMode) listOf("#1E1F22", "#4F250A", "#8E3E0F", "#D96B27", "#F97316") else listOf("#E5E5EA", "#FFE2B3", "#FFC566", "#FF9500", "#CC7700"))
            "PURPLE" -> ThemeColors(accentColorHex = if (isDarkMode) "#A855F7" else "#AF52DE", levelColorsHex = if (isDarkMode) listOf("#1E1F22", "#30104C", "#5B1A8F", "#8B26D9", "#A855F7") else listOf("#E5E5EA", "#EBD1FF", "#D6A3FF", "#AF52DE", "#8E00D6"))
            else -> ThemeColors(accentColorHex = if (isDarkMode) "#39D353" else "#34C759", levelColorsHex = if (isDarkMode) listOf("#1E1F22", "#0E4429", "#006D32", "#26A641", "#39D353") else listOf("#E5E5EA", "#C4F2D0", "#87E69E", "#34C759", "#248A3D"))
        }
    }

    data class ThemeColors(val accentColorHex: String, val levelColorsHex: List<String>)
}
