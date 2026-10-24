package com.sasami.vlc

import java.io.ByteArrayOutputStream
import java.io.File

/** Small deterministic uncompressed AVI; no ffmpeg download or external media required. */
object VideoFixture {
    fun create(file: File) {
        val width = 320
        val height = 180
        val frames = 100
        val frameBytes = width * height * 3
        fun ByteArrayOutputStream.fourcc(value: String) { write(value.toByteArray(Charsets.US_ASCII)) }
        fun ByteArrayOutputStream.int(value: Int) { repeat(4) { write(value ushr (it * 8) and 255) } }
        fun ByteArrayOutputStream.short(value: Int) { repeat(2) { write(value ushr (it * 8) and 255) } }
        fun bytes(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()
        fun chunk(name: String, content: ByteArray) = bytes {
            fourcc(name); int(content.size); write(content); if (content.size % 2 != 0) write(0)
        }
        fun list(name: String, content: ByteArray) = chunk("LIST", bytes { fourcc(name); write(content) })
        val avih = chunk("avih", bytes {
            int(100000); int(frameBytes * 10); int(0); int(16); int(frames); int(0); int(1)
            int(frameBytes); int(width); int(height); repeat(4) { int(0) }
        })
        val strh = chunk("strh", bytes {
            fourcc("vids"); fourcc("DIB "); int(0); short(0); short(0); int(0); int(1); int(10)
            int(0); int(frames); int(frameBytes); int(-1); int(0); short(0); short(0); short(width); short(height)
        })
        val strf = chunk("strf", bytes {
            int(40); int(width); int(height); short(1); short(24); int(0); int(frameBytes)
            int(0); int(0); int(0); int(0)
        })
        val headers = list("hdrl", avih + list("strl", strh + strf))
        val movie = list("movi", bytes {
            repeat(frames) { frame ->
                write(chunk("00db", ByteArray(frameBytes) { index ->
                    val x = (index / 3) % width
                    val y = (index / 3) / width
                    when (index % 3) { 0 -> (80 + frame) % 255; 1 -> y * 255 / height; else -> x * 255 / width }.toByte()
                }))
            }
        })
        val index = chunk("idx1", bytes {
            repeat(frames) { frame -> fourcc("00db"); int(16); int(4 + frame * (frameBytes + 8)); int(frameBytes) }
        })
        file.writeBytes(chunk("RIFF", bytes { fourcc("AVI "); write(headers); write(movie); write(index) }))
    }
}
