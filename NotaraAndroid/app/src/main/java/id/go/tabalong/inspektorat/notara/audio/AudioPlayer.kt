package id.go.tabalong.inspektorat.notara.audio

import android.media.MediaPlayer

/** Pembungkus MediaPlayer ringan untuk memutar satu berkas audio. */
class AudioPlayer {
    private var player: MediaPlayer? = null
    var isPrepared = false
        private set

    fun load(path: String, onPrepared: (durationMs: Int) -> Unit, onCompletion: () -> Unit) {
        release()
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnPreparedListener {
                isPrepared = true
                onPrepared(it.duration)
            }
            setOnCompletionListener { onCompletion() }
            prepareAsync()
        }
    }

    fun play() { player?.start() }
    fun pause() { player?.pause() }
    fun isPlaying(): Boolean = player?.isPlaying == true
    fun currentPosition(): Int = try { player?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun duration(): Int = try { player?.duration ?: 0 } catch (e: Exception) { 0 }
    fun seekTo(ms: Int) { try { player?.seekTo(ms) } catch (_: Exception) {} }

    fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        isPrepared = false
    }
}
