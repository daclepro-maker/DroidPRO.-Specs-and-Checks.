package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    // Helper for formatting sizes nicely
    private fun formatSize(bytes: Long): String {
        val kilobytes = bytes / 1024.0
        val megabytes = kilobytes / 1024.0
        val gigabytes = megabytes / 1024.0
        return when {
            gigabytes >= 1.0 -> String.format(Locale.US, "%.2f GB", gigabytes)
            megabytes >= 1.0 -> String.format(Locale.US, "%.2f MB", megabytes)
            kilobytes >= 1.0 -> String.format(Locale.US, "%.1f KB", kilobytes)
            else -> "$bytes Bytes"
        }
    }

    fun exportReport(
        context: Context,
        rootResult: RootCheckResult,
        cpuSpecs: CpuSpecs,
        batterySpecs: BatterySpecs,
        ramStatus: RamStatus,
        partitions: List<StoragePartition>,
        currentTemp: Float,
        shareAfterExport: Boolean,
        onComplete: (success: Boolean, message: String, uri: Uri?) -> Unit
    ) {
        val pdfDocument = PdfDocument()
        
        // A4 page parameters standard (595 x 842 points)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Paint definitions
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 11f
            isAntiAlias = true
        }

        val headerLabelPaint = Paint().apply {
            color = Color.parseColor("#374151") // Dark grey
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val keyPaint = Paint().apply {
            color = Color.parseColor("#4B5563") // Slate grey
            textSize = 11f
            isAntiAlias = true
        }

        val valPaint = Paint().apply {
            color = Color.parseColor("#111827") // Near black
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val badgeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Draw Deep Space Header Accent Banner
        val headerRect = RectF(24f, 24f, 595f - 24f, 134f)
        val clipPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Elegant navy/slate
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(headerRect, 8f, 8f, clipPaint)

        // Draw title inside header
        canvas.drawText("SYSTEM HARDWARE REPORT", 44f, 70f, titlePaint)
        
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateString = formatter.format(Date())
        canvas.drawText("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})", 44f, 95f, subTitlePaint)
        canvas.drawText("Generated On: $dateString", 44f, 112f, subTitlePaint)

        // Draw Root Check Status Banner
        val rootBannerRect = RectF(24f, 146f, 595f - 24f, 196f)
        val rootBannerPaint = Paint().apply {
            color = if (rootResult.isRooted) Color.parseColor("#EF4444") else Color.parseColor("#10B981") // Red vs Green
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rootBannerRect, 6f, 6f, rootBannerPaint)
        
        val statusLabel = if (rootResult.isRooted) "ROOT ACCESS TRIGGERED - NOT SECURE" else "ROOT STATUS: SAFE / SECURE"
        canvas.drawText(statusLabel, 595f / 2f, 177f, badgeTextPaint)

        var currentY = 224f

        // Sections helper
        fun drawSectionHeader(title: String) {
            canvas.drawText(title, 24f, currentY, headerLabelPaint)
            val linePaint = Paint().apply {
                color = Color.parseColor("#D1D5DB")
                strokeWidth = 1f
            }
            canvas.drawLine(24f, currentY + 6f, 595f - 24f, currentY + 6f, linePaint)
            currentY += 24f
        }

        fun drawSpecRow(key: String, value: String, xStart: Float, xValue: Float) {
            canvas.drawText(key, xStart, currentY, keyPaint)
            canvas.drawText(value, xValue, currentY, valPaint)
        }

        // Section 1: CPU SPECIFICATIONS
        drawSectionHeader("1. PROCESSOR & CHIPSET")
        drawSpecRow("Cpu Model Name", cpuSpecs.model, 30f, 160f)
        currentY += 16f
        drawSpecRow("Architecture Type", cpuSpecs.architecture, 30f, 160f)
        drawSpecRow("Active Cores", "${cpuSpecs.coresCount} Logical Cores", 330f, 430f)
        currentY += 16f
        drawSpecRow("Hardware Platform", cpuSpecs.hardware, 30f, 160f)
        drawSpecRow("Manufacturer", cpuSpecs.manufacturer, 330f, 430f)
        currentY += 16f
        drawSpecRow("Instruction Sets", cpuSpecs.instructionSets.take(2).joinToString(", "), 30f, 160f)
        currentY += 34f

        // Section 2: POWER & BATTERY STATUS
        drawSectionHeader("2. POWER STATE & BATTERY HEALTH")
        drawSpecRow("Health Grade", batterySpecs.health, 30f, 160f)
        drawSpecRow("Charge Source", batterySpecs.pluggedSource, 330f, 430f)
        currentY += 16f
        drawSpecRow("Current Charge", "${batterySpecs.level}% (Scale ${batterySpecs.scale})", 30f, 160f)
        drawSpecRow("State of Charge", batterySpecs.status, 330f, 430f)
        currentY += 16f
        drawSpecRow("Voltage Rating", "${batterySpecs.voltage} mV", 30f, 160f)
        drawSpecRow("Battery Cell Temp", String.format(Locale.US, "%.1f °C", batterySpecs.temperature), 330f, 430f)
        currentY += 16f
        drawSpecRow("Anode Technology", batterySpecs.technology, 30f, 160f)
        drawSpecRow("Design Capacity", String.format(Locale.US, "%.0f mAh", batterySpecs.capacityAh), 330f, 430f)
        currentY += 34f

        // Section 3: MEMORY & STORAGE ALLOCATION
        drawSectionHeader("3. RAM & PHYSICAL PARTITIONS")
        
        // RAM Metrics
        drawSpecRow("System RAM Status", "Total: ${formatSize(ramStatus.totalBytes)} | Used: ${formatSize(ramStatus.usedBytes)}", 30f, 160f)
        currentY += 16f
        drawSpecRow("RAM Available", formatSize(ramStatus.availableBytes), 30f, 160f)
        drawSpecRow("RAM Loading Rate", String.format(Locale.US, "%.1f %%", ramStatus.utilizationPercentage), 330f, 430f)
        
        // Bar Chart RAM
        currentY += 10f
        val ramBarRectOuter = RectF(160f, currentY - 14f, 595f - 30f, currentY - 6f)
        val ramBarPaintOuter = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(ramBarRectOuter, 4f, 4f, ramBarPaintOuter)
        
        val ramFillWidth = (ramStatus.utilizationPercentage / 100f) * (ramBarRectOuter.right - ramBarRectOuter.left)
        val ramBarRectInner = RectF(ramBarRectOuter.left, ramBarRectOuter.top, ramBarRectOuter.left + ramFillWidth, ramBarRectOuter.bottom)
        val ramBarPaintInner = Paint().apply {
            color = Color.parseColor("#3B82F6") // Blue for memory
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(ramBarRectInner, 4f, 4f, ramBarPaintInner)
        currentY += 20f

        // Partition Metrics
        for (i in partitions.indices) {
            val part = partitions[i]
            val pctUsed = if (part.totalBytes > 0) (part.usedBytes.toFloat() / part.totalBytes.toFloat()) * 100f else 0f
            
            drawSpecRow(part.name, "Total: ${formatSize(part.totalBytes)} | Free: ${formatSize(part.availableBytes)}", 30f, 160f)
            currentY += 16f
            drawSpecRow("Partition Mount", part.path, 30f, 160f)
            drawSpecRow("Disk Space Used", String.format(Locale.US, "%.1f %% Used", pctUsed), 330f, 430f)

            // Bar Chart Disk
            currentY += 10f
            val diskBarRectOuter = RectF(160f, currentY - 14f, 595f - 30f, currentY - 6f)
            canvas.drawRoundRect(diskBarRectOuter, 4f, 4f, ramBarPaintOuter)
            
            val diskFillWidth = (pctUsed / 100f) * (diskBarRectOuter.right - diskBarRectOuter.left)
            val diskBarRectInner = RectF(diskBarRectOuter.left, diskBarRectOuter.top, diskBarRectOuter.left + diskFillWidth, diskBarRectOuter.bottom)
            val diskBarPaintInner = Paint().apply {
                color = Color.parseColor("#8B5CF6") // Purple for storage
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(diskBarRectInner, 4f, 4f, diskBarPaintInner)
            currentY += 20f
        }
        currentY += 14f

        // Section 4: REAL-TIME HEALTH MONITORS
        drawSectionHeader("4. THERMAL STATE MONITORS")
        drawSpecRow("Real-time SoC Temp", String.format(Locale.US, "%.1f °C (Thermal Zone Sensor Checked)", currentTemp), 30f, 160f)
        currentY += 16f
        val tempEvaluation = when {
            currentTemp > 45f -> "CRITICAL - Device running high thermal threshold."
            currentTemp > 38f -> "WARM - Intensive processes or high charging ambient."
            else -> "COOL - Balanced operations thermal range."
        }
        drawSpecRow("Thermal Health Evaluator", tempEvaluation, 30f, 160f)
        
        // Draw decorative lines / footers
        val footerY = 812f
        val footerLinePaint = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 1f
        }
        canvas.drawLine(24f, footerY - 10f, 595f - 24f, footerY - 10f, footerLinePaint)
        
        val footerTextPaint = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 9f
            isAntiAlias = true
        }
        canvas.drawText("Safety Report Signature: Verified Android Hardware API Suite", 24f, footerY + 2f, footerTextPaint)
        
        val pageNumTextPaint = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page 1 of 1", 595f - 24f, footerY + 2f, pageNumTextPaint)

        pdfDocument.finishPage(page)

        try {
            if (shareAfterExport) {
                // Generate PDF into cache for instant Sharing Intent Setup
                val outputFolder = File(context.cacheDir, "shared_pdfs")
                if (!outputFolder.exists()) {
                    outputFolder.mkdirs()
                }
                val outputFile = File(outputFolder, "Hardware_Analysis_Report_${System.currentTimeMillis()}.pdf")
                val fos = FileOutputStream(outputFile)
                pdfDocument.writeTo(fos)
                fos.close()

                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
                
                onComplete(true, "PDF spawned in cache successfully.", contentUri)
            } else {
                // Save to public Downloads directory using MediaStore (scoped storage friendly!)
                val pdfName = "Hardware_Report_${System.currentTimeMillis()}.pdf"
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri != null) {
                    val outStream: OutputStream? = resolver.openOutputStream(targetUri)
                    if (outStream != null) {
                        pdfDocument.writeTo(outStream)
                        outStream.close()
                        onComplete(true, "PDF saved to Downloads folder as $pdfName", targetUri)
                    } else {
                        onComplete(false, "Failed to stream out bytes to selected volume.", null)
                    }
                } else {
                    // Fallback to local file caching write if MediaStore inserts fail on custom system flavors
                    val backupFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), pdfName)
                    val outStream = FileOutputStream(backupFile)
                    pdfDocument.writeTo(outStream)
                    outStream.close()
                    onComplete(true, "Saved securely to alternate location: ${backupFile.name}", Uri.fromFile(backupFile))
                }
            }
        } catch (e: Exception) {
            onComplete(false, "PDF compilation exception: ${e.message}", null)
        } finally {
            pdfDocument.close()
        }
    }

    // Standard Share Sheet Launcher Intent logic
    fun launchShareSheet(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hardware Specification Report")
            putExtra(Intent.EXTRA_TEXT, "Here is the full rooted-state analysis & physical specifications report of my phone.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Hardware Analysis Report via"))
    }
}
