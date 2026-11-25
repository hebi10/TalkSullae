package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import com.hebi.talksullae.firebase.FirebaseFriendRecordsRepository
import com.hebi.talksullae.firebase.FriendRecords
import kotlinx.coroutines.CancellationException
import com.hebi.talksullae.firebase.CloudMember
import java.time.YearMonth
import java.time.ZoneId

internal data class SeasonRank(
    val uid:String,val points:Long,val games:Long,val wins:Long
)
internal data class RecentGameRecord(
    val gameId:String,val points:Long,val win:Boolean,val seasonId:String
)
internal data class RoomRecordEvent(
    val gameId:String,val seasonId:String,val participants:Long
)
internal data class SeasonFinalResult(val winningScore:Long?,val winnerCount:Long,val gameCount:Long)

internal data class RoomRecordsState(
    val seasonId:String,
    val closed:Boolean,
    val ranking:List<SeasonRank> = emptyList(),
    val totalGames:Long = 0,
    val totalWins:Long = 0,
    val bestStreak:Long = 0,
    val achievements:Set<String> = emptySet(),
    val recentGames:List<RecentGameRecord> = emptyList(),
    val events:List<RoomRecordEvent> = emptyList(),
    val loading:Boolean = true,
    val error:String? = null,
    val finalResult:SeasonFinalResult? = null,
    val finalStatus:String? = null
)
private val achievementLabels=mapOf(
    "first_game" to "첫 게임 완료",
    "games_10" to "10경기 참가",
    "wins_5" to "5승 달성",
    "streak_3" to "3연승",
    "streak_5" to "5연승",
    "liar_wins_5" to "라이어 5승",
    "identity_wins_10" to "정체 추리 10승"
)
private val gameLabels=mapOf(
    "identity" to "너 누구야?",
    "liar" to "라이어 채팅",
    "forbidden" to "금지어 채팅",
    "balance" to "밸런스 예측"
)

