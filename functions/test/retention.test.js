"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {expireDueMessages}=require("../src/retention");

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
     assert.equal(n,100);
     return {async get(){
      return {empty:false,docs:[{ref:{path:name+"/due"}}]};
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
