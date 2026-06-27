package com.example.ui.screens.reports.utils

import android.content.Context
import android.content.Intent
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
    val processedRows = mutableListOf<List<String>>()

    for ((index, tx) in sortedTxs.withIndex()) {
        val isOwed = tx.type == "OWED_BY_TEM" || tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
        val isPaid = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"

        if (isOwed) {
            totalOwed += tx.amount
            runningBalance += tx.amount
        } else if (isPaid) {
            totalPaid += tx.amount
            runningBalance -= tx.amount
        }

        // إرفاق اسم اليوم مع التاريخ تلقائياً للوضوح العالي مع بوابة فحص أمان للأمواج الزمنية (تحويل الثواني لملي ثانية إذا لزم)
        val txTime = if (tx.timestamp < 10000000000L) tx.timestamp * 1000 else tx.timestamp
        val dayName = SimpleDateFormat("EEEE", Locale("ar")).format(Date(txTime))
        val dateNumbers = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date(txTime))
        val formattedDateWithDay = "$dayName - $dateNumbers"

        val finalDesc = if (tx.description.isNotEmpty()) tx.description else {
            when (tx.type) {
                "OWED_BY_TEM", "OWED_BY_THEM" -> "دين عليه"
                "PAYMENT_BY_THEM" -> "سداد"
                "OWED_TO_THEM" -> "دين علي"
                "PAYMENT_TO_THEM" -> "تم السداد"
                else -> "معاملة"
            }
        }

        // إزالة النصوص الزائدة (له/عليه) من سطور الجدول وإظهار الأرقام فقط للتناسق البصري
        val balanceStr = "${formatReportNumber(Math.abs(runningBalance))} ر.ي"

        processedRows.add(
            listOf(
                (index + 1).toString(),
                formattedDateWithDay,
                finalDesc,
                if (isOwed) "${formatReportNumber(tx.amount)} ر.ي" else "-",
                if (isPaid) "${formatReportNumber(tx.amount)} ر.ي" else "-",
                balanceStr
            )
        )
    }

    val finalNetBalance = Math.abs(runningBalance)
    val netStatus = if (isSupplierMode) {
        if (runningBalance >= 0) "له" else "عليه"
    } else {
        if (runningBalance >= 0) "عليه" else "له"
    }

    // 4. إعداد مستند الـ PDF ومقاس الصفحة (A4)
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    // 5. أقلام وألوان الرسم البنكي
    val primaryColorHex = "#0F5257"
    val paintPrimaryBold = Paint().apply {
        color = Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val paintTextRightRegular = Paint().apply {
        color = Color.DKGRAY
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    val paintTextLeftBold = Paint().apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    val paintTextLeftRegular = Paint().apply {
        color = Color.GRAY
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    var currentY = 40f
    val rightMargin = (pageWidth - 40).toFloat()
    val leftMargin = 40f
    val centerWidth = (pageWidth / 2).toFloat()

    // 6. رسم الهوية البصرية (Logo) بالمنتصف تماماً دون أي إطار دائري أو شكل هندسي يفرضه (تحجيم تناسبي دقيق لجمال تام)
    if (logoPath != null) {
        try {
            val file = File(logoPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
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
                    canvas.drawBitmap(scaledBitmap, logoX, logoY, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 7. رسم بيانات النشاط التجاري جهة اليمين (RTL ومتناسقة)
    canvas.drawText(businessName, rightMargin, currentY + 14f, paintPrimaryBold)
    if (businessSlogan.isNotEmpty()) {
        canvas.drawText(businessSlogan, rightMargin, currentY + 32f, paintTextRightRegular)
    }
    if (businessPhone.isNotEmpty()) {
        canvas.drawText("📞 هاتف: $businessPhone", rightMargin, currentY + 48f, paintTextRightRegular)
    }

    // 8. رسم رقم التقرير وتاريخ اصداره اللحظي باليوم والساعة جهة اليسار
    val dayName = SimpleDateFormat("EEEE", Locale("ar")).format(Date())
    val dateNumbers = SimpleDateFormat("yyyy/MM/dd hh:mm", Locale.ENGLISH).format(Date())
    val amPm = SimpleDateFormat("a", Locale("ar")).format(Date())
    val reportDateStr = "$dayName - $dateNumbers $amPm"
    canvas.drawText("سجل رقم: #$reportNumber", leftMargin, currentY + 14f, paintTextLeftBold)
    canvas.drawText("حُرر في: $reportDateStr", leftMargin, currentY + 32f, paintTextLeftRegular)

    currentY += 75f

    // 9. خط فاصل رقيق وناعم جداً أسفل الترويسة الموحدة
    val paintLine = Paint().apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1f
    }
    canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    currentY += 25f

    // 10. عنوان كشف الحساب بالمنتصف
    val paintTitle = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(title, centerWidth, currentY, paintTitle)

    currentY += 20f

    // 11. رسم كرت ملخص المؤشرات المالي الخالي من السوالب والمعاد صياغته محاسبياً وبطريقة ذكية تفرق بين المورد والعميل
    val paintSummaryBg = Paint().apply {
        color = Color.parseColor("#F8FAFC")
        style = Paint.Style.FILL
    }
    val paintSummaryBorder = Paint().apply {
        color = Color.parseColor("#E2E8F0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    canvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 55f, 10f, 10f, paintSummaryBg)
    canvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 55f, 10f, 10f, paintSummaryBorder)

    val paintSummaryLabel = Paint().apply {
        color = Color.GRAY
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

    // أ. اليمين (إجمالي عليه)
    paintSummaryLabel.textAlign = Paint.Align.RIGHT
    paintSummaryValue.textAlign = Paint.Align.RIGHT
    canvas.drawText(sumLabelRight, rightMargin - 20f, currentY + 22f, paintSummaryLabel)
    canvas.drawText("${formatReportNumber(totalOwed)} ر.ي", rightMargin - 20f, currentY + 41f, paintSummaryValue)

    // ب. المنتصف (إجمالي له)
    paintSummaryLabel.textAlign = Paint.Align.CENTER
    paintSummaryValue.textAlign = Paint.Align.CENTER
    canvas.drawText(sumLabelCenter, centerWidth, currentY + 22f, paintSummaryLabel)
    canvas.drawText("${formatReportNumber(totalPaid)} ر.ي", centerWidth, currentY + 41f, paintSummaryValue)

    // ج. اليسار (المبلغ المتبقي الحالي)
    paintSummaryLabel.textAlign = Paint.Align.LEFT
    paintSummaryValue.textAlign = Paint.Align.LEFT
    canvas.drawText(sumLabelLeft, leftMargin + 20f, currentY + 22f, paintSummaryLabel)
    canvas.drawText("${formatReportNumber(finalNetBalance)} ر.ي ($netStatus)", leftMargin + 20f, currentY + 41f, paintSummaryValue)

    currentY += 80f

    // 12. إحداثيات الأعمدة لتبدأ عربياً بالكامل (RTL من اليمين لليسار) وتجعل مسلسل مستقلاً
    val columnsX = listOf(
        rightMargin,        // مسلسل (أول عمود يمين) - مستقل بفراغ
        rightMargin - 40f,  // التاريخ
        rightMargin - 150f, // البيان / التفاصيل
        rightMargin - 290f, // عليه (دين)
        rightMargin - 370f, // له (سداد)
        leftMargin          // المبلغ المتبقي (أول عمود يسار)
    )

    val headers = listOf("مسلسل", "التاريخ", "البيان / التفاصيل", "عليه (دين)", "له (سداد)", "المبلغ المتبقي")
    
    val paintHeader = Paint().apply {
        color = Color.parseColor(primaryColorHex)
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val paintCell = Paint().apply {
        color = Color.BLACK
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    // رسم ترويسة الجدول من اليمين لليسار
    headers.forEachIndexed { i, headerText ->
        paintHeader.textAlign = if (i == 5) Paint.Align.LEFT else Paint.Align.RIGHT
        canvas.drawText(headerText, columnsX[i], currentY, paintHeader)
    }

    currentY += 12f
    canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    // رسم فاصل عمودي ناعم جداً لفصل المسلسل كلياً عن الجدول
    canvas.drawLine(rightMargin - 28f, currentY - 18f, rightMargin - 28f, pageHeight - 60f, paintLine)

    currentY += 20f

    // 13. رسم الخلايا والصفوف بدقة فائقة وبمستطيلات رشيقة ملونة خلف المبالغ لمنع التداخل
    val paintCellBg = Paint().apply {
        style = Paint.Style.FILL
    }

    for (row in processedRows) {
        if (currentY > pageHeight - 60f) {
            break 
        }

        row.forEachIndexed { i, cellText ->
            paintCell.textAlign = if (i == 5) Paint.Align.LEFT else Paint.Align.RIGHT
            
            // تهيئة الخطوط والألوان والخلفيات الرشيقة للخلايا
            when (i) {
                3 -> { // عليه (دين) - مستطيل أحمر ناعم وخفيف جداً خلف المبلغ
                    if (cellText != "-") {
                        paintCellBg.color = Color.parseColor("#FFF5F5") // خلفية ناعمة جداً
                        canvas.drawRoundRect(columnsX[i] - 70f, currentY - 10f, columnsX[i] + 5f, currentY + 4f, 4f, 4f, paintCellBg)
                        paintCell.color = Color.parseColor("#DC2626")
                    } else {
                        paintCell.color = Color.BLACK
                    }
                }
                4 -> { // له (سداد) - مستطيل أخضر ناعم وخفيف جداً خلف المبلغ
                    if (cellText != "-") {
                        paintCellBg.color = Color.parseColor("#F0FDF4") // خلفية ناعمة جداً
                        canvas.drawRoundRect(columnsX[i] - 70f, currentY - 10f, columnsX[i] + 5f, currentY + 4f, 4f, 4f, paintCellBg)
                        paintCell.color = Color.parseColor("#16A34A")
                    } else {
                        paintCell.color = Color.BLACK
                    }
                }
                5 -> { // المبلغ المتبقي (الرصيد التراكمي بدون نصوص زائدة لتفادي التداخل)
                    paintCell.color = Color.parseColor(primaryColorHex)
                    paintCell.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                else -> {
                    paintCell.color = Color.BLACK
                    paintCell.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
            }
            canvas.drawText(cellText, columnsX[i], currentY, paintCell)
        }

        currentY += 12f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)
        currentY += 16f
    }

    // 14. تذييل التقرير
    val paintFooter = Paint().apply {
        color = Color.GRAY
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("كشف حساب آلي معتمد - صادر بواسطة تطبيق ميزان الدار", centerWidth, (pageHeight - 30).toFloat(), paintFooter)

    pdfDocument.finishPage(page)

    // 15. ميزة الحفظ التلقائي الآمن الخالي من الانهيار والمطابق لكافة الصلاحيات (في مجلد Downloads الخاص بالتطبيق)
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
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
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
