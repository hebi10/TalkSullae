package com.hebi.talksullae.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomChatReconnectTest {
    private class FakeRepo : RoomChatRepository {
        var subscriptions = 0
        var removes = 0
        var onError: ((String) -> Unit)? = null
        var onPage: ((RoomChatPage) -> Unit)? = null
        override fun watchRecent(
            roomId: String, onPage: (RoomChatPage) -> Unit, onError: (String) -> Unit
        ): ListenerRegistration {
            subscriptions++
            this.onError = onError
            this.onPage = onPage
            return ListenerRegistration { removes++ }
        }
        override suspend fun loadOlder(
            roomId: String, cursor: DocumentSnapshot
        ): RoomChatPage = error("unused")
        override suspend fun sendMessage(roomId: String, requestId: String, text: String): String =
            error("unused")
    }

    @Test fun retryRestoresChatDraftAndStartsFreshSubscription() {
        val repository = FakeRepo()
        val vm = RoomChatViewModel(repository)
        vm.start("a".repeat(20))
        vm.updateDraft("전송 전 초안")
        repository.onError?.invoke("네트워크를 확인해 주세요.")
        assertFalse(vm.state.value.initialLoading)
        vm.retryRead()
        assertEquals("전송 전 초안", vm.state.value.draft)
        assertTrue(vm.state.value.initialLoading)
        assertEquals(null, vm.state.value.readError)
        assertEquals(2, repository.subscriptions)
        assertEquals(1, repository.removes)
    }

    @Test fun cacheOnlyDataIsNeverDisplayedAndServerReconnectionRestoresMessages() {
        val repository = FakeRepo()
        val vm = RoomChatViewModel(repository)
        vm.start("a".repeat(20))
        val message = RoomChatMessage("m", "friend", "친구", "메시지", 100L)
        repository.onPage?.invoke(RoomChatPage(listOf(message), null, false, true))
        assertTrue(vm.state.value.messages.isEmpty())
        assertTrue(vm.state.value.fromCache)
        repository.onPage?.invoke(RoomChatPage(listOf(message), null, false, false))
        assertEquals(listOf(message), vm.state.value.messages)
        assertFalse(vm.state.value.fromCache)
        repository.onPage?.invoke(RoomChatPage(listOf(message), null, false, true))
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test fun removedMessagesInsideCurrentWindowAreNotMovedIntoHistory() {
        fun msg(id: String, time: Long) = RoomChatMessage(id, "friend", "친구", id, time)
        val old = msg("old", 1)
        val evicted = msg("evicted", 2)
        val boundary = msg("boundary", 3)
        val removed = msg("removed", 4)
        val new = msg("new", 5)
        assertEquals(listOf(old, evicted), retainOlderChatMessages(
            listOf(old), listOf(evicted, boundary, removed), listOf(new, boundary), true))
        assertTrue(retainOlderChatMessages(listOf(old), listOf(boundary, removed),
            listOf(boundary), false).isEmpty())
    }
}
