package net.trilleo.hexium.core.replay

import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.ChartValidator
import net.trilleo.hexium.core.chart.Severity
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.JudgementConfig

/** [judge] holds the finished play, and is null when [error] says why it couldn't be re-judged. */
class RejudgeResult internal constructor(val judge: Judge?, val error: String?)

/** Runs a replay through the same judge as live play. */
object ReplayJudge {
	private val BUTTONS = SkyButton.entries.toTypedArray()

	fun play(chart: JudgeChart, replay: Replay): Judge {
		val judge = Judge(chart, replay.judgementOffsetMs)
		for (i in 0 until replay.eventCount) {
			val time = replay.timeMs(i)
			when (replay.event(i)) {
				ReplayEvent.AIM -> judge.aimSample(time, replay.x(i), replay.y(i))
				ReplayEvent.LANE_DOWN -> judge.pressLane(replay.index(i), time)
				ReplayEvent.LANE_UP -> judge.releaseLane(replay.index(i), time)
				ReplayEvent.BUTTON_DOWN -> judge.pressButton(BUTTONS[replay.index(i)], time, replay.x(i), replay.y(i))
				ReplayEvent.BUTTON_UP -> judge.releaseButton(BUTTONS[replay.index(i)], time)
			}
		}
		judge.finish()
		return judge
	}

	/** Re-judges from the difficulty file's bytes, checking the replay belongs to it. Never throws on bad input. */
	fun rejudge(chartFile: ByteArray, replay: Replay): RejudgeResult {
		if (!Replay.hashOf(chartFile).contentEquals(replay.chartHash)) return failed("This replay is of a different chart")
		if (replay.judgeVersion != JudgementConfig.JUDGE_VERSION) {
			return failed("This replay was played under judge version ${replay.judgeVersion}, this is ${JudgementConfig.JUDGE_VERSION}")
		}
		val parsed = ChartParser.parseDifficulty(String(chartFile, Charsets.UTF_8))
		val chart = parsed.value ?: return failed("The chart can't be read: ${parsed.issues.first { it.severity == Severity.ERROR }}")
		val problem = ChartValidator.validateChart(chart).firstOrNull { it.severity == Severity.ERROR }
		if (problem != null) return failed("The chart isn't valid: $problem")
		return RejudgeResult(play(JudgeChart.compile(chart), replay), null)
	}

	private fun failed(why: String) = RejudgeResult(null, why)
}
