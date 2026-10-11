package com.renly.rmf.domain.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * 静音心流伴奏保活播放器 (Silent Audio Keep-Alive Player)
 *
 * 原理与用途：
 * 系统的 SystemUI 胶囊通知识别服务，在检测到前台服务持有活跃 MediaSession 且存在
 * 底层音频流输出时，会稳定触发状态栏动态胶囊通知和锁屏实时媒体控制卡片。
 * 本播放器输出轻量纯净的 8000Hz 零分贝静音 PCM 数据流，不请求独占音频焦点，绝不干扰用户听歌或看视频。
 *
 * 功耗与性能深度优化：
 * 采用 AudioTrack.MODE_STATIC 静态硬件环回缓冲技术，一次性写入 2048 字节静音 PCM 数据后直接
 * 委托音频芯片/硬件底层循环播放，彻底消除软件定时轮询与协程唤醒（0% CPU，0% 线程唤醒，极度省电）。
 */
class SilentAudioPlayer {

    private val tag = "SilentAudioPlayer"
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    @Synchronized
    fun start() {
        if (isPlaying) return
        isPlaying = true

        try {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, 2048)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfig)
                .build()

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }

            // 写入全 0 静音 PCM 数据 (16-bit mono: 1 帧 = 2 字节)
            val silenceBuffer = ByteArray(bufferSize)
            track.write(silenceBuffer, 0, silenceBuffer.size)
            val numFrames = silenceBuffer.size / 2
            track.setLoopPoints(0, numFrames, -1) // 硬件层无限静默循环
            track.setVolume(0.001f)
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.w(tag, "Silent audio hardware loop setup failed, continuing silently: ${e.message}")
            releaseTrack()
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
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
