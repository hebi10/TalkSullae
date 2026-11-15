"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {aggregateMyCareer}=require("../src/game/careerService");

test("per-room totals aggregate without double-counting achievement flags",()=>{
  const result=aggregateMyCareer([
    {totalGames:2,totalWins:1,totalSeasonPoints:120,bestStreak:1,
      games:{identity:{plays:2,wins:1}},achievements:{first_game:true}},
    {totalGames:3,totalWins:2,totalSeasonPoints:180,bestStreak:2,
      games:{liar:{plays:3,wins:2}},achievements:{first_game:true,wins_5:true}}
  ]);
  assert.equal(result.roomCount,2);
  assert.equal(result.totalGames,5);
  assert.equal(result.totalWins,3);
  assert.equal(result.totalSeasonPoints,300);
  assert.equal(result.bestStreak,2);
  assert.equal(result.games.identity.plays,2);
  assert.equal(result.games.liar.wins,2);
  assert.deepEqual(result.achievements,["first_game","wins_5"]);
});

test("malformed and negative counters are rejected",()=>{
  assert.throws(()=>aggregateMyCareer([{totalGames:-1}]));
  assert.throws(()=>aggregateMyCareer([{games:{balance:{plays:1.5}}}]));
});
