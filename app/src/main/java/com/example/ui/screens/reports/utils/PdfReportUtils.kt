package com.example.ui.screens.reports.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

data class PdfTransaction(
    val date: String,
    val description: String,
    val amount: Double,
    val type: String, // "OWED_BY_TEM", "PAYMENT_BY_THEM", "OWED_TO_THEM", "PAYMENT_TO_THEM"
    val timestamp: Long
)

// ==========================================
// UPGRADED DYNAMIC UNIVERSAL PDF GENERATOR (Overloaded for backwards compatibility)
// ==========================================
fun formatReportNumber(value: Double): String {
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
    val formatter = java.text.DecimalFormat("#,##0.00", symbols)
    return formatter.format(value)
}

fun generateModernPdfReport(
    context: Context,
    title: String,
    headers: List<String>,
    data: List<Pair<String, String>>
) {
    val isLedger = title.contains("كشف حساب")
    if (isLedger) {
        val transactionsList = data.mapIndexed { index, (dateStr, rowVal) ->
            val parts = rowVal.split(" - ")
            val amountRaw = parts.getOrNull(0) ?: "0"
            val typeRaw = parts.getOrNull(1) ?: "OWED_BY_TEM"
            
            val cleanAmt = amountRaw.replace(",", "")
                .replace("٫", ".")
                .replace("ر.ي", "")
                .replace("ريال", "")
                .replace(" ", "")
                .filter { it.isDigit() || it == '.' }
            val amountDouble = cleanAmt.toDoubleOrNull() ?: 0.0
            
            val mappedType = when {
                typeRaw.contains("PAYMENT_BY_THEM") || typeRaw.contains("استلام دفعة") || typeRaw.contains("سداد دفعة") || typeRaw.contains("تم السداد") || typeRaw.contains("سداد") -> {
                    if (title.contains("عليّ") || title.contains("ديون الدار") || title.contains("له عندنا") || typeRaw.contains("PAYMENT_TO_THEM")) {
                        "PAYMENT_TO_THEM"
                    } else {
                        "PAYMENT_BY_THEM"
                    }
                }
                typeRaw.contains("OWED_BY_TEM") || typeRaw.contains("OWED_BY_THEM") || typeRaw == "دين عليه" || typeRaw.contains("عليه") -> {
                    "OWED_BY_THEM"
                }
                typeRaw.contains("OWED_TO_THEM") || typeRaw == "دين علي" || typeRaw == "دين عليّ" || typeRaw.contains("عليّ") || typeRaw == "علي" -> {
                    "OWED_TO_THEM"
                }
                else -> {
                    "OWED_BY_THEM"
                }
            }
            
            val parsedTimestamp = try {
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
                sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                try {
                    val sdf2 = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    sdf2.parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (e2: Exception) {
                    System.currentTimeMillis() - (data.size - index) * 1000 * 60 * 60 * 24L
                }
            }
            
            PdfTransaction(
                date = dateStr,
                description = "",
                amount = amountDouble,
                type = mappedType,
                timestamp = parsedTimestamp
            )
        }
        generateModernPdfReport(context, title, transactionsList)
    } else {
        var totalAmountVal = ""
        var paidAmountVal = ""
        var remainingAmountVal = ""

        if (title.contains("المخزون") || title.contains("جرد")) {
            if (headers.size >= 5) {
                val h2 = headers[2]
                totalAmountVal = if (h2.contains(":")) h2.substringAfter(":").trim() else h2
                val h4 = headers[4]
                paidAmountVal = if (h4.contains(":")) h4.substringAfter(":").trim() else h4
                val h3 = headers[3]
                remainingAmountVal = if (h3.contains(":")) h3.substringAfter(":").trim() else h3
            } else if (headers.isNotEmpty()) {
                totalAmountVal = headers.getOrNull(0)?.substringAfter(":")?.trim() ?: ""
                paidAmountVal = headers.getOrNull(1)?.substringAfter(":")?.trim() ?: ""
                remainingAmountVal = headers.getOrNull(2)?.substringAfter(":")?.trim() ?: ""
            }
        } else {
            if (headers.size >= 1) {
                val h = headers[0]
                totalAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
            if (headers.size >= 2) {
                val h = headers[1]
                paidAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
            if (headers.size >= 3) {
                val h = headers[2]
                remainingAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
        }

        val tableHeaders = when {
            title.contains("المخزون") || title.contains("جرد") -> listOf("سعر الشراء / البيع", "البند / التصنيف والكمية")
            else -> listOf("القيمة الإجمالية", "البند / التصنيف")
        }

        val tableRows = mutableListOf<List<String>>()
        for ((rowTitle, rowVal) in data) {
            tableRows.add(listOf(rowVal, rowTitle))
        }

        generateGenericPdfReport(
            context = context,
            title = title,
            totalAmount = totalAmountVal,
            paidAmount = paidAmountVal,
            remainingAmount = remainingAmountVal,
            tableHeaders = tableHeaders,
            tableRows = tableRows
        )
    }
}

// ==========================================
// DYNAMIC PRIMARY PDF REPORT FOR LEDGERS
// ==========================================
fun generateModernPdfReport(
    context: Context,
    title: String,
    transactions: List<PdfTransaction>
) {
    // 1. حساب الـ Hash للبيانات لضبط رقم التقرير تلقائياً حسب المتغيرات
    val sharedPrefs = context.getSharedPreferences("report_counter_prefs", Context.MODE_PRIVATE)
    val customerKey = title.replace(" ", "_").replace(":", "_")
    val lastHash = sharedPrefs.getString("${customerKey}_hash", "") ?: ""
    val currentHash = transactions.joinToString { "${it.amount}_${it.type}_${it.timestamp}" }.hashCode().toString()
    
    var reportNumber = sharedPrefs.getInt("${customerKey}_number", 1)
    if (lastHash.isNotEmpty() && lastHash != currentHash) {
        reportNumber += 1 // زادت المعاملات أو تغيرت، يرتفع الرقم تلقائياً
        sharedPrefs.edit().putInt("${customerKey}_number", reportNumber).putString("${customerKey}_hash", currentHash).apply()
    } else if (lastHash.isEmpty()) {
        sharedPrefs.edit().putInt("${customerKey}_number", 1).putString("${customerKey}_hash", currentHash).apply()
    }

    // 2. قراءة بيانات النشاط التجاري الحالية ديناميكياً من كافة المصادر المتاحة
    val prefs = context.getSharedPreferences("business_profile", Context.MODE_PRIVATE)
    val altPrefs = context.getSharedPreferences("business_profile_prefs", Context.MODE_PRIVATE)

    val businessName = prefs.getString("biz_name", "").orEmpty()
        .ifBlank { altPrefs.getString("business_name", "").orEmpty() }
        .ifBlank { "الاسم التجاري" }

    val businessSlogan = prefs.getString("biz_desc", "").orEmpty()
        .ifBlank { altPrefs.getString("business_slogan", "").orEmpty() }

    var businessPhone = ""
    val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
    try {
        val jsonArray = JSONArray(phonesJson)
        if (jsonArray.length() > 0) {
            businessPhone = jsonArray.getString(0)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (businessPhone.isBlank()) {
        businessPhone = altPrefs.getString("business_phone", "").orEmpty()
    }

    val logoPath = prefs.getString("biz_logo_path", "").orEmpty()
        .ifBlank { altPrefs.getString("logo_path", "").orEmpty() }
        .ifBlank { null }

    // 3. الفرز والترتيب الحسابي التراكمي (أقدم فـ أحدث) مع تصحيح الأنواع الرياضية
    val sortedTxs = transactions.sortedBy { it.timestamp }
    val isSupplierMode = transactions.any { it.type == "OWED_TO_THEM" || it.type == "PAYMENT_TO_THEM" }
    
    var totalOwed = 0.0
    var totalPaid = 0.0
    var runningBalance = 0.0

    for (tx in sortedTxs) {
        val isOwed = tx.type == "OWED_BY_TEM" || tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
        val isPaid = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"

        if (isOwed) {
            totalOwed += tx.amount
        } else if (isPaid) {
            totalPaid += tx.amount
        }
    }

    val finalNetBalance = Math.abs(totalOwed - totalPaid)
    val netStatus = if (isSupplierMode) {
        if (totalOwed >= totalPaid) "له" else "عليه"
    } else {
        if (totalOwed >= totalPaid) "عليه" else "له"
    }

    // 4. إعداد مستند الـ PDF ومقاس الصفحة (A4)
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    var pageNum = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
    var activePage = pdfDocument.startPage(pageInfo)
    var activeCanvas = activePage.canvas

    // 5. أقلام وألوان الرسم البنكي المطور
    val primaryColorHex = "#0F5257"
    val slateGrayHex = "#E2E8F0"
    val debitRedHex = "#DC2626"
    val creditGreenHex = "#16A34A"
    val headerBgHex = "#F8FAFC"
    val textDarkHex = "#1E293B"
    val textGrayHex = "#64748B"

    val paintTextRightBold = Paint().apply {
        color = Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    
    val paintTextRightRegular = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    val paintTextLeftBold = Paint().apply {
        color = Color.parseColor(textDarkHex)
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    val paintTextLeftRegular = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    val paintTitle = Paint().apply {
        color = Color.parseColor(textDarkHex)
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    val paintLine = Paint().apply {
        color = Color.parseColor(slateGrayHex)
        strokeWidth = 1f
    }

    val rightMargin = (pageWidth - 40).toFloat()
    val leftMargin = 40f
    val centerWidth = (pageWidth / 2).toFloat()

    fun drawHeaderForPage(canvas: android.graphics.Canvas, isFirstPage: Boolean, pageNumber: Int) {
        if (isFirstPage) {
            var y = 45f

            // 3. Draw Logo in the absolute center
            if (logoPath != null) {
                try {
                    val file = File(logoPath)
                    if (file.exists()) {
                        val bitmap = decodeSampledBitmap(file.absolutePath)
                        if (bitmap != null) {
                            val maxW = 80f
                            val maxH = 55f
                            val originalWidth = bitmap.width.toFloat()
                            val originalHeight = bitmap.height.toFloat()
                            val scale = (maxW / originalWidth).coerceAtMost(maxH / originalHeight)
                            val finalW = (originalWidth * scale).coerceAtLeast(1f)
                            val finalH = (originalHeight * scale).coerceAtLeast(1f)
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, finalW.toInt(), finalH.toInt(), true)
                            
                            val logoX = centerWidth - (finalW / 2f)
                            val logoY = y + ((maxH - finalH) / 2f)
                            canvas.drawBitmap(scaledBitmap, logoX, logoY, null)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 4. Draw business details on the right top
            canvas.drawText(businessName, rightMargin, y + 12f, paintTextRightBold)
            if (businessSlogan.isNotEmpty()) {
                canvas.drawText(businessSlogan, rightMargin, y + 28f, paintTextRightRegular)
            }
            if (businessPhone.isNotEmpty()) {
                canvas.drawText("📞 هاتف: $businessPhone", rightMargin, y + 42f, paintTextRightRegular)
            }

            // 5. Draw report number and date
            val dayNameToday = SimpleDateFormat("EEEE", Locale("ar")).format(Date())
            val dateNumbersToday = SimpleDateFormat("yyyy/MM/dd hh:mm", Locale.ENGLISH).format(Date())
            val amPmToday = SimpleDateFormat("a", Locale("ar")).format(Date())
            val reportDateStrToday = "$dayNameToday - $dateNumbersToday $amPmToday"
            canvas.drawText("سجل رقم ($reportNumber)", leftMargin, y + 12f, paintTextLeftBold)
            canvas.drawText("التاريخ: $reportDateStrToday", leftMargin, y + 28f, paintTextLeftRegular)

            y += 70f
            canvas.drawLine(leftMargin, y, rightMargin, y, paintLine)

            y += 25f
            canvas.drawText(title, centerWidth, y, paintTitle)

            y += 20f

            // Summary box
            val paintSummaryBg = Paint().apply {
                color = Color.parseColor(headerBgHex)
                style = Paint.Style.FILL
            }
            val paintSummaryBorder = Paint().apply {
                color = Color.parseColor(slateGrayHex)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(leftMargin, y, rightMargin, y + 55f, 10f, 10f, paintSummaryBg)
            canvas.drawRoundRect(leftMargin, y, rightMargin, y + 55f, 10f, 10f, paintSummaryBorder)

            val paintSummaryLabel = Paint().apply {
                color = Color.parseColor(textGrayHex)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val paintSummaryValue = Paint().apply {
                color = Color.parseColor(primaryColorHex)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val (sumLabelRight, sumLabelCenter, sumLabelLeft) = if (isSupplierMode) {
                Triple("إجمالي له علينا (دين)", "إجمالي سددناه له (سداد)", "صافي المتبقي للمورد")
            } else {
                Triple("إجمالي عليه (دين)", "إجمالي سدده لنا (سداد)", "صافي المتبقي عليه")
            }

            // Right
            paintSummaryLabel.textAlign = Paint.Align.RIGHT
            paintSummaryValue.textAlign = Paint.Align.RIGHT
            canvas.drawText(sumLabelRight, rightMargin - 20f, y + 22f, paintSummaryLabel)
            canvas.drawText("${formatReportNumber(totalOwed)} ر.ي", rightMargin - 20f, y + 41f, paintSummaryValue)

            // Center
            paintSummaryLabel.textAlign = Paint.Align.CENTER
            paintSummaryValue.textAlign = Paint.Align.CENTER
            canvas.drawText(sumLabelCenter, centerWidth, y + 22f, paintSummaryLabel)
            canvas.drawText("${formatReportNumber(totalPaid)} ر.ي", centerWidth, y + 41f, paintSummaryValue)

            // Left
            paintSummaryLabel.textAlign = Paint.Align.LEFT
            paintSummaryValue.textAlign = Paint.Align.LEFT
            canvas.drawText(sumLabelLeft, leftMargin + 20f, y + 22f, paintSummaryLabel)
            canvas.drawText("${formatReportNumber(finalNetBalance)} ر.ي ($netStatus)", leftMargin + 20f, y + 41f, paintSummaryValue)
        } else {
            var y = 45f

            canvas.drawText("$title - تابع الصفحة ($pageNumber)", rightMargin, y + 12f, paintTextRightBold)
            val sdfDate = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
            canvas.drawText("التاريخ: ${sdfDate.format(Date())}", leftMargin, y + 12f, paintTextLeftRegular)
            
            y += 20f
            canvas.drawLine(leftMargin, y, rightMargin, y, paintLine)
        }
    }

    // Coordinates of columns in RTL
    val columnsX = listOf(
        rightMargin,             // اليوم
        rightMargin - 55f,       // التاريخ
        rightMargin - 125f,      // التفاصيل
        rightMargin - 310f,      // المبلغ
        leftMargin               // المتبقي
    )

    val headers = listOf("اليوم", "التاريخ", "البيان / التفاصيل", "المبلغ", "المبلغ المتبقي")

    fun drawTableHeaders(canvas: android.graphics.Canvas, y: Float) {
        val paintHeaderBg = Paint().apply {
            color = Color.parseColor(headerBgHex)
            style = Paint.Style.FILL
        }
        val paintHeaderBorder = Paint().apply {
            color = Color.parseColor(slateGrayHex)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(leftMargin, y - 14f, rightMargin, y + 10f, 6f, 6f, paintHeaderBg)
        canvas.drawRoundRect(leftMargin, y - 14f, rightMargin, y + 10f, 6f, 6f, paintHeaderBorder)

        val paintHeader = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        headers.forEachIndexed { i, headerText ->
            paintHeader.textAlign = if (i == 4) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText(headerText, columnsX[i], y + 2f, paintHeader)
        }
    }

    val paintCellText = Paint().apply {
        color = Color.parseColor(textDarkHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }

    val paintRowSeparator = Paint().apply {
        color = Color.parseColor("#F1F5F9")
        strokeWidth = 0.8f
    }

    fun drawRow(canvas: android.graphics.Canvas, y: Float, tx: PdfTransaction, index: Int, runningBal: Double) {
        canvas.drawLine(leftMargin, y + 14f, rightMargin, y + 14f, paintRowSeparator)

        val txTime = if (tx.timestamp < 10000000000L) tx.timestamp * 1000 else tx.timestamp
        val dayName = SimpleDateFormat("EEEE", Locale("ar")).format(Date(txTime))
        val dateNumbers = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date(txTime))

        val finalDesc = if (tx.description.isNotEmpty()) tx.description else {
            when (tx.type) {
                "OWED_BY_TEM", "OWED_BY_THEM" -> "دين عليه"
                "PAYMENT_BY_THEM" -> "سداد"
                "OWED_TO_THEM" -> "دين علي"
                "PAYMENT_TO_THEM" -> "تم السداد"
                else -> "معاملة"
            }
        }

        // Column 0: اليوم
        paintCellText.textAlign = Paint.Align.RIGHT
        paintCellText.color = Color.parseColor(textDarkHex)
        paintCellText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(dayName, columnsX[0], y + 4f, paintCellText)

        // Column 1: التاريخ
        canvas.drawText(dateNumbers, columnsX[1], y + 4f, paintCellText)

        // Column 2: البيان / التفاصيل
        val maxDetailsWidth = 175f
        val truncatedDetails = if (paintCellText.measureText(finalDesc) > maxDetailsWidth) {
            val length = paintCellText.breakText(finalDesc, true, maxDetailsWidth - 10f, null)
            finalDesc.substring(0, length) + "..."
        } else {
            finalDesc
        }
        canvas.drawText(truncatedDetails, columnsX[2], y + 4f, paintCellText)

        // Column 3: المبلغ
        val isPositive = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
        val pillBgColor = if (isPositive) "#F0FDF4" else "#FFF5F5"
        val pillTextColor = if (isPositive) "#16A34A" else "#DC2626"
        val prefix = if (isPositive) "+" else "-"
        val formattedAmount = "$prefix ${formatReportNumber(tx.amount)} ر.ي"

        val paintPillBg = Paint().apply {
            color = Color.parseColor(pillBgColor)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val paintPillText = Paint().apply {
            color = Color.parseColor(pillTextColor)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val textWidth = paintPillText.measureText(formattedAmount)
        val pillLeft = columnsX[3] - textWidth - 12f
        val pillRight = columnsX[3]
        canvas.drawRoundRect(pillLeft, y - 8f, pillRight, y + 10f, 4f, 4f, paintPillBg)
        canvas.drawText(formattedAmount, columnsX[3] - 6f, y + 4f, paintPillText)

        // Column 4: المبلغ المتبقي (الرصيد التراكمي بلون الرمادي)
        val balanceStr = "${formatReportNumber(Math.abs(runningBal))} ر.ي"
        val paintBalanceText = Paint().apply {
            color = Color.parseColor(textGrayHex)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        canvas.drawText(balanceStr, columnsX[4], y + 4f, paintBalanceText)
    }

    var currentY = 240f // Page 1 start
    drawHeaderForPage(activeCanvas, isFirstPage = true, pageNumber = 1)
    drawTableHeaders(activeCanvas, currentY)
    currentY += 25f

    for ((index, tx) in sortedTxs.withIndex()) {
        val isOwed = tx.type == "OWED_BY_TEM" || tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
        val isPaid = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"

        if (isOwed) {
            runningBalance += tx.amount
        } else if (isPaid) {
            runningBalance -= tx.amount
        }

        if (currentY > pageHeight - 65f) {
            val paintFooter = Paint().apply {
                color = Color.parseColor(textGrayHex)
                textSize = 8f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            activeCanvas.drawText("صفحة $pageNum | كشف حساب آلي معتمد - صادر بواسطة تطبيق ميزان الدار", centerWidth, (pageHeight - 30).toFloat(), paintFooter)

            pdfDocument.finishPage(activePage)
            
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            activePage = pdfDocument.startPage(pageInfo)
            activeCanvas = activePage.canvas
            
            drawHeaderForPage(activeCanvas, isFirstPage = false, pageNumber = pageNum)
            
            currentY = 100f
            drawTableHeaders(activeCanvas, currentY)
            currentY += 25f
        }

        drawRow(activeCanvas, currentY, tx, index, runningBalance)
        currentY += 25f
    }

    val paintFooter = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 8f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        isAntiAlias = true
    }
    activeCanvas.drawText("صفحة $pageNum | كشف حساب آلي معتمد - صادر بواسطة تطبيق ميزان الدار 🌸", centerWidth, (pageHeight - 30).toFloat(), paintFooter)
    pdfDocument.finishPage(activePage)

    val fileName = "mizan_report_${System.currentTimeMillis() % 100000}.pdf"
    
    try {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف الحساب المطور"))
        
        Toast.makeText(context, "تم توليد وحفظ كشف الحساب بنجاح في مجلد التنزيلات الخاص بالتطبيق 📂", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        pdfDocument.close()
        Toast.makeText(context, "فشل حفظ التقرير: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ==========================================
// THE EXQUISITE NEW PRIMARY PDF GENERATION SIGNATURE
// ==========================================
fun generateGenericPdfReport(
    context: Context,
    title: String,
    totalAmount: String,       // قيمة "الإجمالي" الممررة ديناميكياً
    paidAmount: String,        // قيمة "تم سداد / تم دفع" الممررة ديناميكياً
    remainingAmount: String,   // قيمة "المتبقي" الممررة ديناميكياً
    tableHeaders: List<String>,
    tableRows: List<List<String>>
) {
    // 1. Fetch business details dynamically from multiple shared preference sources
    val prefs = context.getSharedPreferences("business_profile", Context.MODE_PRIVATE)
    val altPrefs = context.getSharedPreferences("business_profile_prefs", Context.MODE_PRIVATE)

    val businessName = prefs.getString("biz_name", "").orEmpty()
        .ifBlank { altPrefs.getString("business_name", "").orEmpty() }
        .ifBlank { "ميزان الدار" }

    val businessSlogan = prefs.getString("biz_desc", "").orEmpty()
        .ifBlank { altPrefs.getString("business_slogan", "").orEmpty() }
        .ifBlank { "التطبيق المالي للتدابير وتنسيق الميزانية" }

    var businessPhone = ""
    val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
    try {
        val jsonArray = JSONArray(phonesJson)
        if (jsonArray.length() > 0) {
            businessPhone = jsonArray.getString(0)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (businessPhone.isBlank()) {
        businessPhone = altPrefs.getString("business_phone", "").orEmpty()
    }

    val logoPath = prefs.getString("biz_logo_path", null)
        ?: altPrefs.getString("logo_path", null)

    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    var pageNum = 1
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
    var activePage = pdfDocument.startPage(pageInfo)
    var activeCanvas = activePage.canvas

    // Colour definitions from modern design choices
    val primaryColorHex = "#0F5257"
    val slateGrayHex = "#E2E8F0"
    val debitRedHex = "#DC2626"
    val creditGreenHex = "#16A34A"
    val headerBgHex = "#F8FAFC"
    val textDarkHex = "#1E293B"
    val textGrayHex = "#64748B"

    // Set up modern, responsive paint configurations
    val paintTextRightBold = Paint().apply {
        color = Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    
    val paintTextRightRegular = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    val paintTextLeftBold = Paint().apply {
        color = Color.parseColor(textDarkHex)
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    val paintTextLeftRegular = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    // Starting drawing coordinates
    var currentY = 45f
    val rightMargin = (pageWidth - 40).toFloat()
    val leftMargin = 40f
    val centerWidth = (pageWidth / 2).toFloat()

    // 3. Draw Logo in the absolute center (proportional scaling, no circular frame or box)
    if (logoPath != null) {
        try {
            val file = File(logoPath)
            if (file.exists()) {
                val bitmap = decodeSampledBitmap(file.absolutePath)
                if (bitmap != null) {
                    val maxW = 80f
                    val maxH = 55f
                    val originalWidth = bitmap.width.toFloat()
                    val originalHeight = bitmap.height.toFloat()
                    val scale = (maxW / originalWidth).coerceAtMost(maxH / originalHeight)
                    val finalW = (originalWidth * scale).coerceAtLeast(1f)
                    val finalH = (originalHeight * scale).coerceAtLeast(1f)
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, finalW.toInt(), finalH.toInt(), true)
                    
                    val logoX = centerWidth - (finalW / 2f)
                    val logoY = currentY + ((maxH - finalH) / 2f)
                    activeCanvas.drawBitmap(scaledBitmap, logoX, logoY, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 4. Draw business details on the right top (RTL, close alignment)
    activeCanvas.drawText(businessName, rightMargin, currentY + 12f, paintTextRightBold)
    if (businessSlogan.isNotEmpty()) {
        activeCanvas.drawText(businessSlogan, rightMargin, currentY + 28f, paintTextRightRegular)
    }
    if (businessPhone.isNotEmpty()) {
        activeCanvas.drawText("📞 هاتف: $businessPhone", rightMargin, currentY + 42f, paintTextRightRegular)
    }

    // 5. Draw report number and stamp date on the left top (close alignment)
    val sdfDate = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
    val formattedDate = sdfDate.format(Date())
    activeCanvas.drawText("سجل رقم (1)", leftMargin, currentY + 12f, paintTextLeftBold)
    activeCanvas.drawText("التاريخ: $formattedDate", leftMargin, currentY + 28f, paintTextLeftRegular)

    currentY += 70f

    // 6. Draw clean soft modern separator line below header
    val paintLine = Paint().apply {
        color = Color.parseColor(slateGrayHex)
        strokeWidth = 1.2f
    }
    activeCanvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    currentY += 25f

    // 7. Centered report title
    val paintTitle = Paint().apply {
        color = Color.parseColor(textDarkHex)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    activeCanvas.drawText(title, centerWidth, currentY, paintTitle)

    currentY += 20f

    // 8. Custom indicators and summary metrics styled block (Curved soft rectangle)
    val paintSummaryBg = Paint().apply {
        color = Color.parseColor(headerBgHex)
        style = Paint.Style.FILL
    }
    val paintSummaryBorder = Paint().apply {
        color = Color.parseColor(slateGrayHex)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    
    activeCanvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 50f, 8f, 8f, paintSummaryBg)
    activeCanvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 50f, 8f, 8f, paintSummaryBorder)

    val paintSummaryLabel = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }
    val paintSummaryValue = Paint().apply {
        color = Color.parseColor(primaryColorHex)
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    // Determine clean localized labels depending on context
    val (labelRight, labelCenter, labelLeft) = when {
        title.contains("كشف حساب") || title.contains("ديون") || title.contains("حبايب") -> {
            Triple("إجمالي عليه (مدين)", "إجمالي له (دائن)", "الصافي الرصيد الحالي")
        }
        title.contains("ميزان الدار المالي") || title.contains("تقرير ميزان") -> {
            Triple("إجمالي الوارد الكلي", "المصروفات المنصرفة", "صافي المحصول والمتبقي")
        }
        title.contains("المخزون") || title.contains("جرد") -> {
            Triple("تكلفة شراء البضاعة", "الأرباح المتوقعة", "القيمة البيعية المقدرة")
        }
        else -> {
            Triple("الإجمالي الإجمالي", "تم دفع / سداد", "المتبقي المستحق")
        }
    }

    // Right Column (الإجمالي / الوارد / عليه)
    paintSummaryLabel.textAlign = Paint.Align.RIGHT
    paintSummaryValue.textAlign = Paint.Align.RIGHT
    activeCanvas.drawText(labelRight, rightMargin - 20f, currentY + 20f, paintSummaryLabel)
    
    val colorRight = if (labelRight.contains("عليه")) debitRedHex else primaryColorHex
    activeCanvas.drawText(
        totalAmount, 
        rightMargin - 20f, 
        currentY + 38f, 
        paintSummaryValue.apply { color = Color.parseColor(colorRight) }
    )

    // Center Column (سداد / المنصرف / له)
    paintSummaryLabel.textAlign = Paint.Align.CENTER
    paintSummaryValue.textAlign = Paint.Align.CENTER
    activeCanvas.drawText(labelCenter, centerWidth, currentY + 20f, paintSummaryLabel)
    
    val colorCenter = if (labelCenter.contains("له") || labelCenter.contains("المصروفات")) creditGreenHex else primaryColorHex
    activeCanvas.drawText(
        paidAmount, 
        centerWidth, 
        currentY + 38f, 
        paintSummaryValue.apply { color = Color.parseColor(colorCenter) }
    )

    // Left Column (المتبقي / صافي / الصافي)
    paintSummaryLabel.textAlign = Paint.Align.LEFT
    paintSummaryValue.textAlign = Paint.Align.LEFT
    activeCanvas.drawText(labelLeft, leftMargin + 20f, currentY + 20f, paintSummaryLabel)
    
    activeCanvas.drawText(
        remainingAmount, 
        leftMargin + 20f, 
        currentY + 38f, 
        paintSummaryValue.apply { color = Color.parseColor(primaryColorHex) }
    )

    currentY += 75f

    // 9. Draw Table Headers and Content beautifully with ZERO black vertical lines
    if (tableHeaders.isNotEmpty() && tableRows.isNotEmpty()) {
        var tableY = currentY
        activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBg)
        activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBorder)

        val paintTableHeader = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val numCols = tableHeaders.size
        val colWidth = (pageWidth - 80f) / numCols

        // Draw headers (RTL oriented: right to left)
        for (i in 0 until numCols) {
            val headerText = tableHeaders[i]
            val xPos = rightMargin - (i * colWidth) - (colWidth / 2f)
            paintTableHeader.textAlign = Paint.Align.CENTER
            activeCanvas.drawText(headerText, xPos, tableY + 16f, paintTableHeader)
        }

        tableY += 25f

        val paintRowText = Paint().apply {
            color = Color.parseColor(textDarkHex)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val paintRowSeparator = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            strokeWidth = 0.8f
        }

        for (rowIndex in tableRows.indices) {
            val row = tableRows[rowIndex]
            
            // Check page height space availability
            if (tableY > pageHeight - 80f) {
                pdfDocument.finishPage(activePage)
                pageNum++
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                activePage = pdfDocument.startPage(newPageInfo)
                activeCanvas = activePage.canvas
                tableY = 50f
                
                // Redraw table headers on the new page
                activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBg)
                activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBorder)
                for (i in 0 until numCols) {
                    val headerText = tableHeaders[i]
                    val xPos = rightMargin - (i * colWidth) - (colWidth / 2f)
                    paintTableHeader.textAlign = Paint.Align.CENTER
                    activeCanvas.drawText(headerText, xPos, tableY + 16f, paintTableHeader)
                }
                tableY += 25f
            }

            // Draw clean subtle row separator line before writing
            activeCanvas.drawLine(leftMargin, tableY + 22f, rightMargin, tableY + 22f, paintRowSeparator)

            // Draw non-overlapping column text elements (RTL layout)
            for (colIndex in 0 until numCols) {
                val cellText = row.getOrNull(colIndex) ?: ""
                val xPos = rightMargin - (colIndex * colWidth) - (colWidth / 2f)
                
                // Formatting depending on content
                val paintCell = Paint(paintRowText).apply {
                    textAlign = Paint.Align.CENTER
                    val isDebit = cellText.contains("عليه") || cellText.contains("مدين") || cellText.contains("دين")
                    val isCredit = cellText.contains("له") || cellText.contains("سداد") || cellText.contains("تم سداد") || cellText.contains("دائن")
                    
                    if (isDebit) {
                        color = Color.parseColor(debitRedHex)
                        typeface = Typeface.DEFAULT_BOLD
                    } else if (isCredit) {
                        color = Color.parseColor(creditGreenHex)
                        typeface = Typeface.DEFAULT_BOLD
                    } else {
                        color = Color.parseColor(textDarkHex)
                        typeface = Typeface.DEFAULT
                    }
                }
                activeCanvas.drawText(cellText, xPos, tableY + 15f, paintCell)
            }

            tableY += 26f
        }
    }

    // Modern professional footer at bottom center
    val paintFooter = Paint().apply {
        color = Color.parseColor(textGrayHex)
        textSize = 8f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        isAntiAlias = true
    }
    activeCanvas.drawText("ميزان الدار - التطبيق المالي المتكامل لتنسيق الحسابات والتدابير 🌸", centerWidth, (pageHeight - 30).toFloat(), paintFooter)

    pdfDocument.finishPage(activePage)

    // Save final rendered PDF structure and trigger sharing intents
    val fileName = "mizan_report_${System.currentTimeMillis() % 100000}.pdf"
    val file = File(context.getExternalFilesDir(null), fileName)
    try {
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.pdf_chooser_title)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.pdf_export_failed, e.message), Toast.LENGTH_LONG).show()
        pdfDocument.close()
    }
}

private fun decodeSampledBitmap(path: String, reqWidth: Int = 300, reqHeight: Int = 300): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        
        var inSampleSize = 1
        val (w: Int, h: Int) = options.outWidth to options.outHeight
        if (h > reqHeight || w > reqWidth) {
            val halfHeight = h / 2
            val halfWidth = w / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        BitmapFactory.decodeFile(path, options)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
