package net.trilleo.hexium.core.judge

import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.BpmChange
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.MeterChange
import net.trilleo.hexium.core.chart.Note

internal fun beat(whole: Int, num: Int = 0, den: Int = 1) = Beat(whole, num, den)

/** At 60 BPM with no offset, beat N is N seconds, which keeps expected times readable. */
internal fun chartOf(vararg notes: Note, difficulty: Difficulty = Difficulty.EXPERT, bpm: Double = 60.0) = DifficultyChart(
	formatVersion = 1,
	difficulty = difficulty,
	level = 1,
	laneCount = 4,
	offsetMs = 0.0,
	bpm = listOf(BpmChange(beat(0), bpm)),
	meter = listOf(MeterChange(beat(0), 4, 4)),
	notes = notes.toList(),
)

internal fun judgeOf(vararg notes: Note, difficulty: Difficulty = Difficulty.EXPERT) =
	Judge(JudgeChart.compile(chartOf(*notes, difficulty = difficulty)))

/** What was judged, in order. */
internal fun Judge.judgements(): List<Judgement> = List(judgedCount) { judgement(it) }
