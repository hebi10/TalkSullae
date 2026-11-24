package com.hebi.talksullae.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomChatDraftStoreTest {
    private class Store : RoomChatDraftStore {
        private val entries = mutableMapOf<String, StoredRoomDraft>()
        override fun load(roomId: String) = entries[roomId]
        override fun save(roomId: String, value: StoredRoomDraft, durable: Boolean): Boolean {
            entries[roomId] = value
            return true
        }
        override fun complete(roomId: String, requestId: String) {
            val value = entries[roomId] ?: return
            if(value.pending?.requestId == requestId) entries[roomId] = value.copy(pending = null)
        }
    }
    private class Repo : RoomChatRepository {
        override fun watchRecent(roomId: String,onPage:(RoomChatPage)->Unit,onError:(String)->Unit): ListenerRegistration {
            onPage(RoomChatPage(emptyList(),null,false,false))
            return ListenerRegistration {}
        }
        override suspend fun loadOlder(roomId: String,cursor:DocumentSnapshot): RoomChatPage = error("unused")
        override suspend fun sendMessage(roomId:String,requestId:String,text:String):String = error("unused")
    }
    @Test fun draftAndPendingAreRestoredOnlyForTheSameRoom() {
        val store = Store()
        val pending = PendingRoomMessage("123e4567-e89b-42d3-a456-000000000001","전송 중")
        store.save("room1",StoredRoomDraft("새 초안",pending),true)
        val vm = RoomChatViewModel(Repo(),store)
        vm.start("room1")
        assertEquals("새 초안",vm.state.value.draft)
        assertEquals(pending,vm.state.value.pending)
        vm.stop()
        vm.start("room2")
        assertEquals("",vm.state.value.draft)
        assertNull(vm.state.value.pending)
        vm.stop()
        vm.start("room1")
        assertEquals(pending,vm.state.value.pending)
    }
}
