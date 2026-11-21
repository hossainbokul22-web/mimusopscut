package io.github.artcraftandroid.capcraft.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Finds where a video cuts from one shot to the next, by comparing small greyscale copies of frames
 * a tenth of a second apart; a jump much larger than the video's usual change is a cut. It reads the
 * rhythm only (how long each shot lasts), nothing else of the video. On the phone; blocking, so call
 * from a worker. A rough tool: a fast pan or a flash can read as a cut, and a soft dissolve may not.
 */
object CutDetect {
    private const val STEP_US = 100_000L
    private const val MAX_US = 60_000_000L
    private const val GRID = 24

    /** The lengths in seconds of the shots of [uri], in order (at most [limit]); empty if none could be read. */
    fun shotLengths(context: Context, uri: Uri, limit: Int = 40, progress: (Float) -> Unit = {}): List<Double> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val total = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            val end = minOf(total, MAX_US)
            if (end < 1_000_000L) return emptyList()
            var previous: IntArray? = null
            val diffs = ArrayList<Pair<Long, Double>>()
            var at = 0L
            while (at < end) {
                val frame = r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST, GRID, GRID)
                if (frame != null) {
                    val px = IntArray(GRID * GRID)
                    frame.getPixels(px, 0, GRID, 0, 0, GRID, GRID)
                    frame.recycle()
                    val grey = IntArray(px.size) { i ->
                        val c = px[i]
                        (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10
                    }
                    previous?.let { p -> diffs.add(at to grey.indices.sumOf { abs(grey[it] - p[it]) }.toDouble() / grey.size / 255.0) }
                    previous = grey
                }
                at += STEP_US
                progress((at.toFloat() / end).coerceIn(0f, 1f))
            }
            if (diffs.size < 5) return emptyList()
            val mean = diffs.sumOf { it.second } / diffs.size
            val sd = sqrt(diffs.sumOf { (it.second - mean) * (it.second - mean) } / diffs.size)
            val threshold = maxOf(0.09, mean + 3.0 * sd)
            val cuts = ArrayList<Long>()
            diffs.forEachIndexed { i, (time, d) ->
                val before = diffs.getOrNull(i - 1)?.second ?: 0.0
                val after = diffs.getOrNull(i + 1)?.second ?: 0.0
                // a peak over the threshold, and not within a third of a second of the last cut
                if (d > threshold && d >= before && d >= after && (cuts.isEmpty() || time - cuts.last() >= 300_000L)) cuts.add(time)
            }
            val edges = listOf(0L) + cuts + listOf(end)
            return edges.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.filter { it >= 0.3 }.take(limit)
        } finally {
            runCatching { r.release() }
        }
    }
}
