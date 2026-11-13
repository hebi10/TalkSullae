"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const rules=require("../src/game/liarRules");

const ids=["owner","friend1","friend2","friend3"];
const secret={
  owner:{role:"citizen",topic:"커피"},
  friend1:{role:"citizen",topic:"커피"},
  friend2:{role:"citizen",topic:"커피"},
  friend3:{role:"liar"}
};
const base=(phase="voting",notice="vote",round=1,options=ids)=>({
  phase,round,
  participants:Object.fromEntries(ids.map(uid=>[uid,{ready:true}])),
  publicState:{notice,options}
});
const vote=targetUid=>({targetUid});

test("exactly one liar, uniform secret topic and no public secret for 4-10 players",()=>{
  for(const count of [4,5,7,10]){
    const players=Array.from({length:count},(_,i)=>"player"+i);
    const setup=rules.prepare({participants:players},n=>n-1);
    assert.equal(setup.phase,"playing");
    assert.equal(setup.durationMs,180000);
    assert.equal(Object.keys(setup.privateByUid).length,count);
    assert.equal(Object.values(setup.privateByUid).filter(v=>v.role==="liar").length,1);
    assert.equal(Object.values(setup.privateByUid).filter(v=>v.role==="citizen").length,count-1);
    const liar=Object.values(setup.privateByUid).find(v=>v.role==="liar");
    assert.equal(liar.topic,undefined);
    const topics=new Set(Object.values(setup.privateByUid).map(v=>v.topic).filter(Boolean));
    assert.equal(topics.size,1);
    assert.ok(!JSON.stringify(setup.publicState).includes([...topics][0]));
    assert.equal(JSON.stringify(setup.publicState).includes("player"),false);
  }
  assert.throws(()=>rules.prepare({participants:["a","b","c"]}));
  assert.throws(()=>rules.prepare({participants:Array(11).fill("a")}));
  assert.throws(()=>rules.prepare({participants:ids},()=>999));
});

test("only registered participant may vote for another and only liar may guess",()=>{
  const session=base();
  assert.deepEqual(rules.validateSubmission({session,actorUid:"owner",choice:"friend3",
    personalData:secret.owner}),vote("friend3"));
  for(const [uid,choice,data] of [
    ["owner","owner",secret.owner],["owner","invalid",secret.owner],
    ["outsider","friend3",secret.owner],["owner",{targetUid:"friend3"},secret.owner]
  ])assert.throws(()=>rules.validateSubmission({
    session,actorUid:uid,choice,personalData:data
  }));
  const guessing=base("voting","guess",2,rules.TOPICS);
  assert.throws(()=>rules.validateSubmission({
    session:guessing,actorUid:"owner",choice:"커피",personalData:secret.owner
  }),error=>error.code==="permission-denied");
  assert.deepEqual(rules.validateSubmission({
    session:guessing,actorUid:"friend3",choice:"커피",personalData:secret.friend3
  }),{topicChoice:"커피"});
  assert.throws(()=>rules.validateSubmission({
    session:guessing,actorUid:"friend3",choice:"모르는 주제",personalData:secret.friend3
  }));
});

test("caught liar gets one topic guess and correct answer wins after server reveal",()=>{
  const started=rules.advance({session:base("playing")});
  assert.equal(started.phase,"voting");
  assert.equal(started.publicState.notice,"vote");
  const history={1:{
    owner:vote("friend3"),friend1:vote("friend3"),friend2:vote("friend3"),
    friend3:vote("friend2")
  }};
  const caught=rules.advance({
    session:base(),submissionsByRound:history,assignments:secret
  });
  assert.equal(caught.phase,"voting");
  assert.equal(caught.round,2);
  assert.equal(caught.publicState.notice,"guess");
  assert.deepEqual(caught.publicState.options,rules.TOPICS);
  history[2]={friend3:{topicChoice:"커피"}};
  const guess=rules.advance({session:base("voting","guess",2,rules.TOPICS),
    submissionsByRound:history,assignments:secret,
    memberNames:{owner:"민지",friend1:"지훈",friend2:"수빈",friend3:"도윤"}});
  assert.equal(guess.phase,"reveal");
  assert.equal(guess.publicState.publishedResult.includes("도윤"),true);
  assert.equal(guess.publicState.publishedResult.includes("커피"),true);
  assert.equal(guess.publicState.publishedResult.includes("승리: 라이어"),true);
  const finish=rules.advance({session:{...base("reveal","result",2),publicState:guess.publicState},
    submissionsByRound:history,assignments:secret});
  assert.equal(finish.phase,"finished");
  assert.deepEqual(finish.scoresByUid,{owner:20,friend1:20,friend2:20,friend3:100});
});

