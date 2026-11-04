package net.trilleo.hexium.client.audio

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.trilleo.hexium.Hexium
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.SOFTSourceLatency
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryUtil
import org.slf4j.LoggerFactory

/**
 * Plays one OGG file through its own OpenAL source on the game's OpenAL context, never through
 * Minecraft's sound manager. A background thread decodes and queues the audio, so a frame only ever
 * asks for the position.
 *
 * Minecraft destroys its context when the audio device changes or sounds reload, which takes this
 * source with it. [onContextLost] stops using it, and [recoverIfLost] rebuilds it on the new context.
 */
class SongStream private constructor(
	private val ogg: ByteBuffer,
	private val decoder: Long,
	private val sampleRate: Int,
	private val channels: Int,
	val lengthMs: Double,
) : AutoCloseable {
	/** Held for the whole of a refill, so a seek can't mix old audio into the queue. Taken before [lock]. */
	private val decoderLock = ReentrantLock()

	/** Guards the source and its queue. The pump never holds it during a decode, so a frame doesn't wait on one. */
	private val lock = ReentrantLock()

	private val format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
	private val pcm: ShortBuffer = MemoryUtil.memAllocShort(BUFFER_FRAMES * channels)
	private val buffers = IntArray(BUFFER_COUNT)
	private val free = IntArray(BUFFER_COUNT)
	private var freeCount = 0
	private val oneBuffer = IntArray(1)

	/** How many frames each queued buffer holds, oldest first, as a ring. */
	private val queuedFrames = IntArray(BUFFER_COUNT)
	private var queuedHead = 0
	private var queuedCount = 0

	/** Frames before the start of the queue. AL's own position only counts from there. */
	private var baseFrames = 0L
	private val offsetAndLatency = DoubleArray(2)
	private val oneInt = IntArray(1)
	private var hasLatencyExtension = false

	/** 0 while there's no context to use. */
	@Volatile
	private var context = 0L
	private var source = 0
	private var gain = 1f
	private var playing = false
	private var ranDry = false
	private var recoveryFailed = false
	private var pumpFailed = false

	@Volatile
	private var decoderFinished = false
	private var lastPositionMs = 0.0

	@Volatile
	private var closed = false
	private val thread = Thread(::pumpUntilClosed, "Hexium song stream")

	/** How many times the queue ran dry, so the music stopped until it was refilled. */
	@Volatile
	var underruns = 0
		private set

	/** True if the sound card's own latency is taken off the position. */
	val usesLatency: Boolean
		get() = hasLatencyExtension

	/** True once the last sample has been played. */
	val finished: Boolean
		get() = lock.withLock { decoderFinished && queuedCount == 0 }

	init {
		try {
			createSource(ALC10.alcGetCurrentContext())
		} catch (e: Exception) {
			MemoryUtil.memFree(pcm)
			throw e
		}
		thread.isDaemon = true
		thread.start()
	}

	private fun createSource(onContext: Long) {
		hasLatencyExtension = AL.getCapabilities().AL_SOFT_source_latency
		source = AL10.alGenSources()
		if (!AL10.alIsSource(source)) throw IOException("OpenAL has no free source")
		AL10.alGenBuffers(buffers)
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
		AL10.alSourcei(source, AL11.AL_DISTANCE_MODEL, AL10.AL_NONE)
		AL10.alSourcef(source, AL10.AL_GAIN, gain)
		resetQueue()
		context = onContext
	}

	private fun resetQueue() {
		for (i in buffers.indices) free[i] = buffers[i]
		freeCount = buffers.size
		queuedHead = 0
		queuedCount = 0
		ranDry = false
	}

	/** Starts or resumes from the current position. */
	fun play() {
		lock.withLock { playing = true }
		fill()
		lock.withLock {
			if (contextIsCurrent() && sourceState() != AL10.AL_PLAYING) AL10.alSourcePlay(source)
		}
	}

	fun pause() {
		lock.withLock {
			playing = false
			if (contextIsCurrent()) AL10.alSourcePause(source)
		}
	}

	/** Moves to [ms], keeping whether it's playing. */
	fun seek(ms: Double) {
		decoderLock.withLock {
			lock.withLock {
				if (contextIsCurrent()) {
					// Rewind, not stop: buffers queued on a stopped source count as played straight away.
					AL10.alSourceRewind(source)
					AL10.alSourcei(source, AL10.AL_BUFFER, AL10.AL_NONE)
				}
				resetQueue()
				moveDecoderTo(ms)
			}
		}
		if (playing) play() else fill()
	}

	private fun moveDecoderTo(ms: Double) {
		val frame = (ms.coerceAtLeast(0.0) / 1000.0 * sampleRate).toLong().coerceAtMost(Int.MAX_VALUE.toLong())
		if (!STBVorbis.stb_vorbis_seek(decoder, frame.toInt())) LOGGER.warn("Can't seek the song to frame {}", frame)
		baseFrames = frame
		decoderFinished = false
		lastPositionMs = frame * 1000.0 / sampleRate
	}

	fun setGain(value: Float) {
		if (value == gain) return
		lock.withLock {
			gain = value
			if (contextIsCurrent()) AL10.alSourcef(source, AL10.AL_GAIN, value)
		}
	}

	/** Where the listener is in the song, or NaN if the music isn't sounding right now. Doesn't allocate. */
	fun positionMs(): Double {
		lock.lock()
		try {
			if (!contextIsCurrent() || sourceState() != AL10.AL_PLAYING) return Double.NaN
			val seconds = if (hasLatencyExtension) {
				SOFTSourceLatency.alGetSourcedvSOFT(source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, offsetAndLatency)
				offsetAndLatency[0] - offsetAndLatency[1]
			} else {
				AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET, oneInt)
				oneInt[0].toDouble() / sampleRate
			}
			lastPositionMs = baseFrames * 1000.0 / sampleRate + seconds * 1000.0
			return lastPositionMs
		} finally {
			lock.unlock()
		}
	}

	private fun sourceState(): Int {
		AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE, oneInt)
		return oneInt[0]
	}

	/** Minecraft is about to destroy the context. Called from a mixin, before it does. */
	private fun contextLost() {
		decoderLock.withLock {
			lock.withLock { context = 0L }
		}
	}

	/**
	 * Call once per frame. If the source was lost, rebuilds it at [songMs] once Minecraft has a new
	 * context. Doesn't allocate unless that happens.
	 */
	fun recoverIfLost(songMs: Double) {
		if (context != 0L) return
		val current = ALC10.alcGetCurrentContext()
		if (current == 0L) return
		try {
			decoderLock.withLock {
				lock.withLock {
					createSource(current)
					moveDecoderTo(songMs)
				}
			}
		} catch (e: IOException) {
			// Tried again next frame. Sounds playing in the world may free up a source.
			if (!recoveryFailed) LOGGER.warn("Can't restart the song", e)
			recoveryFailed = true
			return
		}
		recoveryFailed = false
		if (playing) play() else fill()
	}

	private fun contextIsCurrent(): Boolean = context != 0L && ALC10.alcGetCurrentContext() == context

	private fun pumpUntilClosed() {
		while (!closed) {
			try {
				fill()
			} catch (e: RuntimeException) {
				if (!pumpFailed) LOGGER.warn("The song stream failed", e)
				pumpFailed = true
			}
			LockSupport.parkNanos(PUMP_INTERVAL_NS)
		}
	}

	/** Takes back the buffers that have played and refills them. */
	private fun fill() {
		decoderLock.withLock {
			if (closed || !contextIsCurrent()) return
			lock.withLock {
				// OpenAL can report buffers as played when they haven't been: on a source that hasn't started,
				// and on one that stopped and got more. So only ask once it has started, and not while paused.
				val state = sourceState()
				if (!playing && state != AL10.AL_INITIAL) return
				if (state != AL10.AL_INITIAL) reclaim()
			}
			while (freeCount > 0 && !decoderFinished) {
				pcm.clear()
				val frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm)
				if (frames == 0) {
					decoderFinished = true
					break
				}
				pcm.limit(frames * channels)
				lock.withLock { queue(frames) }
			}
			lock.withLock { restartAfterRunningDry() }
		}
	}

	private fun reclaim() {
		AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED, oneInt)
		val processed = oneInt[0]
		for (i in 0 until processed) {
			AL10.alSourceUnqueueBuffers(source, oneBuffer)
			free[freeCount++] = oneBuffer[0]
			baseFrames += queuedFrames[queuedHead]
			queuedHead = (queuedHead + 1) % BUFFER_COUNT
			queuedCount--
		}
	}

	private fun queue(frames: Int) {
		// Buffers queued on a stopped source count as played straight away, so it must be rewound first.
		if (sourceState() == AL10.AL_STOPPED) {
			reclaim()
			AL10.alSourceRewind(source)
			ranDry = true
		}
		val buffer = free[--freeCount]
		AL10.alBufferData(buffer, format, pcm, sampleRate)
		oneBuffer[0] = buffer
		AL10.alSourceQueueBuffers(source, oneBuffer)
		queuedFrames[(queuedHead + queuedCount) % BUFFER_COUNT] = frames
		queuedCount++
	}

	private fun restartAfterRunningDry() {
		if (!ranDry || !playing || queuedCount == 0) return
		ranDry = false
		underruns++
		AL10.alSourcePlay(source)
	}

	override fun close() {
		if (closed) return
		closed = true
		thread.join(CLOSE_WAIT_MS)
		decoderLock.withLock {
			lock.withLock {
				if (contextIsCurrent()) {
					AL10.alSourceStop(source)
					AL10.alDeleteSources(source)
					AL10.alDeleteBuffers(buffers)
				}
				STBVorbis.stb_vorbis_close(decoder)
				MemoryUtil.memFree(pcm)
				MemoryUtil.memFree(ogg)
			}
		}
		if (current === this) current = null
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)

		/** A queue of about 0.75 seconds at 44.1 kHz: long enough to survive a stall, quick to refill. */
		private const val BUFFER_FRAMES = 8192
		private const val BUFFER_COUNT = 4
		private const val PUMP_INTERVAL_NS = 10_000_000L
		private const val CLOSE_WAIT_MS = 1000L

		/** The open stream. There's only ever one. */
		@Volatile
		private var current: SongStream? = null

		/** Minecraft is about to destroy its OpenAL context. */
		@JvmStatic
		fun onContextLost() {
			current?.contextLost()
		}

		/** Opens [path], which may be inside the mod's jar. Throws [IOException] with a message for the player's log. */
		fun open(path: Path): SongStream {
			if (ALC10.alcGetCurrentContext() == 0L) throw IOException("there's no audio device")
			val bytes = Files.readAllBytes(path)
			val ogg = MemoryUtil.memAlloc(bytes.size)
			ogg.put(bytes).flip()
			val error = IntArray(1)
			val decoder = STBVorbis.stb_vorbis_open_memory(ogg, error, null)
			if (decoder == 0L) {
				MemoryUtil.memFree(ogg)
				throw IOException("$path isn't an OGG Vorbis file (stb_vorbis error ${error[0]})")
			}
			try {
				STBVorbisInfo.malloc().use { info ->
					STBVorbis.stb_vorbis_get_info(decoder, info)
					val rate = info.sample_rate()
					val channels = if (info.channels() == 1) 1 else 2
					val lengthMs = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * 1000.0 / rate
					val stream = SongStream(ogg, decoder, rate, channels, lengthMs)
					current = stream
					return stream
				}
			} catch (e: IOException) {
				STBVorbis.stb_vorbis_close(decoder)
				MemoryUtil.memFree(ogg)
				throw e
			} catch (e: RuntimeException) {
				STBVorbis.stb_vorbis_close(decoder)
				MemoryUtil.memFree(ogg)
				throw IOException("$path can't be played: ${e.message}", e)
			}
		}
	}
}