@Composable
internal fun OnlineRoomRecordsHost(
    uid:String,roomId:String,roomName:String,
    members:List<CloudMember>,onBack:()->Unit
) {
    var offset by remember(roomId){mutableIntStateOf(0)}
    val zone=remember{ZoneId.of("Asia/Seoul")}
    var today by remember{mutableStateOf(YearMonth.now(zone))}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{ _, event->
            if(event==Lifecycle.Event.ON_RESUME)today=YearMonth.now(zone)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(zone){
        while(true){
            today=YearMonth.now(zone)
            delay(60_000L)
        }
    }
    val season=remember(today,offset){today.minusMonths(offset.toLong()).toString()}
    var state by remember(roomId,uid,season){
        mutableStateOf(RoomRecordsState(season,offset>0))
    }
    var selectedFriend by remember(uid,roomId,season){mutableStateOf<String?>(null)}
    var friendValue by remember(uid,roomId,season,selectedFriend){mutableStateOf<FriendRecords?>(null)}
    var friendLoading by remember(uid,roomId,season,selectedFriend){mutableStateOf(false)}
    var friendError by remember(uid,roomId,season,selectedFriend){mutableStateOf<String?>(null)}
    var friendRetry by remember(uid,roomId,season,selectedFriend){mutableIntStateOf(0)}
    var includeComparison by remember(uid,roomId,season,selectedFriend){mutableStateOf(false)}
    var closureRetry by remember(uid,roomId,season){mutableIntStateOf(0)}
    var foreground by remember(lifecycle){mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle,uid,roomId){
        val observer=LifecycleEventObserver { _, _ ->
            foreground=lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(!foreground){selectedFriend=null;friendValue=null}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer)}
    }
    val activeFriend=selectedFriend?.let { id -> members.firstOrNull { it.uid==id && it.uid!=uid && it.status=="active" } }
    LaunchedEffect(uid,roomId,season,activeFriend?.uid,friendRetry,foreground,includeComparison){
        friendValue=null
        friendError=null
        val friend=activeFriend ?: return@LaunchedEffect
        if(!foreground)return@LaunchedEffect
        friendLoading=true
        try {
            friendValue=FirebaseFriendRecordsRepository().get(roomId,friend.uid,includeComparison)
        } catch(cancelled:CancellationException){
            throw cancelled
        } catch(_:Exception){
            friendError="친구의 전적을 확인하지 못했습니다. 방 접근 권한과 연결 상태를 확인해 주세요."
        } finally {
            friendLoading=false
        }
    }
    LaunchedEffect(uid,roomId,season,foreground,state.closed,state.loading,state.error,closureRetry){
        if(!foreground||!state.closed||state.loading||state.error!=null)return@LaunchedEffect
        try {
            val value=FirebaseFunctions.getInstance("asia-northeast3").getHttpsCallable("getRoomSeasonClosure")
                .call(mapOf("roomId" to roomId,"seasonId" to season)).await().data as? Map<*,*>
            if(value?.get("status")=="closing")state=state.copy(finalStatus="마감 처리 중입니다. 잠시 후 다시 확인해 주세요.")
            else if(value?.get("status")=="empty")state=state.copy(finalStatus="종료된 시즌 기록이 없습니다.")
            else if(value?.get("status")!="closed")state=state.copy(finalStatus="서버의 시즌 마감 상태를 다시 확인해 주세요.")
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){state=state.copy(finalStatus="시즌 마감 결과를 확인할 수 없습니다.")}
    }
    DisposableEffect(uid,roomId,season){
        val db=FirebaseFirestore.getInstance()
        val room=db.collection("rooms").document(roomId)
        val registrations=listOf(
            room.collection("seasons").document(season)
                .addSnapshotListener(MetadataChanges.INCLUDE){doc,error->
                    val result=if(error==null&&doc!=null&&!doc.metadata.isFromCache&&doc.exists())doc.get("finalResult") as? Map<*,*> else null
                    val count=(result?.get("winnerCount") as? Number)?.toLong()
                    val score=(result?.get("winningScore") as? Number)?.toLong()
                    val games=(result?.get("gameCount") as? Number)?.toLong()
                    val final=if(result?.get("status")=="closed"&&count!=null&&count>=0&&games!=null&&games>=0&&
                        ((count==0L&&score==null)||(count>0L&&score!=null&&score>0L)))
                        SeasonFinalResult(score,count,games) else null
                    state=state.copy(finalResult=final,finalStatus=if(final!=null)null else state.finalStatus)
                },
            room.collection("seasons").document(season).collection("scores")
                .orderBy("seasonPoints",Query.Direction.DESCENDING).limit(20L)
                .addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
                    if(error!=null){
                        selectedFriend=null
                        state=state.copy(ranking=emptyList(),loading=false,error="시즌 순위를 불러올 수 없습니다.")
                    }
                    else if(snapshot?.metadata?.isFromCache==true){
                        selectedFriend=null
                        state=state.copy(ranking=emptyList(),loading=true)
                    }
                    else if(snapshot!=null)state=state.copy(
                        ranking=snapshot.documents.map{
                            SeasonRank(it.id,it.getLong("seasonPoints")?:0L,
                                it.getLong("gamesPlayed")?:0L,it.getLong("wins")?:0L)
                        },loading=false
                    )
                },
            room.collection("playerStats").document(uid)
                .addSnapshotListener(MetadataChanges.INCLUDE){snap,error->
                    if(error!=null)state=state.copy(
                        totalGames=0,totalWins=0,bestStreak=0,achievements=emptySet(),
                        loading=false,error="나의 기록을 불러올 수 없습니다.")
                    else if(snap?.metadata?.isFromCache==true)state=state.copy(
                        totalGames=0,totalWins=0,bestStreak=0,achievements=emptySet())
                    else if(snap!=null){
                        val achievements=(snap.get("achievements") as? Map<*, *>)
                            ?.filter{it.value==true}?.keys?.mapNotNull{it as? String}
                            ?.toSet().orEmpty()
                        state=state.copy(
                            totalGames=snap.getLong("totalGames")?:0L,
                            totalWins=snap.getLong("totalWins")?:0L,
                            bestStreak=snap.getLong("bestStreak")?:0L,
                            achievements=achievements
                        )
                    }
                },
            room.collection("playerStats").document(uid).collection("games")
                .orderBy("settledAt",Query.Direction.DESCENDING).limit(30L)
                .addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
                    if(error!=null)state=state.copy(recentGames=emptyList(),error="최근 전적을 조회할 수 없습니다.")
                    else if(snapshot?.metadata?.isFromCache==true)state=state.copy(recentGames=emptyList())
                    else if(snapshot!=null)state=state.copy(
                        recentGames=snapshot.documents.map{
                            RecentGameRecord(it.getString("gameId").orEmpty(),
                                it.getLong("seasonPoints")?:0L,
                                it.getBoolean("win")?:false,
                                it.getString("seasonId").orEmpty())
                        }
                    )
                },
            room.collection("recordEvents")
                .orderBy("settledAt",Query.Direction.DESCENDING).limit(20L)
                .addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
                    if(error!=null)state=state.copy(events=emptyList(),error="방 연대기를 확인할 수 없습니다.")
                    else if(snapshot?.metadata?.isFromCache==true)state=state.copy(events=emptyList())
                    else if(snapshot!=null)state=state.copy(
                        events=snapshot.documents.map{
                            RoomRecordEvent(it.getString("gameId").orEmpty(),
                                it.getString("seasonId").orEmpty(),
                                it.getLong("participantCount")?:0L)
                        }
                    )
                }
        )
        onDispose{registrations.forEach{it.remove()}}
    }
    RoomRecordsContent(
        roomName=roomName,state=state,
        names=members.associate{it.uid to it.name},
        onPrevious={if(offset<24)offset++},
        onNext={if(offset>0)offset--},
        canPrevious=offset<24,
        canNext=offset>0,
        onBack=onBack,
        friendNames=members.filter { it.uid!=uid && it.status=="active" }.associate { it.uid to it.name },
        onFriendClick={ selectedFriend=it },
        onClosureRetry={closureRetry++}
    )
    if(activeFriend!=null && foreground){
        FriendRecordsDialog(activeFriend.name,friendValue,friendLoading,friendError,
            onRetry={friendRetry++},onDismiss={selectedFriend=null},onCompare={includeComparison=true})
    }
}

