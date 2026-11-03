package net.trilleo.hexium.core.chart

/**
 * A time in beats as an exact fraction, stored as `[whole, num, den]` in chart files.
 *
 * It can hold invalid values so the validator can report them. [compareTo] and [toDouble]
 * only mean something when [isValid] is true.
 *
 * `equals` compares the fields as written, so `[12,2,4]` and `[12,1,2]` aren't equal but
 * do compare as the same time. Use [compareTo] for time.
 */
data class Beat(val whole: Int, val num: Int, val den: Int) : Comparable<Beat> {
	val isValid: Boolean
		get() = whole >= 0 && den > 0 && num >= 0 && num < den

	override fun compareTo(other: Beat): Int {
		if (whole != other.whole) return whole.compareTo(other.whole)
		return (num.toLong() * other.den).compareTo(other.num.toLong() * den)
	}

	fun toDouble(): Double = whole + num.toDouble() / den
}
