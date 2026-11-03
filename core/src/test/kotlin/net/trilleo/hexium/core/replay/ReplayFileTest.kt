package net.trilleo.hexium.core.replay

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.SkyButton

class ReplayFileTest {
	private val allKinds = listOf(
		aimEvent(0, 0, 1.5, 0.25f, -0.5f),
		event(1, 3, 2.0),
		event(2, 3, 2.0),
		aimEvent(3, 1, 3.0, 1.5f, 0.75f),
		event(4, 1, 4.0),
	)

	private fun assertRejected(bytes: ByteArray, expectedInMessage: String) {
		val result = ReplayFile.read(bytes)
		assertNull(result.replay)
		val error = assertNotNull(result.error)
		assertTrue(expectedInMessage in error, error)
	}

	private fun read(bytes: ByteArray): Replay {
		val result = ReplayFile.read(gzip(bytes))
		assertEquals(null, result.error)
		return assertNotNull(result.replay)
	}

	@Test
	fun readsEveryFieldFromAHandBuiltFile() {
		val replay = read(fileBytes(judgeVersion = 7, offsetMs = -3.25, events = allKinds))
		assertEquals(7, replay.judgeVersion)
		assertContentEquals(HASH, replay.chartHash)
		assertEquals(-3.25, replay.judgementOffsetMs)
		assertEquals(5, replay.eventCount)
		assertEquals(
			listOf(ReplayEvent.AIM, ReplayEvent.LANE_DOWN, ReplayEvent.LANE_UP, ReplayEvent.BUTTON_DOWN, ReplayEvent.BUTTON_UP),
			List(5) { replay.event(it) },
		)
		assertEquals(listOf(0, 3, 3, 1, 1), List(5) { replay.index(it) })
		assertEquals(listOf(1.5, 2.0, 2.0, 3.0, 4.0), List(5) { replay.timeMs(it) })
		assertEquals(0.25f, replay.x(0))
		assertEquals(-0.5f, replay.y(0))
		assertEquals(1.5f, replay.x(3))
		assertEquals(0.75f, replay.y(3))
	}

	@Test
	fun writesExactlyTheDocumentedBytes() {
		val written = ReplayFile.write(read(fileBytes(events = allKinds)))
		val unpacked = GZIPInputStream(ByteArrayInputStream(written)).readAllBytes()
		assertContentEquals(fileBytes(events = allKinds), unpacked)
	}

	@Test
	fun writingIsStable() {
		val replay = read(fileBytes(events = allKinds))
		assertContentEquals(ReplayFile.write(replay), ReplayFile.write(replay))
	}

	@Test
	fun emptyReplayIsValid() {
		assertEquals(0, read(fileBytes()).eventCount)
	}

	@Test
	fun numbersKeepTheirExactBits() {
		val time = Math.nextUp(1234.5678)
		val x = Math.nextUp(0.3f)
		val once = read(fileBytes(events = listOf(aimEvent(0, 0, time, x, 0f))))
		val replay = assertNotNull(ReplayFile.read(ReplayFile.write(once)).replay)
		assertEquals(time, replay.timeMs(0))
		assertEquals(x, replay.x(0))
	}

	@Test
	fun notGzipIsRejected() {
		assertRejected(byteArrayOf(1, 2, 3, 4), "compressed")
		assertRejected(ByteArray(0), "compressed")
	}

	@Test
	fun truncatedGzipIsRejected() {
		val bytes = gzip(fileBytes(events = allKinds))
		assertRejected(bytes.copyOf(bytes.size - 12), "compressed")
	}

	@Test
	fun wrongMagicIsRejected() {
		assertRejected(gzip(fileBytes(magic = "HXRQ")), "not a Hexium replay")
	}

	@Test
	fun unknownFormatVersionIsRejected() {
		assertRejected(gzip(fileBytes(formatVersion = 2)), "format version 2")
	}

	@Test
	fun everyTruncationIsRejected() {
		val whole = fileBytes(events = allKinds)
		for (length in 0 until whole.size) {
			val result = ReplayFile.read(gzip(whole.copyOf(length)))
			assertNull(result.replay, "cut to $length bytes")
			assertNotNull(result.error)
		}
	}

	@Test
	fun bytesAfterTheLastEventAreRejected() {
		assertRejected(gzip(fileBytes(events = allKinds) + 0), "after the last event")
		assertRejected(gzip(fileBytes(eventCount = 4, events = allKinds)), "after the last event")
	}

	@Test
	fun unknownEventKindIsRejected() {
		assertRejected(gzip(fileBytes(events = listOf(event(5, 0, 1.0)))), "kind 5")
	}

	@Test
	fun badIndexIsRejected() {
		assertRejected(gzip(fileBytes(events = listOf(aimEvent(3, 2, 1.0, 0f, 0f)))), "button")
		assertRejected(gzip(fileBytes(events = listOf(event(4, 2, 1.0)))), "button")
		assertRejected(gzip(fileBytes(events = listOf(aimEvent(0, 1, 1.0, 0f, 0f)))), "index")
	}

	@Test
	fun timesGoingDownAreRejected() {
		assertRejected(gzip(fileBytes(events = listOf(event(1, 0, 2.0), event(2, 0, 1.0)))), "event 1")
		assertEquals(2, read(fileBytes(events = listOf(event(1, 0, 2.0), event(2, 0, 2.0)))).eventCount)
	}

	@Test
	fun numbersThatAreNotFiniteAreRejected() {
		assertRejected(gzip(fileBytes(events = listOf(event(1, 0, Double.NaN)))), "event 0")
		assertRejected(gzip(fileBytes(events = listOf(event(1, 0, Double.POSITIVE_INFINITY)))), "event 0")
		assertRejected(gzip(fileBytes(events = listOf(aimEvent(0, 0, 1.0, Float.NaN, 0f)))), "event 0")
		assertRejected(gzip(fileBytes(events = listOf(aimEvent(3, 0, 1.0, 0f, Float.POSITIVE_INFINITY)))), "event 0")
		assertRejected(gzip(fileBytes(offsetMs = Double.NaN)), "offset")
	}

	@Test
	fun aHugeClaimedEventCountIsRejectedWithoutAllocatingForIt() {
		assertRejected(gzip(fileBytes(eventCount = Int.MAX_VALUE)), "events")
		assertRejected(gzip(fileBytes(eventCount = -1)), "events")
		assertRejected(gzip(fileBytes(eventCount = Replay.MAX_EVENTS + 1)), "events")
		assertRejected(gzip(fileBytes(eventCount = Replay.MAX_EVENTS)), "cut short")
	}

	@Test
	fun aSmallFileThatUnpacksIntoSomethingHugeIsRejected() {
		val bomb = gzip(ByteArray(200 * 1024 * 1024))
		assertTrue(bomb.size < 1024 * 1024)
		assertRejected(bomb, "too large")
	}

	@Test
	fun buttonIndexesMatchSkyButton() {
		assertEquals(0, SkyButton.L.ordinal)
		assertEquals(1, SkyButton.R.ordinal)
	}
}
