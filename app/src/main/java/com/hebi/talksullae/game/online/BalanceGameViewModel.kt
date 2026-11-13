package com.hebi.talksullae.game.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hebi.talksullae.game.core.GamePhase
import java.util.UUID

internal data class BalancePendingAction(
    val kind: String,
    val requestId: String,
    val revision: Long,
    val payload: Map<String,Any>,
    val rulesVersion: Int
)

internal data class BalanceGameState(
    val session: OnlineGameSnapshot? = null,
    val personal: Map<*, *>? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val personalLoading: Boolean = false,
    val otherGameActive: Boolean = false,
    val error: String? = null,
    val personalError: String? = null,
    val info: String? = null,
    val pending: BalancePendingAction? = null
){
    val submitted: Boolean get()=personal?.get("submitted") as? Boolean ?: false
    val ownChoice: String? get()=personal?.get("ownChoice") as? String
}

internal class BalanceGameViewModel(
    private val repository:OnlineGameSessionRepository
): ViewModel(){
    private val mutable=MutableStateFlow(BalanceGameState())
    val state:StateFlow<BalanceGameState> = mutable
    private var room:String?=null
    private var uid:String?=null
    private var gameId:String?=null
    private var listener:ListenerRegistration?=null
    private var generation=0L
    private var personalRequestRevision:Long?=null
    private var personalPending=false

    fun start(roomId:String,userId:String){
        if(room==roomId&&uid==userId)return
        stop()
        room=roomId;uid=userId
        refresh()
    }
    fun stop(){
        generation++
        listener?.remove();listener=null
        room=null;uid=null;gameId=null
        personalPending=false;personalRequestRevision=null
        mutable.value=BalanceGameState()
    }
    fun refresh(){
        val currentRoom=room?:return
        val token=generation
        mutable.update{it.copy(loading=true,error=null)}
        viewModelScope.launch{
            try{
                val found=repository.findActiveSession(currentRoom)
                if(generation!=token||room!=currentRoom)return@launch
                if(found==null){
                    listener?.remove();listener=null;gameId=null
                    mutable.update{BalanceGameState(loading=false)}
                }else bind(currentRoom,found)
            }catch(e:Exception){
                if(generation==token)mutable.update{
                    it.copy(loading=false,error=e.localizedMessage?:"게임 조회 오류")
                }
            }
        }
    }
    private fun bind(currentRoom:String,id:String){
        if(gameId==id&&listener!=null){
            mutable.update{it.copy(loading=false)}
            return
        }
        listener?.remove();listener=null;gameId=id
        mutable.update{BalanceGameState(loading=true)}
        val token=generation
        listener=repository.watchSession(currentRoom,id,{snap->
            if(generation!=token||gameId!=id)return@watchSession
            if(snap==null){
                gameId=null;listener?.remove();listener=null
                mutable.update{BalanceGameState(loading=false,error="게임 세션을 확인할 수 없습니다.")}
            }else if(snap.session.gameId!="balance"){
                mutable.update{BalanceGameState(loading=false,otherGameActive=true)}
            }else{
                val member=snap.participants.any{it.uid==uid}
                mutable.update{it.copy(
                    session=snap,loading=false,otherGameActive=false,
                    personal=if(member)it.personal else null,
                    error=null
                )}
                if(member)refreshPersonal(currentRoom,id,token)
            }
        },{error->
            if(generation==token&&gameId==id){
                mutable.update{it.copy(loading=false,error=error)}
            }
        })
    }
    fun refreshPersonal(){
        val currentRoom=room?:return
        val id=gameId?:return
        refreshPersonal(currentRoom,id,generation)
    }
    private fun refreshPersonal(currentRoom:String,id:String,token:Long){
        val revision=mutable.value.session?.session?.revision?:return
        if(mutable.value.personalLoading){
            if(personalRequestRevision!=revision)personalPending=true
            return
        }
        personalRequestRevision=revision
        mutable.update{it.copy(personalLoading=true,personalError=null)}
        viewModelScope.launch {
            try{
                val (returned,personal)=repository.restoreMySession(currentRoom,id)
                if(generation==token&&gameId==id){
                    val current=mutable.value.session?.session
                    if(current?.revision!=revision) personalPending=true
                    else if(returned.session.revision!=revision)
                        mutable.update{it.copy(personalError="게임 상태가 변경됐습니다. 다시 조회해 주세요.")}
                    else mutable.update{it.copy(personal=personal,personalError=null)}
                }
            }catch(_:Exception){
                if(generation==token&&gameId==id)
                    mutable.update{it.copy(personalError="나의 선택을 불러오지 못했습니다. 다시 시도해 주세요.")}
            }finally{
                if(generation==token&&gameId==id){
                    personalRequestRevision=null
                    mutable.update{it.copy(personalLoading=false)}
                    if(personalPending){
                        personalPending=false
                        refreshPersonal(currentRoom,id,token)
                    }
                }
            }
        }
    }
    fun create(asyncMode:Boolean){
        val currentRoom=room?:return
        if(mutable.value.busy||mutable.value.session!=null||
           mutable.value.otherGameActive)return
        val token=generation
        mutable.update{it.copy(busy=true,error=null)}
        viewModelScope.launch{
            try{
                val id=repository.createSession(currentRoom,"balance",
                    if(asyncMode)"async" else "realtime",UUID.randomUUID().toString())
                if(token==generation&&room==currentRoom)bind(currentRoom,id)
            }catch(e:Exception){
                if(token==generation)mutable.update{
                    it.copy(error=e.localizedMessage?:"게임 생성에 실패했습니다.")
                }
            }finally{
                if(token==generation)mutable.update{it.copy(busy=false)}
            }
        }
    }
    fun action(kind:String,extras:Map<String,Any> = emptyMap()){
        val snap=mutable.value.session?:return
        if(mutable.value.busy||mutable.value.pending!=null)return
        val task=BalancePendingAction(kind,UUID.randomUUID().toString(),
            snap.session.revision,mapOf("kind" to kind)+extras,snap.session.rulesVersion)
        mutable.update{it.copy(pending=task,error=null)}
        submit(task)
    }
    fun retryAction(){mutable.value.pending?.let{
        if(!mutable.value.busy)submit(it)
    }}
    fun discardFailedAction(){
        if(mutable.value.busy)return
        mutable.update{it.copy(pending=null,error=null,
            info="서버 상태를 다시 확인해 주세요.")}
        refresh()
    }
    private fun submit(task:BalancePendingAction){
        val currentRoom=room?:return
        val id=gameId?:return
        val token=generation
        mutable.update{it.copy(busy=true,error=null)}
        viewModelScope.launch{
            try{
                val newRevision=repository.submitAction(currentRoom,id,"balance",
                    task.rulesVersion,task.revision,task.requestId,task.payload)
                if(token==generation&&gameId==id){
                    mutable.update{it.copy(pending=null,error=null)}
                    if(task.kind=="submit"){
                        try{
                            repository.submitAction(currentRoom,id,"balance",
                                task.rulesVersion,newRevision,
                                UUID.randomUUID().toString(),mapOf("kind" to "advance"))
                        }catch(_:Exception){
                            // Other participants may not have submitted yet.
                        }
                    }
                    refreshPersonal(currentRoom,id,token)
                }
            }catch(e:Exception){
                if(token==generation&&gameId==id)mutable.update{
                    it.copy(error=(e.localizedMessage?:"게임 요청 실패")+
                        " · 같은 요청 재시도 가능")
                }
            }finally{
                if(token==generation)mutable.update{it.copy(busy=false)}
            }
        }
    }
    override fun onCleared(){stop();super.onCleared()}
}
