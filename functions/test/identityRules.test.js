"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const rules=require("../src/game/identityRules");

const players=["p1","p2","p3","p4"];
const assignments={
 p1:{alias:"고양이"},p2:{alias:"토끼"},
 p3:{alias:"부엉이"},p4:{alias:"여우"}
};
const session=(phase="voting")=>({
 phase,
 participants:Object.fromEntries(players.map(uid=>[uid,{ready:true}])),
 publicState:{prompt:"첫인상?",options:["고양이","토끼","부엉이","여우"]}
});

test("secure 4-10 participant assignments are unique and only public aliases leak",()=>{
 for(const count of [4,5,7,10]){
   const ids=Array.from({length:count},(_,i)=>"player-"+i);
   const setup=rules.prepare({participants:ids},n=>n-1);
   const aliases=ids.map(uid=>setup.privateByUid[uid].alias);
   assert.equal(setup.phase,"playing");
   assert.equal(setup.durationMs,180000);
   assert.equal(new Set(aliases).size,count);
   assert.equal(setup.publicState.options.length,count);
   assert.deepEqual(new Set(setup.publicState.options),new Set(aliases));
   assert.ok(!JSON.stringify(setup.publicState).includes("player-"));
 }
 assert.throws(()=>rules.prepare({participants:["x","y","z"]}));
 assert.throws(()=>rules.prepare({participants:Array(11).fill("x")}));
});

test("public alias ordering is independent of secret participant assignments",()=>{
 const ids=Array.from({length:10},(_,i)=>"player-"+i);
 const left=rules.prepare({participants:ids},()=>0);
 const right=rules.prepare({participants:ids},n=>n-1);
 assert.deepEqual(left.publicState.options,rules.ALIASES);
 assert.deepEqual(right.publicState.options,rules.ALIASES);
 assert.notDeepEqual(ids.map(uid=>left.privateByUid[uid].alias),
   ids.map(uid=>right.privateByUid[uid].alias));
 assert.notDeepEqual(ids.map(uid=>left.privateByUid[uid].alias),
   left.publicState.options);
 for(const count of [4,5,7,10]){
   const setup=rules.prepare({participants:ids.slice(0,count)},()=>0);
   assert.deepEqual(setup.publicState.options,
     rules.ALIASES.filter(alias=>Object.values(setup.privateByUid)
       .some(secret=>secret.alias===alias)));
 }
});

test("identity matching excludes own alias and own real UID and enforces bijection",()=>{
 const valid={"토끼":"p2","부엉이":"p3","여우":"p4"};
 const saved=rules.validateSubmission({
   session:session(),actorUid:"p1",personalData:assignments.p1,choice:valid
 });
 assert.deepEqual(saved.matches,valid);
 const inputs=[
  {"고양이":"p1",...valid},
  {"토끼":"p1","부엉이":"p3","여우":"p4"},
  {"토끼":"p2","부엉이":"p2","여우":"p4"},
  {"토끼":"p2","부엉이":"p3"},
  {"토끼":"p2","부엉이":"p3","여우":"p4","extra":"p1"},
  []
 ];
 for(const choice of inputs){
   assert.throws(()=>rules.validateSubmission({
     session:session(),actorUid:"p1",personalData:assignments.p1,choice
   }));
 }
 assert.throws(()=>rules.validateSubmission({
   session:session("playing"),actorUid:"p1",personalData:assignments.p1,choice:valid
 }));
});

test("server scores correct guesses and limited disguise bonus, not client points",()=>{
 const submissions={
  p1:{matches:{"토끼":"p2","부엉이":"p3","여우":"p4"}},
  p2:{matches:{"고양이":"p3","부엉이":"p1","여우":"p4"}},
  p3:{matches:{"고양이":"p1","토끼":"p2","여우":"p4"}},
  p4:{matches:{"고양이":"p1","토끼":"p2","부엉이":"p3"}}
 };
 const scores=rules.computeScores(session(),submissions,assignments);
 assert.deepEqual(scores,{p1:310,p2:100,p3:310,p4:300});
 const names={p1:"민지",p2:"지훈",p3:"해비",p4:"수빈"};
 const message=rules.publicReveal(session(),assignments,names,scores);
 assert.ok(message.includes("고양이 = 민지"));
 assert.ok(message.includes("토끼 = 지훈"));
 assert.ok(!message.includes("p1"));
 assert.ok(message.length<1000);
 assert.throws(()=>rules.computeScores(session(),submissions,{p1:assignments.p1}));
});

test("real rule progresses chat -> voting -> reveal -> one-score result",()=>{
 const input=session("playing");
 const voting=rules.advance({session:input});
 assert.equal(voting.phase,"voting");
 assert.equal(voting.durationMs,90000);
 assert.equal(voting.publicState.publishedResult,undefined);
 const reveal=rules.advance({
   session:session("voting"),
   assignments,
   submissions:{p1:{matches:{"토끼":"p2","부엉이":"p3","여우":"p4"}}},
   memberNames:{p1:"민지",p2:"지훈",p3:"해비",p4:"수빈"}
 });
 assert.equal(reveal.phase,"reveal");
 assert.equal(reveal.durationMs,10000);
 assert.ok(reveal.publicState.publishedResult.includes("고양이 = 민지"));
 const finish=rules.advance({
   session:{...session("reveal"),publicState:reveal.publicState},
   assignments,
   submissions:{p1:{matches:{"토끼":"p2","부엉이":"p3","여우":"p4"}}}
 });
 assert.equal(finish.phase,"finished");
 assert.equal(finish.scoresByUid.p1,300);
 assert.ok(finish.publicState.publishedResult);
});

test("player projection returns only personal alias, never all mappings",()=>{
 assert.deepEqual(rules.projectForPlayer({
  session:session("voting"),personalData:assignments.p1
 }),{alias:"고양이"});
 assert.equal(rules.projectForPlayer({session:session("playing"),personalData:null}),null);
 assert.deepEqual(rules.projectForPlayer({
  session:session("finished"),personalData:assignments.p1
 }),{});
});
