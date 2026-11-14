"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const balance=require("../src/game/balanceRules");
const players=["owner","friend","other"];
const session=(phase="playing",mode="realtime")=>({
 phase,mode,round:1,participants:Object.fromEntries(players.map(uid=>[uid,{ready:true}])),
 publicState:{notice:phase==="playing"?"choose":"predict",options:["평생 여름","평생 겨울"]}
});
test("choice question supports 2-10 players, realtime/async durations and no secret keys",()=>{
 for(const n of [2,3,6,10]){
   const ids=Array.from({length:n},(_,i)=>"p"+i);
   for(const mode of ["realtime","async"]){
     const s=balance.prepare({session:{mode},participants:ids},max=>max-1);
     assert.equal(s.phase,"playing");
     assert.equal(s.durationMs,mode==="realtime"?90000:86400000);
     assert.equal(s.publicState.options.length,2);
     assert.deepEqual(s.privateByUid,{});
   }
 }
 assert.throws(()=>balance.prepare({session:{mode:"realtime"},participants:["p"]}));
});
test("own A/B and private prediction are strictly validated",()=>{
 assert.deepEqual(balance.validateSubmission({session:session(),actorUid:"owner",choice:"A"}),{ownChoice:"A"});
 for(const wrong of ["C",{choice:"A"},null])
   assert.throws(()=>balance.validateSubmission({session:session(),actorUid:"owner",choice:wrong}));
 const v=session("voting");
 assert.deepEqual(balance.validateSubmission({session:v,actorUid:"owner",
   choice:{targetUid:"friend",prediction:"B"}}),{targetUid:"friend",prediction:"B"});
 for(const wrong of [
   {targetUid:"owner",prediction:"A"},{targetUid:"ghost",prediction:"A"},
   {targetUid:"friend",prediction:"C"},{targetUid:"friend",prediction:"A",points:999}
 ])assert.throws(()=>balance.validateSubmission({session:v,actorUid:"owner",choice:wrong}));
});
test("predict scores derived only from other player's private choice",()=>{
 const pub={playing:{
   owner:{ownChoice:"A"},friend:{ownChoice:"B"},other:{ownChoice:"A"}
 },voting:{
   owner:{targetUid:"friend",prediction:"B"},
   friend:{targetUid:"other",prediction:"A"},
   other:{targetUid:"friend",prediction:"A"}
 }};
 const vote=balance.advance({session:session(),submissionsByPhase:{playing:pub.playing}});
 assert.equal(vote.phase,"voting");
 assert.equal(vote.publicState.publishedResult,undefined);
 const reveal=balance.advance({session:session("voting"),submissionsByPhase:pub});
 assert.equal(reveal.phase,"reveal");
 assert.ok(reveal.publicState.publishedResult.includes("A: 평생 여름 (2명)"));
 const result=balance.advance({session:{...session("reveal"),
   publicState:reveal.publicState},submissionsByPhase:pub});
 assert.deepEqual(result.scoresByUid,{owner:120,friend:120,other:20});
 assert.equal(result.phase,"finished");
});
test("missing responses do not create points; result is bounded and ignores forgery",()=>{
 const output=balance.calculate(session("voting"),{
  playing:{owner:{ownChoice:"B"}},voting:{friend:{targetUid:"owner",prediction:"B"}}
 });
 assert.deepEqual(output.scoresByUid,{owner:20,friend:100,other:0});
 assert.throws(()=>balance.calculate(session("voting"),{
  playing:{owner:{ownChoice:"C"}},voting:{}
 }));
 assert.throws(()=>balance.calculate(session("voting"),{
  playing:{},voting:{owner:{targetUid:"owner",prediction:"B"}}
 }));
});
