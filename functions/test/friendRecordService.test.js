"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {parseRequest,emptyComparison,countPair,countIdentity,countBalance}=require("../src/game/friendRecordService");
test("only the exact room and another valid member may be requested",()=>{
 const roomId="a".repeat(20);
 assert.deepEqual(parseRequest({roomId,targetUid:"friend"},"me"),{roomId,targetUid:"friend",includeComparison:true});
 for(const targetUid of ["me","","a/b","..","x".repeat(129)])assert.throws(()=>parseRequest({roomId,targetUid},"me"));
 assert.throws(()=>parseRequest({roomId,targetUid:"friend",uid:"other"},"me"));
 assert.throws(()=>parseRequest({roomId,targetUid:"friend",includeComparison:"false"},"me"));
 assert.equal(parseRequest({roomId,targetUid:"friend",includeComparison:false},"me").includeComparison,false);
});
test("friend comparisons are game-scoped and omit missing submissions",()=>{
 const out=emptyComparison();
 assert.equal(countPair(out,"identity",300,200),true);
 assert.equal(countPair(out,"liar",0,120),true);
 assert.equal(countPair(out,"balance",120,120),true);
 assert.equal(countPair(out,"unknown",2,1),false);
 assert.equal(countPair(out,"identity",-1,0),false);
 countIdentity(out,"me","friend","owl","cat",{cat:"friend"},{owl:"third"});
 countIdentity(out,"me","friend","owl","cat",null,null);
 countIdentity(out,"me","friend",null,"cat",{cat:"friend"},{owl:"me"});
 countBalance(out,"A","A");
 countBalance(out,"A","B");
 countBalance(out,null,"B");
 assert.deepEqual([out.sharedGames,out.myHigher,out.friendHigher,out.ties],[3,1,1,1]);
 assert.deepEqual([out.myIdentityAttempts,out.myIdentityHits,out.friendIdentityAttempts,out.friendIdentityHits],[1,1,1,0]);
 assert.deepEqual([out.balanceCompared,out.balanceMatched],[2,1]);
});
