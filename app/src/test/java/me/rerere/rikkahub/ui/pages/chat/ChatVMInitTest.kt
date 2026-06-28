package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

class ChatVMInitTest {
    @Test
    fun `generation done listener uses constructor flow during initialization`() {
        val flow = MutableSharedFlow<Uuid>()

        assertSame(flow, selectGenerationDoneFlowForInit(flow))
    }
}
