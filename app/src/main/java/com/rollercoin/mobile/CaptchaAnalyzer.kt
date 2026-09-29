package com.rollercoin.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DetectedCluster(
    var cx: Float,
    var cy: Float,
    var minX: Int,
    var minY: Int,
    var maxX: Int,
    var maxY: Int,
    val pixels: MutableList<IntArray>,
    var count: Int,
)

/**
 * Image analysis algorithm equivalent to CaptchaSolver in bot.php.
 * Matches thumb_img against captcha_img using HSV histogram intersection and color distance.
 */
object CaptchaAnalyzer {

    fun analyze(
        captchaBytes: ByteArray,
        thumbBytes: ByteArray,
        maxDots: Int = 3,
    ): List<Pair<Int, Int>> {
        return runCatching {
            val scene = BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.size) ?: return emptyList()
            val thumb = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size) ?: return emptyList()

            val scW = scene.width
            val scH = scene.height
            val (tBgR, tBgG, tBgB) = detectBackground(thumb)

            var thumbFg = extractForegroundPixels(thumb, tBgR, tBgG, tBgB, 60f)
            if (thumbFg.size < 200) {
                thumbFg = extractForegroundPixels(thumb, tBgR, tBgG, tBgB, 40f)
            }
            if (thumbFg.isEmpty()) return emptyList()

            val thumbPixRgb = thumbFg.map { intArrayOf(it[2], it[3], it[4]) }
            val thumbHist = buildHsvHistogram(thumbPixRgb)
            val thumbAvgRgb = avgRgb(thumbPixRgb)

            val (scBgR, scBgG, scBgB) = detectBackground(scene)
            val sceneFg = extractForegroundPixels(scene, scBgR, scBgG, scBgB, 38f)
            val clusters = clusterPixels(sceneFg, mergeRadius = 28f, minPixels = 80)
            if (clusters.isEmpty()) return emptyList()

            val scores = scoreBlobs(clusters, thumbHist, thumbAvgRgb, scH)
            val sortedIndices = scores.indices.sortedByDescending { scores[it] }

            val sortedScores = sortedIndices.map { scores[it] }
            val targetN = estimateTargetCount(sortedScores, maxDots)
            val selected = sortedIndices.take(targetN)

