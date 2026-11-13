package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.hebi.talksullae.game.online.BalanceGameViewModel
import com.hebi.talksullae.game.online.FirestoreOnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun OnlineBalanceGameHost(
    uid:String,roomId:String,roomName:String,ownerId:String,
    members:List<CloudMember>,onBack:()->Unit,
    repository:OnlineGameSessionRepository?=null
){
    val repo=remember(repository){repository?:FirestoreOnlineGameSessionRepository()}
    val factory=remember(repo){
        object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T =
                BalanceGameViewModel(repo) as T
        }
    }
    val vm:BalanceGameViewModel=viewModel(key="balance-$uid-$roomId",factory=factory)
    DisposableEffect(vm,uid,roomId){
        vm.start(roomId,uid)
        onDispose{vm.stop()}
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session=state.session
    val phase=session?.session?.phase
    val member=session?.participants?.any{it.uid==uid}?:false
    val names=members.associate{it.uid to it.name}
    val options=(session?.publicState?.get("options") as? List<*>)
        ?.mapNotNull{it as? String}.orEmpty()
    val mode=session?.mode?.wireValue
    var asyncMode by remember {mutableStateOf(false)}
    var choice by remember(session?.session?.sessionId,phase){mutableStateOf("")}
    var target by remember(session?.session?.sessionId,phase){mutableStateOf("")}
    var prediction by remember(session?.session?.sessionId,phase){mutableStateOf("")}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    val deadline=session?.session?.deadlineEpochMillis
    LaunchedEffect(deadline){
        while(deadline!=null){
            now=System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining=deadline?.let{max(0L,(it-now+999L)/1000L)}
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal=16.dp,vertical=12.dp).testTag("balance-game-host"),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        Row(Modifier.fillMaxWidth()){
            Text("밸런스 예측 · $roomName",Modifier.weight(1f),
                style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onBack){Text("방 관리")}
        }
        Text("내 A/B 선택을 제출한 뒤 다른 친구의 선택을 예측합니다.")
        if(state.loading)CircularProgressIndicator()
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        state.personalError?.let{
            Text(it,color=MaterialTheme.colorScheme.error)
            TextButton(onClick=vm::refreshPersonal,
                modifier=Modifier.testTag("balance-personal-retry")){
                Text("나의 선택 다시 확인")
            }
        }
        state.info?.let{Text(it)}
        if(state.pending!=null&&!state.busy){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick=vm::retryAction){Text("같은 요청 재시도")}
                TextButton(onClick=vm::discardFailedAction){Text("서버 상태 다시 확인")}
            }
        }
        if(state.otherGameActive){
            Text("다른 게임이 진행 중입니다. 해당 게임 종료 후 시작해 주세요.")
            OutlinedButton(onClick=vm::refresh){Text("게임 다시 확인")}
        }else if(!state.loading&&session==null){
            Text("진행 중인 밸런스 게임이 없습니다.")
            if(uid==ownerId){
                Text("게임 방식")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={asyncMode=false},
                        modifier=Modifier.testTag("balance-mode-realtime")){
                        Text("실시간"+if(!asyncMode)" · 선택됨" else "")
                    }
                    OutlinedButton(onClick={asyncMode=true},
                        modifier=Modifier.testTag("balance-mode-async")){
                        Text("비동기"+if(asyncMode)" · 선택됨" else "")
                    }
                }
                PrimaryAction("새 밸런스 게임 만들기",onClick={vm.create(asyncMode)},
                    enabled=!state.busy,modifier=Modifier.fillMaxWidth()
                        .testTag("balance-create"))
            }else Text("방장이 게임을 만들면 참여할 수 있습니다.")
        }
        if(session!=null){
            Text("상태: ${phase?.name ?: "확인 중"} · ${if(mode=="async")"비동기" else "실시간"}",
                style=MaterialTheme.typography.titleMedium)
            remaining?.let{Text("남은 시간 약 ${it}초 · 서버 기준")}
            if(session.fromCache)Text("캐시 상태입니다. 서버 연결을 확인해 주세요.")
            if(phase==GamePhase.WAITING||phase==GamePhase.READY){
                Text("참가 ${session.participants.size}명 · 최소 2명, 최대 10명")
                session.participants.forEach{player->
                    Row(Modifier.fillMaxWidth()){
                        Text(names[player.uid]?:"친구",Modifier.weight(1f))
                        Text(if(player.ready)"준비 완료" else "준비 중")
                    }
                }
                if(!member){
                    PrimaryAction("게임 참가",onClick={vm.action("join")},
                        enabled=!state.busy&&state.pending==null,
                        modifier=Modifier.fillMaxWidth().testTag("balance-join"))
                }else{
                    val ready=session.participants.first{it.uid==uid}.ready
                    PrimaryAction(if(ready)"준비 취소" else "준비 완료",
                        onClick={vm.action("set_ready",mapOf("ready" to !ready))},
                        enabled=!state.busy&&state.pending==null,
                        modifier=Modifier.fillMaxWidth().testTag("balance-ready"))
                }
                if(uid==ownerId&&phase==GamePhase.READY){
                    PrimaryAction("밸런스 게임 시작",onClick={vm.action("start")},
                        enabled=!state.busy&&state.pending==null,
                        modifier=Modifier.fillMaxWidth().testTag("balance-start"))
                }
            }
            if(member&&phase==GamePhase.PLAYING){
                Text("나의 선택",style=MaterialTheme.typography.titleMedium)
                if(state.submitted){
                    Text("선택을 제출했습니다. 결과 공개 전까지 다른 사람의 선택은 비공개입니다.")
                    state.ownChoice?.let{Text("내 선택: $it")}
                }else if(options.size==2){
                    options.forEachIndexed{index,item->
                        val key=if(index==0)"A" else "B"
                        OutlinedButton(onClick={choice=key},
                            modifier=Modifier.fillMaxWidth().testTag("balance-choice-$key")){
                            Text("$key · $item"+if(choice==key)" · 선택됨" else "")
                        }
                    }
                    PrimaryAction("나의 선택 제출",
                        onClick={vm.action("submit",mapOf("choice" to choice))},
                        enabled=choice.isNotEmpty()&&!state.busy&&state.pending==null,
                        modifier=Modifier.fillMaxWidth().testTag("balance-own-submit"))
                }else Text("질문을 불러오는 중입니다.")
            }
            if(member&&phase==GamePhase.VOTING){
                Text("친구의 선택 예측",style=MaterialTheme.typography.titleMedium)
                if(state.submitted){
                    Text("예측을 제출했습니다. 다른 참가자의 제출을 기다립니다.")
                }else{
                    Text("예측할 친구")
                    session.participants.filter{it.uid!=uid}.forEach{player->
                        OutlinedButton(onClick={target=player.uid},
                            modifier=Modifier.fillMaxWidth()
                                .testTag("balance-predict-target-"+player.uid)){
                            Text((names[player.uid]?:"친구")+
                                if(target==player.uid)" · 선택됨" else "")
                        }
                    }
                    if(options.size==2){
                        options.forEachIndexed{index,item->
                            val key=if(index==0)"A" else "B"
                            OutlinedButton(onClick={prediction=key},
                                modifier=Modifier.fillMaxWidth()
                                    .testTag("balance-predict-choice-$key")){
                                Text("$key · $item"+if(prediction==key)" · 선택됨" else "")
                            }
                        }
                    }
                    PrimaryAction("예측 제출",onClick={
                        vm.action("submit",mapOf("choice" to mapOf(
                            "targetUid" to target,"prediction" to prediction)))
                    },enabled=target.isNotEmpty()&&prediction.isNotEmpty()&&
                        !state.busy&&state.pending==null,
                        modifier=Modifier.fillMaxWidth().testTag("balance-predict-submit"))
                }
            }
            if(phase==GamePhase.REVEAL||phase==GamePhase.FINISHED){
                Text("전체 선택·예측 결과",style=MaterialTheme.typography.titleMedium)
                Text(session.publicState["publishedResult"] as? String ?: "결과를 불러오는 중입니다.",
                    modifier=Modifier.testTag("balance-result"))
            }
            if(phase==GamePhase.CANCELLED)Text("게임이 취소됐습니다.")
            if(uid==ownerId&&phase!=GamePhase.FINISHED&&phase!=GamePhase.CANCELLED){
                OutlinedButton(onClick={vm.action("cancel")},
                    enabled=!state.busy&&state.pending==null,
                    modifier=Modifier.fillMaxWidth()){Text("게임 취소")}
            }
        }
    }
}
