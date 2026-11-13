package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.online.FirestoreOnlineGameSessionRepository
import com.hebi.talksullae.game.online.ForbiddenGameViewModel
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun OnlineForbiddenGameHost(
    uid: String,roomId: String,roomName: String,ownerId: String,
    members: List<CloudMember>,onBack: () -> Unit,
    repository: OnlineGameSessionRepository? = null
) {
    val repo=remember(repository){repository ?: FirestoreOnlineGameSessionRepository()}
    val factory=remember(repo){
        object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T =
                ForbiddenGameViewModel(repo) as T
        }
    }
    val vm:ForbiddenGameViewModel=viewModel(key="forbidden-$uid-$roomId",factory=factory)
    DisposableEffect(vm,uid,roomId){
        vm.start(roomId,uid)
        onDispose{vm.stop()}
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snap=state.session
    val phase=snap?.session?.phase
    val participant=snap?.participants?.any{it.uid==uid}?:false
    val names=members.associate{it.uid to it.name}
    val eliminated=(snap?.publicState?.get("eliminatedUids") as? List<*>)
        ?.mapNotNull{it as? String}.orEmpty()
    var nowMillis by remember{mutableLongStateOf(System.currentTimeMillis())}
    val deadline=snap?.session?.deadlineEpochMillis
    LaunchedEffect(deadline){
        while(deadline!=null){
            nowMillis=System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining=deadline?.let{max(0L,(it-nowMillis+999L)/1000L)}
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal=16.dp,vertical=12.dp)
            .testTag("forbidden-game-host"),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        Row(Modifier.fillMaxWidth()){
            Text("금지어 채팅 · $roomName",Modifier.weight(1f),
                style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onBack){Text("방 관리")}
        }
        Text("3~10명이 다른 친구의 금지어를 유도합니다. 본인 금지어는 공개되지 않습니다.")
        HorizontalDivider()
        if(state.loading)CircularProgressIndicator()
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        state.info?.let{Text(it)}
        if(state.pendingAction!=null&&!state.busy){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick=vm::retryAction){Text("같은 요청 재시도")}
                TextButton(onClick=vm::discardFailedAction){Text("서버 상태 재확인")}
            }
        }
        if(state.otherGameActive){
            Text("이 방에서 다른 게임이 진행 중입니다. 종료 후 금지어 게임을 시작할 수 있습니다.")
            OutlinedButton(onClick=vm::refresh,
                modifier=Modifier.fillMaxWidth()){Text("진행 중인 게임 재조회")}
        }else if(!state.loading&&snap==null){
            Text("현재 진행 중인 금지어 게임이 없습니다.")
            if(uid==ownerId){
                PrimaryAction("새 금지어 게임 만들기",onClick=vm::create,
                    enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("forbidden-create"))
            }else Text("방장이 게임을 생성하면 참여할 수 있습니다.")
            OutlinedButton(onClick=vm::refresh,
                modifier=Modifier.fillMaxWidth()){Text("게임 다시 확인")}
        }
        if(snap!=null){
            Text("상태: ${phase?.name ?: "확인 중"} · 라운드 ${snap.session.round}",
                style=MaterialTheme.typography.titleMedium)
            remaining?.let{Text("남은 시간 약 ${it}초 · 서버 기준")}
            if(snap.fromCache)Text("저장된 상태입니다. 서버 동기화를 확인해 주세요.")
            if(phase==GamePhase.WAITING||phase==GamePhase.READY){
                Text("참가 ${snap.participants.size}명 · 최소 3명, 최대 10명")
                snap.participants.forEach{player->
                    Row(Modifier.fillMaxWidth()){
                        Text(names[player.uid]?:"친구",Modifier.weight(1f))
                        Text(if(player.ready)"준비 완료" else "준비 중")
                    }
                }
                if(!participant){
                    PrimaryAction("참가",onClick={vm.action("join")},
                        enabled=!state.busy&&state.pendingAction==null,
                        modifier=Modifier.fillMaxWidth().testTag("forbidden-join"))
                }else{
                    val ready=snap.participants.first{it.uid==uid}.ready
                    PrimaryAction(if(ready)"준비 취소" else "준비 완료",
                        onClick={vm.action("set_ready",mapOf("ready" to !ready))},
                        enabled=!state.busy&&state.pendingAction==null,
                        modifier=Modifier.fillMaxWidth().testTag("forbidden-ready"))
                }
                if(uid==ownerId&&phase==GamePhase.READY){
                    PrimaryAction("금지어 게임 시작",onClick={vm.action("start")},
                        enabled=!state.busy&&state.pendingAction==null,
                        modifier=Modifier.fillMaxWidth().testTag("forbidden-start"))
                }
            }
            if(participant&&phase==GamePhase.PLAYING){
                Text(snap.publicState["prompt"] as? String ?: "금지어를 유도해 주세요.",
                    style=MaterialTheme.typography.titleMedium)
                Text("다른 친구의 금지어",style=MaterialTheme.typography.titleMedium)
                if(state.personal==null){
                    Text("개인 금지어 정보를 확인 중입니다.")
                }else{
                    state.otherWords.forEach{(target,word)->
                        Text((names[target]?:"친구")+" · "+word,
                            modifier=Modifier.testTag("forbidden-other-"+target))
                    }
                }
                state.personalError?.let{error->
                    Text(error,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick=vm::retryPersonal,enabled=!state.personalLoading,
                        modifier=Modifier.testTag("forbidden-personal-retry")){
                        Text("금지어 정보 다시 불러오기")
                    }
                }
                Text("탈락 ${eliminated.size}명 / 참가 ${snap.participants.size}명")
                if(uid in eliminated)Text("금지어를 말해 탈락했습니다. 채팅을 보낼 수 없습니다.")
                Column(Modifier.fillMaxWidth().heightIn(max=280.dp)
                    .verticalScroll(rememberScrollState()).testTag("forbidden-chat-messages"),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(state.canLoadOlder){
                        TextButton(onClick=vm::loadOlder,enabled=!state.loadingOlder){
                            Text(if(state.loadingOlder)"이전 대화 조회 중…" else "이전 대화 보기")
                        }
                    }
                    state.messages.forEach{message->
                        Text((names[message.senderUid]?:"친구")+": "+message.text)
                    }
                    if(state.messages.isEmpty())Text("아직 대화가 없습니다.")
                }
                OutlinedTextField(
                    value=state.draft,onValueChange=vm::editMessage,
                    enabled=state.personal!=null&&uid !in eliminated &&
                        state.pendingMessage==null&&!state.sending,
                    label={Text("게임 메시지 · 최대 500자")},maxLines=3,
                    modifier=Modifier.fillMaxWidth().testTag("forbidden-chat-input")
                )
                if(state.pendingMessage!=null&&!state.sending){
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        TextButton(onClick=vm::retryMessage){Text("메시지 재시도")}
                        TextButton(onClick=vm::abandonMessage){Text("다시 작성")}
                    }
                }else{
                    PrimaryAction("게임 채팅 전송",onClick=vm::sendMessage,
                        enabled=state.personal!=null&&uid !in eliminated &&
                            !state.sending&&state.pendingMessage==null&&
                            state.draft.trim().length in 1..500,
                        modifier=Modifier.fillMaxWidth().testTag("forbidden-chat-send"))
                }
            }
            if(phase==GamePhase.REVEAL||phase==GamePhase.FINISHED){
                Text("금지어 결과",style=MaterialTheme.typography.titleMedium)
                Text(snap.publicState["publishedResult"] as? String ?: "결과 집계 중",
                    modifier=Modifier.testTag("forbidden-result"))
            }
            if(phase==GamePhase.CANCELLED)Text("게임이 취소됐습니다.")
            if(uid==ownerId&&phase!=GamePhase.FINISHED&&phase!=GamePhase.CANCELLED){
                OutlinedButton(onClick={vm.action("cancel")},
                    enabled=!state.busy&&state.pendingAction==null,
                    modifier=Modifier.fillMaxWidth()){Text("게임 취소")}
            }
        }
    }
}
