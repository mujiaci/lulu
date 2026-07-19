package me.rerere.rikkahub.data.voicecall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallStreamingTest {
    @Test
    fun `emits complete sentence once and flushes final remainder`() {
        val segmenter = VoiceCallStreamSegmenter()

        assertTrue(segmenter.offer("先说第一句").isEmpty())
        assertEquals(listOf("先说第一句。"), segmenter.offer("先说第一句。后面还在生成"))
        assertTrue(segmenter.offer("先说第一句。后面还在生成").isEmpty())
        assertEquals(listOf("后面还在生成"), segmenter.finish("先说第一句。后面还在生成"))
    }

    @Test
    fun `uses a sufficiently long comma phrase as an early speech boundary`() {
        val segmenter = VoiceCallStreamSegmenter()
        val text = "这一段已经足够长可以先自然地说给用户听了，后面仍在继续"

        assertEquals(
            listOf("这一段已经足够长可以先自然地说给用户听了，"),
            segmenter.offer(text),
        )
    }

    @Test
    fun `never streams private runtime tags`() {
        val segmenter = VoiceCallStreamSegmenter()

        assertTrue(
            segmenter.offer("<companion_runtime>private=true。</companion_runtime>角色回复。").isEmpty(),
        )
    }

    @Test
    fun `does not repeat speech when provider rewrites emitted prefix`() {
        val segmenter = VoiceCallStreamSegmenter()

        assertEquals(listOf("原来的第一句。"), segmenter.offer("原来的第一句。"))
        assertTrue(segmenter.offer("改写后的第一句。").isEmpty())
    }
}
