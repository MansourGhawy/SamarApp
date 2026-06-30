package com.example.data.serialization

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private fun drawArabicText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        paint: Paint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Int {
        val textPaint = TextPaint(paint)
        val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, textPaint, width, alignment, 1f, 0f, false)
        }
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        val paintHeaderBg = Paint().apply {
            color = Color.parseColor("#F9FAFB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(42f, y, 553f, y + 30f, paintHeaderBg)

        // Draw header border lines
        val paintHeaderBorder = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(42f, y, 553f, y, paintHeaderBorder)
        canvas.drawLine(42f, y + 30f, 553f, y + 30f, paintHeaderBorder)

        val paintHeaderText = Paint().apply {
            color = Color.parseColor("#374151")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // 1. [ م ] (التسلسل): X = 523 to 553 (عرض 30)
        drawArabicText(canvas, "م", 523f, y + 9f, 30, paintHeaderText, Layout.Alignment.ALIGN_CENTER)

        // 2. [ التاريخ ]: X = 433 to 523 (عرض 90)
        drawArabicText(canvas, "التاريخ", 433f, y + 9f, 90, paintHeaderText, Layout.Alignment.ALIGN_CENTER)

        // 3. [ البيان / التفاصيل ]: X = 242 to 433 (عرض 191)
        drawArabicText(canvas, "البيان / التفاصيل", 242f, y + 9f, 191, paintHeaderText, Layout.Alignment.ALIGN_NORMAL)

        // 4. [ عليه (دين) ]: X = 177 to 242 (عرض 65)
        drawArabicText(canvas, "عليه (دين)", 177f, y + 9f, 65, paintHeaderText, Layout.Alignment.ALIGN_CENTER)

        // 5. [ له (سداد) ]: X = 112 to 177 (عرض 65)
        drawArabicText(canvas, "له (سداد)", 112f, y + 9f, 65, paintHeaderText, Layout.Alignment.ALIGN_CENTER)

        // 6. [ المبلغ المتبقي ]: X = 42 to 112 (عرض 70)
        drawArabicText(canvas, "المتبقي", 42f, y + 9f, 70, paintHeaderText, Layout.Alignment.ALIGN_CENTER)
    }

    private fun drawSubsequentPageHeader(canvas: Canvas, customerName: String, primaryColorHex: String) {
        val paintMiniHeader = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val miniHeaderText = "$customerName - كشف حساب مالي"
        drawArabicText(canvas, miniHeaderText, 42f, 25f, 511, paintMiniHeader, Layout.Alignment.ALIGN_NORMAL)

        val paintMiniLine = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(42f, 38f, 553f, 38f, paintMiniLine)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, totalPages: Int, primaryColorHex: String, context: Context) {
        val paintFooterText = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val footerTextLeft = "صفحة $pageNum من $totalPages"
        val footerTextRight = "كشف حساب معتمد ومولد آلياً بواسطة تطبيق ميزان الدار"

        // Draw left aligned footer text (page number)
        drawArabicText(canvas, footerTextLeft, 42f, 800f, 150, paintFooterText, Layout.Alignment.ALIGN_OPPOSITE)

        // Draw right aligned footer text (verification line)
        drawArabicText(canvas, footerTextRight, 200f, 800f, 353, paintFooterText, Layout.Alignment.ALIGN_NORMAL)
    }

    private fun generatePdfFileInternal(
        context: Context,
        customer: HabayebCustomer,
        netDebt: Double,
        transactions: List<HabayebTransaction>,
        primaryColorHex: String = "#0F4C43"
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val sortedTxs = transactions.sortedBy { it.timestamp }
        val totalItems = sortedTxs.size

        // Calculate total pages dynamically
        // Page 1 fits up to 14 rows, subsequent pages fit up to 20 rows
        val totalPages = if (totalItems <= 14) {
            1
        } else {
            1 + ((totalItems - 14 + 19) / 20)
        }

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Load business profile details
        val prefs = context.getSharedPreferences("business_profile", Context.MODE_PRIVATE)
        val bizName = prefs.getString("biz_name", "") ?: ""
        val bizDesc = prefs.getString("biz_desc", "") ?: ""
        val bizLogoPath = prefs.getString("biz_logo_path", "") ?: ""
        val bizPhones = mutableListOf<String>()
        try {
            val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
            val jsonArray = JSONArray(phonesJson)
            for (i in 0 until jsonArray.length()) {
                bizPhones.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Draw dynamic business profile header
        val displayedName = if (bizName.isNotBlank()) bizName else "ميزان الدار"
        val displayedDesc = if (bizDesc.isNotBlank()) bizDesc else "التطبيق المالي للتدابير وتنسيق الميزانية"

        var hasLogo = false
        var logoW = 0f
        var logoH = 0f

        var rawBitmap: Bitmap? = null
        var scaledLogo: Bitmap? = null
        if (bizLogoPath.isNotEmpty()) {
            try {
                val logoFile = File(bizLogoPath)
                if (logoFile.exists()) {
                    // Check file size and compress/decode efficiently to avoid OutOfMemoryError
                    if (logoFile.length() > 1024 * 1024) {
                        val original = BitmapFactory.decodeFile(logoFile.absolutePath)
                        if (original != null) {
                            val stream = ByteArrayOutputStream()
                            original.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            val byteArray = stream.toByteArray()
                            rawBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                        }
                    } else {
                        rawBitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                    }
                    
                    if (rawBitmap != null) {
                        val maxW = 70f
                        val maxH = 55f
                        val originalWidth = rawBitmap.width.toFloat()
                        val originalHeight = rawBitmap.height.toFloat()
                        val scale = (maxW / originalWidth).coerceAtMost(maxH / originalHeight)
                        val finalW = (originalWidth * scale).coerceAtLeast(1f)
                        val finalH = (originalHeight * scale).coerceAtLeast(1f)
                        
                        scaledLogo = Bitmap.createScaledBitmap(rawBitmap, finalW.toInt(), finalH.toInt(), true)
                        
                        logoW = finalW
                        logoH = finalH
                        hasLogo = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Header Distribution (RTL, from Y = 42 pt to Y = 110 pt)
        val paintBizName = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintBizDesc = Paint().apply {
            color = Color.parseColor("#4B5563")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintBizPhones = Paint().apply {
            color = Color.parseColor("#6B7280")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        // Right Column (Business Info - Right-aligned at X = 553 pt, width = 180)
        val rightColX = 373f
        drawArabicText(canvas, displayedName, rightColX, 42f, 180, paintBizName, Layout.Alignment.ALIGN_NORMAL)
        drawArabicText(canvas, displayedDesc, rightColX, 60f, 180, paintBizDesc, Layout.Alignment.ALIGN_NORMAL)
        val phonesToDraw = if (bizPhones.isNotEmpty()) bizPhones else listOf("هوية بصرية معتمدة")
        val phonesStr = if (bizPhones.isNotEmpty()) "هاتف: " + phonesToDraw.joinToString("  |  ") else phonesToDraw.joinToString("  |  ")
        drawArabicText(canvas, phonesStr, rightColX, 76f, 180, paintBizPhones, Layout.Alignment.ALIGN_NORMAL)

        // Middle Column (Logo - Centered)
        if (hasLogo && scaledLogo != null) {
            val logoX = 297.5f - (logoW / 2f)
            val logoY = 42f + ((55f - logoH) / 2f)
            canvas.drawBitmap(scaledLogo, logoX, logoY, null)
        }

        // Left Column (Documentation Time - Left-aligned at X = 42 pt, width = 180)
        val dayNameFormatted = SimpleDateFormat("EEEE", Locale("ar")).format(Date())
        val dateOnlyFormatted = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date())
        val timeFormatted = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())

        val docDateText = "حرر في: $dayNameFormatted - $dateOnlyFormatted"
        val docTimeText = "الوقت: $timeFormatted"

        val paintLeft1 = Paint().apply {
            color = Color.parseColor("#4B5563")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintLeft2 = Paint().apply {
            color = Color.parseColor("#6B7280")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        drawArabicText(canvas, docDateText, 42f, 45f, 180, paintLeft1, Layout.Alignment.ALIGN_OPPOSITE)
        drawArabicText(canvas, docTimeText, 42f, 62f, 180, paintLeft2, Layout.Alignment.ALIGN_OPPOSITE)

        // Divider Line under Header
        val paintDivider = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(42f, 110f, 553f, 110f, paintDivider)

        // 4. Title Area (Y = 140 pt)
        val paintTitle = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        drawArabicText(canvas, "كشف حساب: ${customer.name}", 42f, 130f, 511, paintTitle, Layout.Alignment.ALIGN_CENTER)

        // Calculate Totals precisely
        var totalDebts = 0.0
        var totalPayments = 0.0
        for (tx in sortedTxs) {
            if (tx.type == "OWED_BY_THEM" || tx.type == "PAYMENT_TO_THEM") {
                totalDebts += tx.amount
            } else if (tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM") {
                totalPayments += tx.amount
            }
        }

        val currencySymbol = "ريال"
        val formattedDebts = String.format(Locale.ENGLISH, "%,.2f", totalDebts) + " " + currencySymbol
        val formattedPayments = String.format(Locale.ENGLISH, "%,.2f", totalPayments) + " " + currencySymbol
        val formattedNet = String.format(Locale.ENGLISH, "%,.2f", Math.abs(netDebt)) + " " + currencySymbol

        val netStatus = if (netDebt > 0) {
            "(مطلوب منه)"
        } else if (netDebt < 0) {
            "(مطلوب له)"
        } else {
            "(متعادل)"
        }

        // Summary Cards (Y = 180 pt to Y = 230 pt)
        val paintCardBg = Paint().apply {
            color = Color.parseColor("#FBFBFB")
            style = Paint.Style.FILL
        }
        val paintCardBorder = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }

        canvas.drawRoundRect(396f, 180f, 553f, 230f, 6f, 6f, paintCardBg)
        canvas.drawRoundRect(396f, 180f, 553f, 230f, 6f, 6f, paintCardBorder)

        canvas.drawRoundRect(219f, 180f, 376f, 230f, 6f, 6f, paintCardBg)
        canvas.drawRoundRect(219f, 180f, 376f, 230f, 6f, 6f, paintCardBorder)

        canvas.drawRoundRect(42f, 180f, 199f, 230f, 6f, 6f, paintCardBg)
        canvas.drawRoundRect(42f, 180f, 199f, 230f, 6f, 6f, paintCardBorder)

        val paintCardLabel = Paint().apply {
            color = Color.parseColor("#6B7280")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintCardVal = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintCardValNet = Paint().apply {
            color = Color.parseColor("#1E3A8A") // Dark Blue/Navy
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Card 1 (Right):
        drawArabicText(canvas, "إجمالي ما عليه", 396f, 188f, 157, paintCardLabel, Layout.Alignment.ALIGN_CENTER)
        drawArabicText(canvas, formattedDebts, 396f, 207f, 157, paintCardVal, Layout.Alignment.ALIGN_CENTER)

        // Card 2 (Middle):
        drawArabicText(canvas, "إجمالي ما له", 219f, 188f, 157, paintCardLabel, Layout.Alignment.ALIGN_CENTER)
        drawArabicText(canvas, formattedPayments, 219f, 207f, 157, paintCardVal, Layout.Alignment.ALIGN_CENTER)

        // Card 3 (Left):
        drawArabicText(canvas, "صافي المتبقي", 42f, 188f, 157, paintCardLabel, Layout.Alignment.ALIGN_CENTER)
        val netText = "$formattedNet $netStatus"
        drawArabicText(canvas, netText, 42f, 207f, 157, paintCardValNet, Layout.Alignment.ALIGN_CENTER)

        // 5. Dynamic Table starts at Y = 250 pt
        drawTableHeader(canvas, 250f)

        val paintCellNormal = Paint().apply {
            color = Color.parseColor("#374151")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintCellBold = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintEmptyDash = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        // Paints for rounded boxes in cells
        val paintOwedBg = Paint().apply {
            color = Color.parseColor("#FEF2F2")
            style = Paint.Style.FILL
        }
        val paintOwedText = Paint().apply {
            color = Color.parseColor("#B91C1C")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintPaymentBg = Paint().apply {
            color = Color.parseColor("#F0FDF4")
            style = Paint.Style.FILL
        }
        val paintPaymentText = Paint().apply {
            color = Color.parseColor("#156534")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintDayText = Paint().apply {
            color = Color.parseColor("#4B5563")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintDateText = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textPaintDesc = TextPaint(paintCellNormal)

        var currentY = 280f
        var runningBal = 0.0
        val rowHeight = 35f

        for ((index, tx) in sortedTxs.withIndex()) {
            if (tx.type == "OWED_BY_THEM" || tx.type == "PAYMENT_TO_THEM") {
                runningBal += tx.amount
            } else {
                runningBal -= tx.amount
            }

            // check page limit (780f maximum for rows)
            if (currentY + rowHeight > 780f) {
                // Finish current page
                drawFooter(canvas, currentPageNumber, totalPages, primaryColorHex, context)
                pdfDocument.finishPage(page)

                // Start new page
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                // Draw subtle header on subsequent page
                drawSubsequentPageHeader(canvas, customer.name, primaryColorHex)

                // Draw table header at Y = 60f
                currentY = 60f
                drawTableHeader(canvas, currentY)
                currentY += 30f
            }

            // Row background and line decoration (Faint row divider)
            val paintRowDivider = Paint().apply {
                color = Color.parseColor("#F3F4F6")
                strokeWidth = 0.5f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(42f, currentY + rowHeight, 553f, currentY + rowHeight, paintRowDivider)

            // Draw index + 1 as sequence [م]
            val seqNo = (index + 1).toString()
            drawArabicText(canvas, seqNo, 523f, currentY + 11f, 30, paintCellNormal, Layout.Alignment.ALIGN_CENTER)

            // Draw Day & Date
            val dayName = try {
                SimpleDateFormat("EEEE", Locale("ar")).format(Date(tx.timestamp * 1000))
            } catch (e: Exception) {
                ""
            }
            val formattedDate = try {
                SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date(tx.timestamp * 1000))
            } catch (e: Exception) {
                ""
            }
            drawArabicText(canvas, dayName, 433f, currentY + 6f, 90, paintDayText, Layout.Alignment.ALIGN_CENTER)
            drawArabicText(canvas, formattedDate, 433f, currentY + 19f, 90, paintDateText, Layout.Alignment.ALIGN_CENTER)

            // Draw Description (RTL layout wrapper)
            val txTypeStr = when (tx.type) {
                "OWED_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_by)
                "PAYMENT_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_by)
                "OWED_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_to)
                "PAYMENT_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_to)
                else -> context.getString(R.string.habayeb_pdf_tx_generic)
            }
            val txLabel = if (tx.description.isNotEmpty()) "$txTypeStr - ${tx.description}" else txTypeStr
            
            val layoutDesc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(txLabel, 0, txLabel.length, textPaintDesc, 191)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(txLabel, textPaintDesc, 191, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            }
            val descYOffset = (rowHeight - layoutDesc.height) / 2f
            canvas.save()
            canvas.translate(242f, currentY + descYOffset)
            layoutDesc.draw(canvas)
            canvas.restore()

            // Draw Owed or Payment values
            val formattedAmount = String.format(Locale.ENGLISH, "%,.2f", tx.amount)
            if (tx.type == "OWED_BY_THEM" || tx.type == "PAYMENT_TO_THEM") {
                // Owed cell (Red tint badge)
                val badgeLeft = 182f
                val badgeTop = currentY + 8.5f
                val badgeRight = 237f
                val badgeBottom = currentY + 26.5f
                canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 4f, 4f, paintOwedBg)
                drawArabicText(canvas, formattedAmount, 182f, currentY + 11f, 55, paintOwedText, Layout.Alignment.ALIGN_CENTER)

                // Payment empty cell
                drawArabicText(canvas, "-", 112f, currentY + 11f, 65, paintEmptyDash, Layout.Alignment.ALIGN_CENTER)
            } else {
                // Owed empty cell
                drawArabicText(canvas, "-", 177f, currentY + 11f, 65, paintEmptyDash, Layout.Alignment.ALIGN_CENTER)

                // Payment cell (Green tint badge)
                val badgeLeft = 117f
                val badgeTop = currentY + 8.5f
                val badgeRight = 172f
                val badgeBottom = currentY + 26.5f
                canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 4f, 4f, paintPaymentBg)
                drawArabicText(canvas, formattedAmount, 117f, currentY + 11f, 55, paintPaymentText, Layout.Alignment.ALIGN_CENTER)
            }

            // Draw Running Balance
            val formattedRunning = String.format(Locale.ENGLISH, "%,.2f", runningBal)
            drawArabicText(canvas, formattedRunning, 42f, currentY + 11f, 70, paintCellBold, Layout.Alignment.ALIGN_CENTER)

            currentY += rowHeight
        }

        // Draw final page footer and finish page
        drawFooter(canvas, currentPageNumber, totalPages, primaryColorHex, context)
        pdfDocument.finishPage(page)

        // Recycle bitmaps safely
        try {
            rawBitmap?.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            scaledLogo?.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Save and return the report file
        val fileName = "habayeb_${customer.name}_${System.currentTimeMillis() % 100000}.pdf"
        val file = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    private fun triggerShareOrViewIntent(context: Context, file: File?, action: String) {
        if (file == null) {
            Toast.makeText(context, context.getString(R.string.habayeb_toast_pdf_export_failed, "فشل إنشاء الملف"), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            if (action == "SHARE") {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.pdf_chooser_title)))
            } else {
                // VIEW/OPEN action
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.habayeb_toast_pdf_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndHandleCustomerPdfReport(
        context: Context,
        customer: HabayebCustomer,
        netDebt: Double,
        transactions: List<HabayebTransaction>,
        action: String, // "VIEW" or "SHARE"
        primaryColorHex: String = "#0F4C43"
    ) {
        val file = generatePdfFileInternal(context, customer, netDebt, transactions, primaryColorHex)
        triggerShareOrViewIntent(context, file, action)
    }

    fun generateAndHandleCustomerPdfReportAsync(
        context: Context,
        scope: CoroutineScope,
        customer: HabayebCustomer,
        netDebt: Double,
        transactions: List<HabayebTransaction>,
        action: String, // "VIEW" or "SHARE"
        primaryColorHex: String = "#0F4C43",
        onFinished: () -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            val file = generatePdfFileInternal(context, customer, netDebt, transactions, primaryColorHex)
            withContext(Dispatchers.Main) {
                triggerShareOrViewIntent(context, file, action)
                onFinished()
            }
        }
    }
}
