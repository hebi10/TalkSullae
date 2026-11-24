"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {Timestamp}=require("firebase-admin/firestore");
const {expireDueMessages,expireRoomOperationLimits}=require("../src/retention");

test("message cleanup targets only due indexed documents, not other collections",async()=>{
 const seen=[],deleted=[];
 const db={
  collectionGroup(name){
   seen.push(name);
   return {where(field,operator,value){
    assert.equal(field,"expiresAt");
    assert.equal(operator,"<=");
    assert.equal(value.toMillis(),1762935600000);
    return {limit(n){
     assert.equal(n,200);
     return {async get(){
      return {empty:false,size:1,docs:[{ref:{path:name+"/due"}}]};
     }};
    }};
   }};
  },
  batch(){return{
   delete(ref){deleted.push(ref.path);},
   async commit(){}
  };}
 };
 const result=await expireDueMessages(db,()=>1762935600000);
 assert.deepEqual(seen,["messages","publicMessages"]);
 assert.deepEqual(deleted,["messages/due","publicMessages/due"]);
 assert.deepEqual(result,{roomChat:1,gameChat:1});
});

test("expired room operation limits are deleted but concurrently renewed limits are retained",async()=>{
 const cutoff=1762935600000;
 const refs=[{path:"roomOperationLimits/expired"},{path:"roomOperationLimits/renewed"}];
 const deleted=[];
 const db={
  collection(name){
   assert.equal(name,"roomOperationLimits");
   return {where(field,op,value){
    assert.equal(field,"expiresAt");assert.equal(op,"<=");assert.equal(value.toMillis(),cutoff);
    return {limit(n){assert.equal(n,100);return {async get(){return {empty:false,size:2,docs:refs.map(ref=>({ref}))};}};}};
   }};
  },
  async runTransaction(fn){
   return fn({
    async get(ref){return {exists:true,ref,get:()=>Timestamp.fromMillis(ref===refs[0]?cutoff-1:cutoff+1000)};},
    delete(ref){deleted.push(ref.path);}
   });
  }
 };
 assert.equal(await expireRoomOperationLimits(db,()=>cutoff),1);
 assert.deepEqual(deleted,["roomOperationLimits/expired"]);
});
