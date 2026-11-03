package net.trilleo.hexium.core.judge

/**
 * Judges one play of a chart from timestamped input, in song milliseconds. The mod calls this
 * every frame and the server calls it with a replay, through the same code, so both get the
 * same result. Nothing here allocates after construction.
 *
 * Results don't depend on how often [advanceTo] is called, only on the input. Time can't go
 * backwards: an input older than the current time counts as happening now.
 */
class Judge(private val chart: JudgeChart) {
	private val config = chart.config
	private val weights = intArrayOf(
		config.justiceCriticalWeightPercent,
		config.justiceWeightPercent,
		config.attackWeightPercent,
		config.missWeightPercent,
	)

	private var now = Double.NEGATIVE_INFINITY

	private val nextPress = IntArray(chart.laneCount)
	private val laneDown = BooleanArray(chart.laneCount)
	private val laneLastDownMs = DoubleArray(chart.laneCount) { Double.NEGATIVE_INFINITY }
	private var nextTick = 0

	private val judgedObject = IntArray(chart.objectCount)
	private val judgedResult = Array(chart.objectCount) { Judgement.MISS }
	private val judgedDelta = DoubleArray(chart.objectCount)
	private val counts = IntArray(weights.size)
	private var weightSum = 0L

	val objectCount: Int
		get() = chart.objectCount

	/** How many objects have been judged. Results stay in this order, so a reader can keep its own position. */
	var judgedCount = 0
		private set
	var combo = 0
		private set
	var maxCombo = 0
		private set

	val score: Int
		get() {
			if (chart.objectCount == 0) return 0
			return (config.perfectScoreBase * weightSum / (100L * chart.objectCount)).toInt()
		}

	/** [ClearLamp.NONE] until every object is judged. */
	val clearLamp: ClearLamp
		get() {
			if (chart.objectCount == 0 || judgedCount < chart.objectCount) return ClearLamp.NONE
			if (count(Judgement.MISS) > 0) return ClearLamp.NONE
			if (count(Judgement.ATTACK) > 0) return ClearLamp.FULL_COMBO
			if (count(Judgement.JUSTICE) > 0) return ClearLamp.ALL_JUSTICE
			return ClearLamp.ALL_JUSTICE_CRITICAL
		}

	fun count(judgement: Judgement): Int = counts[judgement.ordinal]

	fun judgement(index: Int): Judgement = judgedResult[index]

	/** The judged object's index in the chart's notes. */
	fun judgedNote(index: Int): Int = chart.objectNote[judgedObject[index]]

	/** Input time minus target time. Negative is early. NaN if there was no input to measure. */
	fun judgedDeltaMs(index: Int): Double = judgedDelta[index]

	/** Judges everything due before [timeMs]. */
	fun advanceTo(timeMs: Double) {
		if (timeMs > now) now = timeMs
		settleBefore(now)
	}

	/** Judges everything left, once the song is over. */
	fun finish() {
		settleBefore(Double.POSITIVE_INFINITY)
	}

	fun pressLane(lane: Int, timeMs: Double) {
		if (lane < 0 || lane >= chart.laneCount || laneDown[lane]) return
		advanceTo(timeMs)
		laneDown[lane] = true
		laneLastDownMs[lane] = now
		val next = nextPress[lane]
		val targets = chart.pressMs[lane]
		if (next >= targets.size) return
		val delta = now - targets[next]
		if (delta < -config.earlyMissMs) return
		nextPress[lane] = next + 1
		record(chart.pressObject[lane][next], pressJudgement(delta), delta)
	}

	fun releaseLane(lane: Int, timeMs: Double) {
		if (lane < 0 || lane >= chart.laneCount || !laneDown[lane]) return
		advanceTo(timeMs)
		laneDown[lane] = false
		laneLastDownMs[lane] = now
	}

	/** Judges unpressed notes and ticks due before [limit], earliest first. At equal times, notes go before ticks. */
	private fun settleBefore(limit: Double) {
		while (true) {
			var lane = -1
			var dueMs = limit
			for (i in nextPress.indices) {
				if (nextPress[i] < chart.pressMs[i].size) {
					val deadline = chart.pressMs[i][nextPress[i]] + chart.attackWindowMs
					if (deadline < dueMs) {
						dueMs = deadline
						lane = i
					}
				}
			}
			val tickIsDue = nextTick < chart.tickMs.size && chart.tickMs[nextTick] < dueMs
			if (tickIsDue) {
				judgeTick()
			} else if (lane >= 0) {
				record(chart.pressObject[lane][nextPress[lane]], Judgement.MISS, Double.NaN)
				nextPress[lane]++
			} else {
				return
			}
		}
	}

	private fun judgeTick() {
		val tick = nextTick++
		val lane = chart.tickLane[tick]
		val tickMs = chart.tickMs[tick]
		val age = if (laneDown[lane]) 0.0 else tickMs - laneLastDownMs[lane]
		record(chart.tickObject[tick], tickJudgement(age), -age)
	}

	private fun pressJudgement(delta: Double): Judgement {
		val distance = if (delta < 0.0) -delta else delta
		return when {
			distance <= config.justiceCriticalMs -> Judgement.JUSTICE_CRITICAL
			distance <= config.justiceMs -> Judgement.JUSTICE
			distance <= chart.attackWindowMs -> Judgement.ATTACK
			else -> Judgement.MISS
		}
	}

	private fun tickJudgement(age: Double): Judgement = when {
		age <= config.tickJusticeCriticalMs -> Judgement.JUSTICE_CRITICAL
		age <= config.tickJusticeMs -> Judgement.JUSTICE
		age <= config.tickAttackMs -> Judgement.ATTACK
		else -> Judgement.MISS
	}

	private fun record(obj: Int, judgement: Judgement, deltaMs: Double) {
		val index = judgedCount++
		judgedObject[index] = obj
		judgedResult[index] = judgement
		judgedDelta[index] = deltaMs
		counts[judgement.ordinal]++
		weightSum += weights[judgement.ordinal]
		combo = if (judgement == Judgement.MISS) 0 else combo + 1
		if (combo > maxCombo) maxCombo = combo
	}
}
