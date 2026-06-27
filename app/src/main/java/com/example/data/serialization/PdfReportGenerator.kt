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
import com.example.data.local.HabayebCustomer
import com.example.data.local.HabayebTransaction
import org.json.JSONArray
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
    ) {
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
    }

    fun generateAndHandleCustomerPdfReport(
        context: Context,
        customer: HabayebCustomer,
        netDebt: Double,
        transactions: List<HabayebTransaction>,
        action: String // "VIEW" or "SHARE"
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

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

        // Colors & Paints
        val primaryColorHex = "#3F51B5"

        // Draw dynamic business profile header
        val displayedName = if (bizName.isNotBlank()) bizName else "ميزان الدار"
        val displayedDesc = if (bizDesc.isNotBlank()) bizDesc else "التطبيق المالي للتدابير وتنسيق الميزانية"

        if (bizLogoPath.isNotEmpty()) {
            try {
                val logoFile = File(bizLogoPath)
                if (logoFile.exists()) {
                    val rawBitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (rawBitmap != null) {
                        val maxW = 45f
                        val maxH = 45f
                        val originalWidth = rawBitmap.width.toFloat()
                        val originalHeight = rawBitmap.height.toFloat()
                        val scale = (maxW / originalWidth).coerceAtMost(maxH / originalHeight)
                        val finalW = (originalWidth * scale).coerceAtLeast(1f)
                        val finalH = (originalHeight * scale).coerceAtLeast(1f)
                        val scaledLogo = Bitmap.createScaledBitmap(rawBitmap, finalW.toInt(), finalH.toInt(), true)
                        
                        val logoX = 35f + ((maxW - finalW) / 2f)
                        val logoY = 40f + ((maxH - finalH) / 2f)
                        canvas.drawBitmap(scaledLogo, logoX, logoY, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val paintBizName = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintBizDesc = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintBizPhones = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        drawArabicText(canvas, displayedName, 35f, 45f, 525, paintBizName, Layout.Alignment.ALIGN_NORMAL)
        drawArabicText(canvas, displayedDesc, 35f, 65f, 525, paintBizDesc, Layout.Alignment.ALIGN_NORMAL)

        val phonesToDraw = if (bizPhones.isNotEmpty()) bizPhones else listOf("هوية بصرية معتمدة")
        val phonesStr = "📞 " + phonesToDraw.joinToString("  |  ")
        drawArabicText(canvas, phonesStr, 35f, 82f, 525, paintBizPhones, Layout.Alignment.ALIGN_NORMAL)

        // Divider Line under Header
        val paintDivider = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(35f, 115f, (pageWidth - 35).toFloat(), 115f, paintDivider)

        // Prepare content paints
        val paintTitle = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintSectionHeader = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintLabel = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintValue = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintMetadata = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintBoxFill = Paint().apply {
            color = Color.parseColor("#F8F9FA")
            style = Paint.Style.FILL
        }
        val paintBorder = Paint().apply {
            color = Color.parseColor("#E9ECEF")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val paintFooter = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        var currentY = 140f

        // Draw Report Main Title
        drawArabicText(canvas, context.getString(R.string.habayeb_pdf_title), 35f, currentY, 525, paintTitle, Layout.Alignment.ALIGN_CENTER)
        currentY += 28f

        // Metadata
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        val dateString = format.format(Date())
        
        val nameText = context.getString(R.string.habayeb_pdf_client_name, customer.name)
        drawArabicText(canvas, nameText, 35f, currentY, 525, paintMetadata, Layout.Alignment.ALIGN_NORMAL)
        currentY += 14f
        
        val phoneText = context.getString(R.string.habayeb_pdf_phone, customer.phone.ifEmpty { context.getString(R.string.habayeb_no_phone) })
        drawArabicText(canvas, phoneText, 35f, currentY, 525, paintMetadata, Layout.Alignment.ALIGN_NORMAL)
        currentY += 14f
        
        val dateText = context.getString(R.string.habayeb_pdf_date, dateString)
        drawArabicText(canvas, dateText, 35f, currentY, 525, paintMetadata, Layout.Alignment.ALIGN_NORMAL)
        currentY += 22f

        // Net value header card
        canvas.drawRoundRect(35f, currentY - 10f, (pageWidth - 35).toFloat(), currentY + 26f, 8f, 8f, paintBoxFill)
        canvas.drawRoundRect(35f, currentY - 10f, (pageWidth - 35).toFloat(), currentY + 26f, 8f, 8f, paintBorder)

        val balanceLabel = if (netDebt > 0) {
            context.getString(R.string.habayeb_pdf_balance_owed_by)
        } else if (netDebt < 0) {
            context.getString(R.string.habayeb_pdf_balance_owed_to)
        } else {
            context.getString(R.string.habayeb_pdf_balance_balanced)
        }

        val formattedNetDebt = String.format(Locale.ENGLISH, "%,.2f", Math.abs(netDebt))
        val balanceVal = context.getString(R.string.habayeb_pdf_balance_val, formattedNetDebt)

        paintLabel.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        drawArabicText(canvas, balanceLabel, 50f, currentY + 2f, 495, paintLabel, Layout.Alignment.ALIGN_NORMAL)

        paintValue.color = if (netDebt >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        drawArabicText(canvas, balanceVal, 50f, currentY + 2f, 495, paintValue, Layout.Alignment.ALIGN_OPPOSITE)

        currentY += 45f

        // Transactions list header
        drawArabicText(canvas, context.getString(R.string.habayeb_pdf_history_title), 35f, currentY, 525, paintSectionHeader, Layout.Alignment.ALIGN_NORMAL)
        currentY += 22f

        // Loop transactions
        for (tx in transactions) {
            if (currentY > pageHeight - 65f) break

            canvas.drawRoundRect(35f, currentY - 8f, (pageWidth - 35).toFloat(), currentY + 18f, 6f, 6f, paintBoxFill)
            canvas.drawRoundRect(35f, currentY - 8f, (pageWidth - 35).toFloat(), currentY + 18f, 6f, 6f, paintBorder)

            val txTypeStr = when (tx.type) {
                "OWED_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_by)
                "PAYMENT_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_by)
                "OWED_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_to)
                "PAYMENT_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_to)
                else -> context.getString(R.string.habayeb_pdf_tx_generic)
            }

            val formattedDate = try {
                val sDate = Date(tx.timestamp * 1000)
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
                sdf.format(sDate)
            } catch(e: Exception) {
                ""
            }

            val txLabel = context.getString(R.string.habayeb_pdf_tx_format, txTypeStr, tx.description, formattedDate)
            val formattedAmount = String.format(Locale.ENGLISH, "%,.2f", tx.amount)
            val txValue = context.getString(R.string.habayeb_pdf_val_format, formattedAmount)

            paintLabel.textSize = 10f
            paintLabel.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            drawArabicText(canvas, txLabel, 50f, currentY, 495, paintLabel, Layout.Alignment.ALIGN_NORMAL)

            paintValue.textSize = 10f
            paintValue.color = Color.parseColor("#34495E")
            drawArabicText(canvas, txValue, 50f, currentY, 495, paintValue, Layout.Alignment.ALIGN_OPPOSITE)

            currentY += 34f
        }

        // Footer
        drawArabicText(canvas, context.getString(R.string.habayeb_pdf_footer), 35f, (pageHeight - 35).toFloat(), 525, paintFooter, Layout.Alignment.ALIGN_CENTER)

        pdfDocument.finishPage(page)

        // Save and Share or View
        val fileName = "habayeb_${customer.name}_${System.currentTimeMillis() % 100000}.pdf"
        val file = File(context.cacheDir, fileName)
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

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
            pdfDocument.close()
        }
    }
}
