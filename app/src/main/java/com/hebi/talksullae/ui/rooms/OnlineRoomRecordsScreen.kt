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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    val error:String? = null
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
    DisposableEffect(uid,roomId,season){
        val db=FirebaseFirestore.getInstance()
        val room=db.collection("rooms").document(roomId)
        val registrations=listOf(
            room.collection("seasons").document(season).collection("scores")
                .orderBy("seasonPoints",Query.Direction.DESCENDING).limit(20L)
                .addSnapshotListener{snapshot,error->
                    if(error!=null)state=state.copy(
                        loading=false,error="시즌 순위를 불러올 수 없습니다.")
                    else if(snapshot!=null)state=state.copy(
                        ranking=snapshot.documents.map{
                            SeasonRank(it.id,it.getLong("seasonPoints")?:0L,
                                it.getLong("gamesPlayed")?:0L,it.getLong("wins")?:0L)
                        },loading=false
                    )
                },
            room.collection("playerStats").document(uid)
                .addSnapshotListener{snap,error->
                    if(error!=null)state=state.copy(
                        loading=false,error="나의 기록을 불러올 수 없습니다.")
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
                .addSnapshotListener{snapshot,error->
                    if(error!=null)state=state.copy(error="최근 전적을 조회할 수 없습니다.")
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
                .addSnapshotListener{snapshot,error->
                    if(error!=null)state=state.copy(error="방 연대기를 확인할 수 없습니다.")
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
        onBack=onBack
    )
}

@Composable
internal fun RoomRecordsContent(
    roomName:String,state:RoomRecordsState,
    names:Map<String,String>,
    onPrevious:()->Unit,onNext:()->Unit,
    canPrevious:Boolean,canNext:Boolean,onBack:()->Unit
) {
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
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedButton(onClick=onPrevious,enabled=canPrevious,
                modifier=Modifier.testTag("records-previous-season")){
                Text("이전 시즌")
            }
            Text(state.seasonId+" · "+(if(state.closed)"종료" else "진행 중"),
                modifier=Modifier.weight(1f))
            OutlinedButton(onClick=onNext,enabled=canNext,
                modifier=Modifier.testTag("records-next-season")){
                Text("다음 시즌")
            }
        }
        Text("월간 시즌 순위",style=MaterialTheme.typography.titleMedium)
        if(state.loading)CircularProgressIndicator()
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!state.loading&&state.ranking.isEmpty())Text("해당 시즌의 경기 기록이 없습니다.")
        state.ranking.forEachIndexed{index,item->
            Row(Modifier.fillMaxWidth().testTag("records-ranked-"+item.uid)){
                Text("${index+1}위 · "+(names[item.uid]?:"탈퇴한 멤버"),
                    modifier=Modifier.weight(1f))
                Text("${item.points}점 · ${item.games}경기 · ${item.wins}승")
            }
        }
        Text("서로 다른 게임의 원점수는 합산하지 않고 공통 시즌 점수로 환산합니다.",
            style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("나의 최근 경기",style=MaterialTheme.typography.titleMedium)
        if(state.recentGames.isEmpty())Text("아직 정산된 경기가 없습니다.")
        state.recentGames.forEach{item->
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
