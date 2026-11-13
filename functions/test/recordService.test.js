"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const record=require("../src/game/recordService");

test("monthly season IDs use Asia/Seoul day boundary",()=>{
 const before=Date.parse("2026-11-30T14:59:59Z");
 const after=Date.parse("2026-11-30T15:00:00Z");
 assert.equal(record.currentSeason(before),"2026-11");
 assert.equal(record.currentSeason(after),"2026-12");
 assert.equal(record.seasonStatus("2026-11",after),"closed");
 assert.equal(record.seasonStatus("2026-12",after),"active");
 assert.throws(()=>record.seasonStatus("2026-13",after));
 assert.throws(()=>record.seasonStatus("2027-01",after));
});
test("normalization bounds raw points and rewards rather than comparing game raw scores",()=>{
 assert.equal(record.maxRawScore("identity",4),330);
 assert.equal(record.maxRawScore("identity",10),950);
 assert.equal(record.maxRawScore("liar",4),120);
 assert.equal(record.maxRawScore("forbidden",3),100);
 assert.equal(record.maxRawScore("balance",2),120);
 assert.equal(record.normalizedPoints("forbidden",100,3,true),115);
 assert.equal(record.normalizedPoints("liar",120,4,true),115);
 assert.equal(record.normalizedPoints("balance",0,2,false),5);
 assert.equal(record.normalizedPoints("identity",330,4,true),115);
 assert.throws(()=>record.normalizedPoints("balance",121,2,false));
 assert.throws(()=>record.normalizedPoints("forbidden",-1,3,false));
});
test("winners are inferred from server scores, not client flags",()=>{
 assert.deepEqual(record.winnerMap("identity",{a:300,b:100,c:300},["a","b","c"]),
   {a:true,b:false,c:true});
 assert.deepEqual(record.winnerMap("liar",{a:100,b:20,c:120},["a","b","c"]),
   {a:true,b:false,c:true});
 assert.deepEqual(record.winnerMap("balance",{a:20,b:120},["a","b"]),
   {a:false,b:true});
});
test("earned achievements depend on stored counters and current streak",()=>{
 assert.deepEqual(record.achievementsAfter({},{
  totalGames:1,totalWins:0,currentStreak:0,bestStreak:0,liarWins:0,identityWins:0
 }),{first_game:true});
 const achievements=record.achievementsAfter({},{
  totalGames:10,totalWins:5,currentStreak:3,bestStreak:5,liarWins:5,identityWins:10
 });
 for(const id of ["first_game","games_10","wins_5","streak_3","streak_5",
   "liar_wins_5","identity_wins_10"])assert.equal(achievements[id],true);
});
