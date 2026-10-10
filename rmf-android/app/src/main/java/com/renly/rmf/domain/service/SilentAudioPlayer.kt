package com.renly.rmf.domain.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

/**
 * 静音心流伴奏保活播放器 (Silent Audio Keep-Alive Player)
 *
 * 原理与用途：
 * ColorOS 14/15/16 及国内定制系统的 SystemUI 流体云识别服务，在检测到前台服务持有活跃 MediaSession 且存在
 * 底层音频流输出时，会 100% 触发状态栏挖孔旁动态流体云胶囊和锁屏实时媒体控制卡片。
 * 本播放器输出轻量纯净的 8000Hz 零分贝静音 PCM 数据流，不请求独占音频焦点，绝不干扰用户听歌或看视频。
 */
class SilentAudioPlayer {

    private val tag = "SilentAudioPlayer"
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    @Volatile
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        isPlaying = true

        playJob?.cancel()
        playJob = scope.launch {
            try {
                val sampleRate = 8000
                val channelConfig = AudioFormat.CHANNEL_OUT_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBufferSize, 1024)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack?.setVolume(0.001f)
                audioTrack?.play()

                val silenceBuffer = ByteArray(bufferSize) // 全0静音缓冲
                while (isActive && isPlaying) {
                    val written = audioTrack?.write(silenceBuffer, 0, silenceBuffer.size) ?: -1
                    if (written < 0) {
                        break
                    }
                    delay(100) // 低开销间歇写入
                }
            } catch (e: Exception) {
                Log.w(tag, "Silent audio play failed, continuing silently: ${e.message}")
            } finally {
                releaseTrack()
            }
        }
    }

    fun stop() {
        isPlaying = false
        playJob?.cancel()
        playJob = null
        releaseTrack()
    }

    private fun releaseTrack() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        } finally {
            audioTrack = null
        }
    }
}
