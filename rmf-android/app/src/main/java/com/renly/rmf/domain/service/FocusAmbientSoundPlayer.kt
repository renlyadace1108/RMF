package com.renly.rmf.domain.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.random.Random

enum class AmbientSoundType(val id: String, val title: String, val icon: String, val desc: String) {
    NONE("NONE", "静音", "🔇", "关闭环境背景音"),
    WHITE_NOISE("WHITE_NOISE", "经典白噪", "📻", "全频段平稳遮噪，阻隔外界琐碎交谈"),
    RAIN("RAIN", "淅沥雨声", "🌧️", "窗外柔和细雨，抚平焦虑助深度入定"),
    FOREST("FOREST", "晨曦森林", "🌲", "微风穿过树梢，空灵自然声景"),
    FIREPLACE("FIREPLACE", "柴火壁炉", "🪵", "温暖壁炉柴火噼啪，安宁温馨空间"),
    CAFE("CAFE", "午后咖啡", "☕", "低沉柔和的环境呢喃，模拟咖啡馆氛围")
}

object FocusAmbientSoundPlayer {
    private var isPlaying = false
    private var currentType: AmbientSoundType = AmbientSoundType.NONE
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getCurrentType(): AmbientSoundType = currentType

    fun isPlaying(): Boolean = isPlaying

    @Synchronized
    fun startSound(type: AmbientSoundType) {
        if (type == AmbientSoundType.NONE) {
            stopSound()
            return
        }

        if (currentType == type && isPlaying) return

        stopSound()
        currentType = type
        isPlaying = true

        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            playJob = scope.launch {
                val chunkSize = 2048
                val buffer = ShortArray(chunkSize)
                var phase = 0.0
                var brownNoise = 0.0
                var pinkNoise = 0.0
                val random = Random(System.currentTimeMillis())

                while (isActive && isPlaying) {
                    when (type) {
                        AmbientSoundType.WHITE_NOISE -> {
                            for (i in 0 until chunkSize) {
                                // 白噪音：平稳随机高斯噪音 (0.15 振幅避免刺耳)
                                val sample = (random.nextFloat() * 2f - 1f) * 0.15f
                                buffer[i] = (sample * 32767).toInt().toShort()
                            }
                        }
                        AmbientSoundType.RAIN -> {
                            for (i in 0 until chunkSize) {
                                // 模拟雨声：低通粉噪 + 随机细微水滴脉冲
                                val white = random.nextDouble() * 2.0 - 1.0
                                pinkNoise = 0.95 * pinkNoise + 0.05 * white
                                val drop = if (random.nextInt(120) == 0) (random.nextDouble() - 0.5) * 0.4 else 0.0
                                val total = (pinkNoise * 0.35 + drop).coerceIn(-1.0, 1.0)
                                buffer[i] = (total * 32767).toInt().toShort()
                            }
                        }
                        AmbientSoundType.FOREST -> {
                            for (i in 0 until chunkSize) {
                                // 森林微风：慢频振荡调制粉噪
                                phase += 0.0003
                                val windMod = (sin(phase) + 1.2) * 0.5
                                val white = random.nextDouble() * 2.0 - 1.0
                                brownNoise = (brownNoise + (0.02 * white)) / 1.02
                                val sample = (brownNoise * 0.6 * windMod).coerceIn(-1.0, 1.0)
                                buffer[i] = (sample * 32767).toInt().toShort()
                            }
                        }
                        AmbientSoundType.FIREPLACE -> {
                            for (i in 0 until chunkSize) {
                                // 柴火壁炉：深沉红噪 + 随机柴火噼啪 Crackle 脉冲
                                val white = random.nextDouble() * 2.0 - 1.0
                                brownNoise = (brownNoise + (0.04 * white)) / 1.04
                                val pop = if (random.nextInt(450) == 0) {
                                    (random.nextDouble() * 0.8 - 0.4)
                                } else 0.0
                                val sample = (brownNoise * 0.3 + pop).coerceIn(-1.0, 1.0)
                                buffer[i] = (sample * 32767).toInt().toShort()
                            }
                        }
                        AmbientSoundType.CAFE -> {
                            for (i in 0 until chunkSize) {
                                // 咖啡馆：多频段低频混叠平缓声景
                                phase += 0.001
                                val white = random.nextDouble() * 2.0 - 1.0
                                pinkNoise = 0.97 * pinkNoise + 0.03 * white
                                val hum = sin(phase * 2.4) * 0.08
                                val sample = (pinkNoise * 0.4 + hum).coerceIn(-1.0, 1.0)
                                buffer[i] = (sample * 32767).toInt().toShort()
                            }
                        }
                        else -> {
                            buffer.fill(0)
                        }
                    }

                    audioTrack?.write(buffer, 0, chunkSize)
                }
            }
        } catch (_: Exception) {
            stopSound()
        }
    }

    @Synchronized
    fun stopSound() {
        isPlaying = false
        currentType = AmbientSoundType.NONE
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
