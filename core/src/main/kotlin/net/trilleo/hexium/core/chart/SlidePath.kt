package net.trilleo.hexium.core.chart

/**
 * Where a slide is at any beat, shared by the judge and the renderer. Lookups don't allocate.
 * Before the first point and after the last, the position stays on that point. Expects points
 * that passed the validator.
 */
class SlidePath(points: List<SlidePoint>) {
	private val beats = DoubleArray(points.size) { points[it].t.toDouble() }
	private val xs = DoubleArray(points.size) { points[it].x }
	private val ys = DoubleArray(points.size) { points[it].y }
	private val eases = Array(points.size) { points[it].ease }

	fun x(beat: Double): Double = position(xs, beat)

	fun y(beat: Double): Double = position(ys, beat)

	private fun position(values: DoubleArray, beat: Double): Double {
		if (beat <= beats[0]) return values[0]
		val last = beats.size - 1
		if (beat >= beats[last]) return values[last]
		var low = 0
		var high = last - 1
		while (low < high) {
			val middle = (low + high + 1) ushr 1
			if (beats[middle] <= beat) low = middle else high = middle - 1
		}
		val t = (beat - beats[low]) / (beats[low + 1] - beats[low])
		return values[low] + (values[low + 1] - values[low]) * eases[low].progress(t)
	}
}