@Composable
internal fun RoomRecordsContent(
    roomName:String,state:RoomRecordsState,
    names:Map<String,String>,
    onPrevious:()->Unit,onNext:()->Unit,
    canPrevious:Boolean,canNext:Boolean,onBack:()->Unit,
    friendNames:Map<String,String> = emptyMap(),onFriendClick:(String)->Unit = {},
    onClosureRetry:()->Unit={}
) {
    var selectedGame by rememberSaveable(roomName) { mutableStateOf("all") }
    var filterExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal=16.dp,vertical=12.dp).testTag("room-records-screen"),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        Row(Modifier.fillMaxWidth()){
            Text("방 기록 · $roomName",Modifier.weight(1f),
                style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onBack){Text("방 관리")}
        }
        HorizontalDivider()
        Text("나의 누적 전적",style=MaterialTheme.typography.titleMedium)
        Text("경기 ${state.totalGames}회 · 승리 ${state.totalWins}회 · 최고 연승 ${state.bestStreak}회",
            modifier=Modifier.testTag("room-records-summary"))
        Text("획득 업적",style=MaterialTheme.typography.titleMedium)
        if(state.achievements.isEmpty())Text("아직 획득한 업적이 없습니다.")
        else state.achievements.sorted().forEach{id->
            Text(achievementLabels[id]?:id)
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                state.seasonId + " · " + (if (state.closed) "종료" else "진행 중"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("records-season-heading")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrevious, enabled = canPrevious,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("records-previous-season")
                ) { Text("이전 시즌") }
                OutlinedButton(
                    onClick = onNext, enabled = canNext,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("records-next-season")
                ) { Text("다음 시즌") }
            }
        }
        if(state.closed && !state.loading && state.error==null){
            val final=state.finalResult
            if(final!=null){
                Text("시즌 최종 결과",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("season-final"))
                if(final.winnerCount==0L)Text("기록된 우승자가 없습니다.")
                else {
                    Text("우승 점수 ${final.winningScore}점 · 우승자 ${final.winnerCount}명")
                    Text("계정 삭제로 사라진 기록은 표시하지 않으며 차순위가 우승자로 변경되지는 않습니다.",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text("마감 시 누적 경기 ${final.gameCount}회",style=MaterialTheme.typography.bodySmall)
            }else{
                Text(state.finalStatus?:"서버의 시즌 마감 결과를 확인 중입니다.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=onClosureRetry,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                    Text("마감 결과 다시 조회")
                }
            }
        }
        Text(if(state.closed)"보존된 시즌 순위 (상위 20명)" else "월간 시즌 순위",style=MaterialTheme.typography.titleMedium)
        if(state.loading)CircularProgressIndicator()
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!state.loading&&state.ranking.isEmpty())Text("해당 시즌의 경기 기록이 없습니다.")
        competitionRanks(state.ranking).forEach{(rank,item)->
            Column(
                modifier = Modifier.fillMaxWidth().testTag("records-ranked-" + item.uid),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val champion=state.closed && state.finalResult?.winningScore==item.points
                Text(if(champion)"우승 · "+(names[item.uid] ?: "탈퇴한 멤버")
                    else "${rank}위 · " + (names[item.uid] ?: "탈퇴한 멤버"),
                    style = MaterialTheme.typography.bodyLarge)
                Text("${item.points}점 · ${item.games}경기 · ${item.wins}승",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("서로 다른 게임의 원점수는 합산하지 않고 공통 시즌 점수로 환산합니다.",
            style=MaterialTheme.typography.bodySmall)
        if(friendNames.isNotEmpty()){
            HorizontalDivider()
            Text("친구와의 전적",style=MaterialTheme.typography.titleMedium)
            Text("같은 방의 누적 전적과 함께한 최근 경기를 확인합니다.",style=MaterialTheme.typography.bodySmall)
            friendNames.entries.sortedBy { it.value }.forEach { (id,name) ->
                OutlinedButton(onClick={onFriendClick(id)},enabled=!state.loading && state.error==null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("friend-records-open-"+id)){
                    Text("$name 전적 보기")
                }
            }
        }
        HorizontalDivider()
        Text("나의 최근 경기",style=MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick={filterExpanded=true},modifier=Modifier.fillMaxWidth().testTag("records-game-filter")){
                Text(if(selectedGame=="all")"모든 게임" else gameLabels[selectedGame].orEmpty())
            }
            DropdownMenu(expanded=filterExpanded,onDismissRequest={filterExpanded=false}){
                (listOf("all" to "모든 게임")+gameLabels.toList()).forEach{(id,label)->
                    DropdownMenuItem(text={Text(label)},onClick={
                        selectedGame=id
                        filterExpanded=false
                    },modifier=Modifier.testTag("records-filter-$id"))
                }
            }
        }
        val recent=state.recentGames.filter{selectedGame=="all"||it.gameId==selectedGame}
        if(recent.isEmpty())Text(if(selectedGame=="all")"아직 정산된 경기가 없습니다." else "선택한 게임의 최근 기록이 없습니다.")
        recent.forEach{item->
            Text((gameLabels[item.gameId]?:item.gameId)+
                " · "+item.seasonId+
                " · "+(if(item.win)"승리" else "패배/기권")+
                " · "+item.points+" 시즌점")
        }
        HorizontalDivider()
        Text("방 연대기",style=MaterialTheme.typography.titleMedium)
        if(state.events.isEmpty())Text("기록된 이벤트가 없습니다.")
        state.events.forEach{event->
            Text(event.seasonId+" · "+(gameLabels[event.gameId]?:event.gameId)+
                " · "+event.participants+"명")
        }
    }
}

internal fun competitionRanks(items: List<SeasonRank>): List<Pair<Int, SeasonRank>> {
    val sorted = items.sortedWith(compareByDescending<SeasonRank> { it.points }.thenBy { it.uid })
    var rank = 0
    var previous: Long? = null
    return sorted.mapIndexed { index, item ->
        if (item.points != previous) rank = index + 1
        previous = item.points
        rank to item
    }
}