            selected.map { idx ->
                val cl = clusters[idx]
                Pair(cl.cx.roundToInt().coerceIn(0, scW), cl.cy.roundToInt().coerceIn(0, scH))
            }
        }.getOrDefault(emptyList())
    }

    private fun detectBackground(img: Bitmap): FloatArray {
        val w = img.width
        val h = img.height
        val counts = IntArray(512)
        val rSum = LongArray(512)
        val gSum = LongArray(512)
        val bSum = LongArray(512)

        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            counts[key]++
            rSum[key] += r.toLong()
            gSum[key] += g.toLong()
            bSum[key] += b.toLong()
        }

        var bestKey = 0
        var maxCount = -1
        for (i in 0 until 512) {
            if (counts[i] > maxCount) {
                maxCount = counts[i]
                bestKey = i
            }
        }

        val topCount = max(1, counts[bestKey])
        return floatArrayOf(
            rSum[bestKey].toFloat() / topCount,
            gSum[bestKey].toFloat() / topCount,
            bSum[bestKey].toFloat() / topCount,
        )
    }

    private fun extractForegroundPixels(
        img: Bitmap,
        bgR: Float,
        bgG: Float,
        bgB: Float,
        threshold: Float = 40f,
    ): List<IntArray> {
        val w = img.width
        val h = img.height
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = mutableListOf<IntArray>()
        val threshSq = threshold * threshold

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val color = pixels[rowOffset + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val dr = r - bgR
                val dg = g - bgG
                val db = b - bgB
                if (dr * dr + dg * dg + db * db > threshSq) {
                    result.add(intArrayOf(x, y, r, g, b))
                }
            }
        }
        return result
    }

    private fun clusterPixels(
        fgPixels: List<IntArray>,
        mergeRadius: Float = 28f,
        minPixels: Int = 80,
    ): List<DetectedCluster> {
        val clusters = mutableListOf<DetectedCluster>()
        val radiusSq = mergeRadius * mergeRadius

        for (pt in fgPixels) {
            val px = pt[0]
            val py = pt[1]
            val pr = pt[2]
            val pg = pt[3]
            val pb = pt[4]
            var merged = false

            for (cl in clusters) {
                val dx = px - cl.cx
                val dy = py - cl.cy
                if (dx * dx + dy * dy < radiusSq) {
                    val n = cl.count
                    cl.cx = (cl.cx * n + px) / (n + 1)
                    cl.cy = (cl.cy * n + py) / (n + 1)
                    cl.minX = min(cl.minX, px)
                    cl.minY = min(cl.minY, py)
                    cl.maxX = max(cl.maxX, px)
                    cl.maxY = max(cl.maxY, py)
                    cl.pixels.add(intArrayOf(pr, pg, pb))
                    cl.count++
                    merged = true
                    break
                }
            }

            if (!merged) {
                clusters.add(
                    DetectedCluster(
                        cx = px.toFloat(),
                        cy = py.toFloat(),
                        minX = px,
                        minY = py,
                        maxX = px,
                        maxY = py,
                        pixels = mutableListOf(intArrayOf(pr, pg, pb)),
                        count = 1,
                    ),
                )
            }
        }

        return clusters.filter { it.count >= minPixels }
    }

    private fun scoreBlobs(
        clusters: List<DetectedCluster>,
        thumbHist: FloatArray,
        thumbAvgRgb: FloatArray,
        sceneH: Int,
    ): FloatArray {
        val avgR = thumbAvgRgb[0]
        val avgG = thumbAvgRgb[1]
        val avgB = thumbAvgRgb[2]
        val scores = FloatArray(clusters.size)

        for (i in clusters.indices) {
            val cl = clusters[i]
            val blobW = cl.maxX - cl.minX + 1
            val blobH = cl.maxY - cl.minY + 1
            val minDim = min(blobW, blobH)
            val aspect = if (blobH > 0) blobW.toFloat() / blobH else 0f
            val szPenalty = when {
                minDim < 20 -> 0.15f
                minDim < 30 -> 0.50f
                blobW > 110 || blobH > 110 -> 0.70f
                else -> 1.0f
            }
            val aspPenalty = if (aspect in 0.3f..3.0f) 1.0f else 0.75f
            val edgePenalty = if (cl.cy > sceneH - 25) 0.2f else 1.0f

            val bAvgRgb = avgRgb(cl.pixels)
            val dr = avgR - bAvgRgb[0]
            val dg = avgG - bAvgRgb[1]
            val db = avgB - bAvgRgb[2]
            val colorDist = sqrt(dr * dr + dg * dg + db * db)
            val colorBonus = 1.0f / (1.0f + colorDist / 50.0f)

            val clHist = buildHsvHistogram(cl.pixels)
            val sim = histogramIntersection(thumbHist, clHist)
            scores[i] = sim * szPenalty * aspPenalty * edgePenalty * colorBonus
        }

        return scores
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = max(rf, max(gf, bf))
        val min = min(rf, min(gf, bf))
        val delta = max - min
        val v = max
        val s = if (max == 0f) 0f else delta / max
        var h = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        if (h < 0f) h += 360f
        return floatArrayOf(h, s, v)
    }

    private fun buildHsvHistogram(pixels: List<IntArray>): FloatArray {
        val hBins = 12
        val sBins = 4
        val vBins = 4
        val totalBins = hBins * sBins * vBins
        val hist = FloatArray(totalBins)

        for (p in pixels) {
            val (h, s, v) = rgbToHsv(p[0], p[1], p[2])
            val hi = min((h / 360f * hBins).toInt(), hBins - 1)
            val si = min((s * sBins).toInt(), sBins - 1)
            val vi = min((v * vBins).toInt(), vBins - 1)
            val index = hi * sBins * vBins + si * vBins + vi
            hist[index] += 1f
        }

        var sum = 0f
        for (v in hist) sum += v
        if (sum > 0f) {
            for (i in hist.indices) hist[i] /= sum
        }
        return hist
    }

    private fun histogramIntersection(h1: FloatArray, h2: FloatArray): Float {
        var sum = 0f
        val size = min(h1.size, h2.size)
        for (i in 0 until size) {
            sum += min(h1[i], h2[i])
        }
        return sum
    }

    private fun avgRgb(pixels: List<IntArray>): FloatArray {
        val n = max(1, pixels.size).toFloat()
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        for (p in pixels) {
            rSum += p[0]
            gSum += p[1]
            bSum += p[2]
        }
        return floatArrayOf(rSum / n, gSum / n, bSum / n)
    }

    private fun estimateTargetCount(sortedScores: List<Float>, maxDots: Int): Int {
        val n = sortedScores.size
        if (n <= 1) return 1
        var maxGap = 0f
        var gapIndex = 1
        val limit = min(maxDots - 1, n - 1)
        for (i in 0 until limit) {
            val gap = sortedScores[i] - sortedScores[i + 1]
            if (gap > maxGap) {
                maxGap = gap
                gapIndex = i + 1
            }
        }
        return if (maxGap < 0.05f) min(2, maxDots) else gapIndex
    }
}
