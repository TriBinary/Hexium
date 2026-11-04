package net.trilleo.hexium.core.judge

import net.trilleo.hexium.core.chart.SkyButton

/**
 * Judges one play of a chart from timestamped input, in song milliseconds. The mod calls this
 * every frame and the server calls it with a replay, through the same code, so both get the
 * same result. Nothing here allocates after construction.
 *
 * Results don't depend on how often [advanceTo] is called, only on the input. Time can't go
 * backwards: an input older than the current time counts as happening now. Live play goes
 * through [net.trilleo.hexium.core.replay.PlayInput] and
 * [net.trilleo.hexium.core.replay.ReplayRecorder], which records it that way too.
 *
 * All times passed in are raw song time: [judgementOffsetMs] (offset B) is subtracted from them.
 *
 * Crosshair positions are normalised screen coordinates, as in chart files, and may lie off the
 * screen. They're floats so a replay can store exactly what the judge saw.
 */
class Judge(private val chart: JudgeChart, private val judgementOffsetMs: Double = 0.0) {
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

	private val skyNext = IntArray(SkyButton.entries.size)
	private val skyJudged = Array(SkyButton.entries.size) { BooleanArray(chart.skyMs[it].size) }
	private val buttonDown = BooleanArray(SkyButton.entries.size)

	private val slideLastHeldMs = DoubleArray(chart.slideCount) { Double.NEGATIVE_INFINITY }
	private val activeSlides = IntArray(chart.slideCount)
	private var activeSlideCount = 0
	private var nextSlide = 0

	private var nextFlick = 0
	private val flickJudged = BooleanArray(chart.flickMs.size)
	/** A flick can't be given up on until a sample has passed its deadline: a swipe may still be under way. */
	private var flickSeenUntilMs = Double.NEGATIVE_INFINITY
	private var hasAim = false
	private var aimMs = 0.0
	private var aimX = 0.0
	private var aimY = 0.0

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

	/** False for hold and slide ticks: their delta is how long ago the input was held, not how well it was timed. */
	fun judgedIsPress(index: Int): Boolean = chart.tickObject.isEmpty() || judgedObject[index] < chart.tickObject[0]

	/** Input time minus target time. Negative is early. NaN if there was no input to measure. */
	fun judgedDeltaMs(index: Int): Double = judgedDelta[index]

	/** Judges everything due before [timeMs]. */
	fun advanceTo(timeMs: Double) {
		val time = timeMs - judgementOffsetMs
		if (time > now) now = time
		settleBefore(now, waitForFlicks = true)
	}

	/** An input changes what's held, so everything due before it must be judged first. */
	private fun settleForInput(timeMs: Double) {
		val time = timeMs - judgementOffsetMs
		if (time > now) now = time
		settleBefore(now, waitForFlicks = false)
	}

	/** Judges everything left, once the song is over. */
	fun finish() {
		flickSeenUntilMs = Double.POSITIVE_INFINITY
		settleBefore(Double.POSITIVE_INFINITY, waitForFlicks = false)
	}

	/** Records where the crosshair is. Call once per frame: slides and flicks are judged from these samples. */
	fun aimSample(timeMs: Double, x: Float, y: Float) {
		val time = timeMs - judgementOffsetMs
		if (time > now) now = time
		flickSeenUntilMs = now
		val sampleX = x.toDouble()
		val sampleY = y.toDouble()
		if (hasAim && now > aimMs) judgeFlicksAlong(sampleX, sampleY)
		settleBefore(now, waitForFlicks = false)
		updateSlides(sampleX, sampleY)
		hasAim = true
		aimMs = now
		aimX = sampleX
		aimY = sampleY
	}

	fun pressButton(button: SkyButton, timeMs: Double, x: Float, y: Float) {
		val b = button.ordinal
		if (buttonDown[b]) return
		settleForInput(timeMs)
		buttonDown[b] = true
		val clickX = x.toDouble()
		val clickY = y.toDouble()
		updateSlides(clickX, clickY)

		val targets = chart.skyMs[b]
		val judged = skyJudged[b]
		var i = skyNext[b]
		while (i < targets.size && targets[i] - now <= config.earlyMissMs) {
			if (!judged[i] && isInside(clickX, clickY, chart.skyX[b][i], chart.skyY[b][i], chart.skyRadius[b][i])) {
				judged[i] = true
				val delta = now - targets[i]
				record(chart.skyObject[b][i], pressJudgement(delta), delta)
				advanceSkyNext(b)
				return
			}
			i++
		}
	}

