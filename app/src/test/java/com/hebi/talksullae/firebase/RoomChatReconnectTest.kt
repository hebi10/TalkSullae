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
        override fun watchRecent(
            roomId: String, onPage: (RoomChatPage) -> Unit, onError: (String) -> Unit
        ): ListenerRegistration {
            subscriptions++
            this.onError = onError
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
}
