"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const rules=require("../src/game/forbiddenRules");

const ids=["alice","bob","carol"];
const session=(phase="playing",eliminatedUids=[])=>({
  phase,participants:Object.fromEntries(ids.map(uid=>[uid,{ready:true}])),
  publicState:{prompt:"금지어를 피하세요.",eliminatedUids}
});
const words={
  alice:{forbiddenWord:"치킨"},
  bob:{forbiddenWord:"커피"},
  carol:{forbiddenWord:"사진"}
};

test("server chooses unique forbidden words for 3-10 players, no public assignment",()=>{
  for(const count of [3,4,7,10]){
    const participants=Array.from({length:count},(_,i)=>"player-"+i);
    const setup=rules.prepare({participants},n=>n-1);
    assert.equal(setup.phase,"playing");
    assert.equal(setup.durationMs,180000);
    assert.equal(Object.keys(setup.privateByUid).length,count);
    assert.equal(new Set(Object.values(setup.privateByUid).map(v=>v.forbiddenWord)).size,count);
    assert.equal(setup.publicState.eliminatedUids.length,0);
    assert.equal(JSON.stringify(setup.publicState).includes("forbiddenWord"),false);
  }
  assert.throws(()=>rules.prepare({participants:["a","b"]}));
  assert.throws(()=>rules.prepare({participants:Array(11).fill("a")}));
});
test("each player sees other players' words but never their own",()=>{
  for(const player of ids){
    const result=rules.projectForPlayer({
      session:session(),actorUid:player,assignments:words
    });
    assert.equal(result.otherWords.length,2);
    assert.equal(result.otherWords.some(x=>x.uid===player),false);
    assert.equal(result.otherWords.some(x=>x.word===words[player].forbiddenWord),false);
  }
  assert.equal(rules.projectForPlayer({
    session:session("ready"),actorUid:"alice"
  }),null);
  assert.throws(()=>rules.projectForPlayer({
    session:session(),actorUid:"outsider",assignments:words
  }));
});
test("normalized phrase detects forbidden words across spaces/punctuation",()=>{
  assert.equal(rules.isForbidden("오늘 치킨 먹자","치킨"),true);
  assert.equal(rules.isForbidden("치-킨","치킨"),true);
  assert.equal(rules.isForbidden(" 치  킨 ","치킨"),true);
  assert.equal(rules.isForbidden("오늘은 카페","치킨"),false);
  assert.equal(rules.isForbidden("","치킨"),false);
});
test("server status allows no forged duplicated eliminations and scores only survival",()=>{
  assert.deepEqual(rules.score(session("playing",["alice"])),{
    alice:0,bob:100,carol:100
  });
  assert.throws(()=>rules.score(session("playing",["alice","alice"])));
  assert.throws(()=>rules.score(session("playing",["outsider"])));
  assert.throws(()=>rules.inspectAssignments(session(),{
    ...words,carol:{forbiddenWord:"치킨"}
  }));
});
test("chat deadline reveals survival, results are settled only once by engine",()=>{
  const started=rules.advance({session:session(),assignments:words});
  assert.equal(started.phase,"reveal");
  assert.equal(started.durationMs,10000);
  assert.ok(started.publicState.publishedResult.includes("생존"));
  const finish=rules.advance({
    session:{...session("reveal",["alice"]),publicState:{
      ...session("reveal",["alice"]).publicState,
      publishedResult:"게임 결과"
    }},assignments:words
  });
  assert.equal(finish.phase,"finished");
  assert.deepEqual(finish.scoresByUid,{alice:0,bob:100,carol:100});
  assert.throws(()=>rules.validateSubmission());
});