	fun releaseButton(button: SkyButton, timeMs: Double) {
		val b = button.ordinal
		if (!buttonDown[b]) return
		settleForInput(timeMs)
		buttonDown[b] = false
	}

	private fun advanceSkyNext(button: Int) {
		val judged = skyJudged[button]
		var i = skyNext[button]
		while (i < judged.size && judged[i]) i++
		skyNext[button] = i
	}

	private fun isInside(x: Double, y: Double, centreX: Double, centreY: Double, radius: Double): Boolean {
		val dx = (x - centreX) * config.screenAspect
		val dy = y - centreY
		return dx * dx + dy * dy <= radius * radius
	}

	/** Brings each slide being played up to date with a crosshair position at the current time. */
	private fun updateSlides(x: Double, y: Double) {
		while (nextSlide < chart.slideCount && chart.slideStartMs[nextSlide] - config.tickAttackMs <= now) {
			activeSlides[activeSlideCount++] = nextSlide++
		}
		if (activeSlideCount == 0) return
		val beat = chart.timing.msToBeat(now)
		var i = 0
		while (i < activeSlideCount) {
			val slide = activeSlides[i]
			if (now > chart.slideEndMs[slide]) {
				activeSlides[i] = activeSlides[--activeSlideCount]
				continue
			}
			if (buttonDown[chart.slideButton[slide]]) {
				val path = chart.slidePath[slide]
				if (isInside(x, y, path.x(beat), path.y(beat), chart.slideRadius[slide])) {
					slideLastHeldMs[slide] = now
				}
			}
			i++
		}
	}

	/** Judges flicks crossed by the swipe from the last sample to this one, in the order they're crossed. */
	private fun judgeFlicksAlong(toX: Double, toY: Double) {
		val dt = now - aimMs
		val dx = (toX - aimX) * config.screenAspect
		val dy = toY - aimY
		val length = Math.sqrt(dx * dx + dy * dy)
		if (length * 1000.0 / dt <= config.flickMinSpeed) return
		val angle = Math.toDegrees(StrictMath.atan2(dy, dx))
		while (true) {
			var crossed = -1
			var crossedAtMs = Double.POSITIVE_INFINITY
			var i = nextFlick
			while (i < flickJudged.size && chart.flickMs[i] - config.flickJusticeMs <= now) {
				if (!flickJudged[i] && directionFits(i, angle)) {
					val atMs = crossingInWindowMs(i, dx, dy, dt)
					if (atMs < crossedAtMs && atMs - chart.flickMs[i] <= config.flickJusticeMs) {
						crossed = i
						crossedAtMs = atMs
					}
				}
				i++
			}
			if (crossed < 0) return
			settleBefore(crossedAtMs, waitForFlicks = false)
			judgeFlick(crossed, crossedAtMs)
		}
	}

	private fun directionFits(flick: Int, angle: Double): Boolean {
		val dir = chart.flickDir[flick]
		if (dir.isNaN()) return true
		val difference = Math.abs(angle - dir) % 360.0
		val shortest = if (difference > 180.0) 360.0 - difference else difference
		return shortest <= config.flickDirectionToleranceDegrees
	}

	/**
	 * When the swipe crosses a flick inside its window, or infinity if it doesn't. A swipe that entered
	 * earlier and is still inside when the window opens counts as crossing at that moment.
	 */
	private fun crossingInWindowMs(flick: Int, dx: Double, dy: Double, dt: Double): Double {
		val enteredMs = crossingMs(flick, dx, dy, dt)
		val opensMs = chart.flickMs[flick] - config.flickJusticeMs
		if (enteredMs >= opensMs || enteredMs == Double.POSITIVE_INFINITY) return enteredMs
		val u = (opensMs - aimMs) / dt
		val fromX = (aimX - chart.flickX[flick]) * config.screenAspect + dx * u
		val fromY = aimY - chart.flickY[flick] + dy * u
		val radius = chart.flickRadius[flick]
		return if (fromX * fromX + fromY * fromY <= radius * radius) opensMs else Double.POSITIVE_INFINITY
	}

