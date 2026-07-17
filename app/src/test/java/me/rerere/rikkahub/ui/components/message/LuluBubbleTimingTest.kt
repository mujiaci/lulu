package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.ai.transformers.LULU_BUBBLE_SEGMENT_METADATA_TYPE
import me.rerere.rikkahub.data.ai.transformers.LULU_PRESENCE_METADATA_TYPE
import org.junit.Assert.assertEquals
import org.junit.Test

class LuluBubbleTimingTest {
    @Test
    fun `presegmented bubbles stagger by index and pacing`() {
        val presence = UIMessageAnnotation.Metadata(
            type = LULU_PRESENCE_METADATA_TYPE,
            data = buildJsonObject { put("bubble_pacing", "slow") },
        )
        fun annotations(index: Int) = listOf(
            presence,
            UIMessageAnnotation.Metadata(
                type = LULU_BUBBLE_SEGMENT_METADATA_TYPE,
                data = buildJsonObject {
                    put("index", index)
                    put("count", 3)
                },
            ),
        )

        assertEquals(360L, annotations(0).luluBubbleInitialDelayMillis())
        assertEquals(720L, annotations(1).luluBubbleInitialDelayMillis())
        assertEquals(1_080L, annotations(2).luluBubbleInitialDelayMillis())
    }

    @Test
    fun `unsegmented bubble uses one pacing interval`() {
        assertEquals(180L, emptyList<UIMessageAnnotation>().luluBubbleInitialDelayMillis())
    }
}