test("caught liar fails topic guess: civilians win and correct voters get bonus",()=>{
 const history={1:{
   owner:vote("friend3"),friend1:vote("friend3"),
   friend2:vote("friend3"),friend3:vote("friend2")
 },2:{friend3:{topicChoice:"영화"}}};
 const score=rules.result(base("voting","guess",2,rules.TOPICS),history,secret,{});
 assert.equal(score.liarWon,false);
 assert.deepEqual(score.scoresByUid,{
   owner:120,friend1:120,friend2:120,friend3:0
 });
});

test("wrongly selected suspect lets liar win without topic revelation before reveal",()=>{
 const history={1:{
   owner:vote("friend2"),friend1:vote("friend2"),
   friend2:vote("friend3"),friend3:vote("friend2")
 }};
 const next=rules.advance({session:base(),submissionsByRound:history,assignments:secret});
 assert.equal(next.phase,"reveal");
 assert.equal(next.publicState.publishedResult.includes("승리: 라이어"),true);
 const finished=rules.advance({session:base("reveal","result"),submissionsByRound:history,assignments:secret});
 assert.equal(finished.scoresByUid.friend3,100);
});

test("first tie revotes once; second tie means liar survives",()=>{
 const one={1:{
   owner:vote("friend3"),friend1:vote("friend3"),
   friend2:vote("friend1"),friend3:vote("friend1")
 }};
 const re=rules.advance({session:base(),submissionsByRound:one,assignments:secret});
 assert.equal(re.phase,"voting");
 assert.equal(re.publicState.notice,"revote");
 assert.equal(re.round,2);
 assert.deepEqual(re.publicState.options,["friend1","friend3"]);
 one[2]={
   owner:vote("friend3"),friend1:vote("friend3"),
   friend2:vote("friend1"),friend3:vote("friend1")
 };
 const second=rules.advance({
   session:base("voting","revote",2,["friend1","friend3"]),
   submissionsByRound:one,assignments:secret
 });
 assert.equal(second.phase,"reveal");
 assert.equal(second.publicState.publishedResult.includes("재투표 동점"),true);
 assert.equal(second.publicState.publishedResult.includes("승리: 라이어"),true);
});

test("a revote can catch the liar; only after that may the liar guess",()=>{
 const history={1:{
   owner:vote("friend3"),friend1:vote("friend3"),
   friend2:vote("friend1"),friend3:vote("friend1")
 },2:{
   owner:vote("friend3"),friend1:vote("friend3"),
   friend2:vote("friend3"),friend3:vote("friend1")
 }};
 const next=rules.advance({
  session:base("voting","revote",2,["friend1","friend3"]),
  submissionsByRound:history,assignments:secret
 });
 assert.equal(next.publicState.notice,"guess");
 assert.equal(next.round,3);
 history[3]={friend3:{topicChoice:"여행"}};
 const result=rules.advance({session:base("voting","guess",3,rules.TOPICS),
  submissionsByRound:history,assignments:secret});
 assert.equal(result.phase,"reveal");
 assert.equal(result.publicState.publishedResult.includes("승리: 일반 참가자"),true);
});

test("only own role and citizen topic are projected; bad secrets fail closed",()=>{
 assert.deepEqual(rules.projectForPlayer({
   session:base("playing"),personalData:secret.friend3
 }),{role:"liar"});
 assert.deepEqual(rules.projectForPlayer({
   session:base("playing"),personalData:secret.owner
 }),{role:"citizen",topic:"커피"});
 assert.equal(rules.projectForPlayer({session:base("playing"),personalData:null}),null);
 assert.deepEqual(rules.projectForPlayer({
   session:base("finished"),personalData:secret.owner
 }),{});
 assert.throws(()=>rules.inspectAssignments(base(),{...secret,friend3:{role:"citizen",topic:"커피"}}));
 assert.throws(()=>rules.inspectAssignments(base(),{...secret,friend3:{role:"liar",topic:"커피"}}));
});