	/** When the swipe first enters a flick's hit area, or infinity if it doesn't. */
	private fun crossingMs(flick: Int, dx: Double, dy: Double, dt: Double): Double {
		val fromX = (aimX - chart.flickX[flick]) * config.screenAspect
		val fromY = aimY - chart.flickY[flick]
		val radius = chart.flickRadius[flick]
		val c = fromX * fromX + fromY * fromY - radius * radius
		if (c <= 0.0) return aimMs
		val a = dx * dx + dy * dy
		val b = fromX * dx + fromY * dy
		val discriminant = b * b - a * c
		if (b >= 0.0 || discriminant < 0.0) return Double.POSITIVE_INFINITY
		val u = (-b - Math.sqrt(discriminant)) / a
		return if (u <= 1.0) aimMs + u * dt else Double.POSITIVE_INFINITY
	}

	private fun judgeFlick(flick: Int, atMs: Double) {
		flickJudged[flick] = true
		val delta = atMs - chart.flickMs[flick]
		val distance = Math.abs(delta)
		val judgement = if (distance <= config.flickJusticeCriticalMs) Judgement.JUSTICE_CRITICAL else Judgement.JUSTICE
		record(chart.flickObject[flick], judgement, delta)
		advanceNextFlick()
	}

	private fun advanceNextFlick() {
		while (nextFlick < flickJudged.size && flickJudged[nextFlick]) nextFlick++
	}

	fun pressLane(lane: Int, timeMs: Double) {
		if (lane < 0 || lane >= chart.laneCount || laneDown[lane]) return
		settleForInput(timeMs)
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
		settleForInput(timeMs)
		laneDown[lane] = false
		laneLastDownMs[lane] = now
	}

	/**
	 * Judges notes nobody hit and ticks due before [requestedLimit], earliest first. At equal times,
	 * ground notes go before sky taps, sky taps before flicks, and all of them before ticks.
	 *
	 * With [waitForFlicks], it stops at the last sample once a flick's window has opened: a swipe may
	 * still be under way, and judging past it would make the order of results depend on how often
	 * [advanceTo] is called. A flick's own Miss always waits for a sample past its deadline.
	 */
	private fun settleBefore(requestedLimit: Double, waitForFlicks: Boolean) {
		while (true) {
			var limit = requestedLimit
			if (waitForFlicks && nextFlick < flickJudged.size) {
				val opens = chart.flickMs[nextFlick] - config.flickJusticeMs
				val canSettleTo = if (flickSeenUntilMs > opens) flickSeenUntilMs else opens
				if (canSettleTo < limit) limit = canSettleTo
			}
			var lane = -1
			var button = -1
			var flickIsDue = false
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
			for (i in skyNext.indices) {
				if (skyNext[i] < chart.skyMs[i].size) {
					val deadline = chart.skyMs[i][skyNext[i]] + chart.attackWindowMs
					if (deadline < dueMs) {
						dueMs = deadline
						lane = -1
						button = i
					}
				}
			}
			if (nextFlick < flickJudged.size) {
				val deadline = chart.flickMs[nextFlick] + config.flickJusticeMs
				if (deadline < dueMs && deadline < flickSeenUntilMs) {
					dueMs = deadline
					lane = -1
					button = -1
					flickIsDue = true
				}
			}
			if (nextTick < chart.tickMs.size && chart.tickMs[nextTick] < dueMs) {
				judgeTick()
			} else if (lane >= 0) {
				record(chart.pressObject[lane][nextPress[lane]], Judgement.MISS, Double.NaN)
				nextPress[lane]++
			} else if (button >= 0) {
				val next = skyNext[button]
				skyJudged[button][next] = true
				record(chart.skyObject[button][next], Judgement.MISS, Double.NaN)
				advanceSkyNext(button)
			} else if (flickIsDue) {
				flickJudged[nextFlick] = true
				record(chart.flickObject[nextFlick], Judgement.MISS, Double.NaN)
				advanceNextFlick()
			} else {
				return
			}
		}
	}

	private fun judgeTick() {
		val tick = nextTick++
		val tickMs = chart.tickMs[tick]
		val lane = chart.tickLane[tick]
		val age = when {
			lane < 0 -> tickMs - slideLastHeldMs[chart.tickSlide[tick]]
			laneDown[lane] -> 0.0
			else -> tickMs - laneLastDownMs[lane]
		}
		record(chart.tickObject[tick], tickJudgement(age), if (age == Double.POSITIVE_INFINITY) Double.NaN else 0.0 - age)
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
