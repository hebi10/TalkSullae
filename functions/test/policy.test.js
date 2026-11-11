const {test} = require("node:test");
const assert = require("node:assert/strict");
const {roomDraft,roomIdValue,parseJoinCode,authUid,memberName} = require("../src/policy");

test("room input limits and approval flag",()=>{
  assert.deepEqual(roomDraft({name:"  모임방  ",description:"인사",requiresApproval:true}),
    {name:"모임방",description:"인사",requiresApproval:true});
  assert.throws(()=>roomDraft({name:"a",requiresApproval:true}));
  assert.throws(()=>roomDraft({name:"a".repeat(25),requiresApproval:true}));
  assert.throws(()=>roomDraft({name:"모임",description:"x".repeat(121),requiresApproval:true}));
  assert.throws(()=>roomDraft({name:"모임",requiresApproval:"true"}));
});

test("reject malformed room id and invitation tokens",()=>{
  assert.equal(roomIdValue("01234567890123456789"),"01234567890123456789");
  assert.throws(()=>roomIdValue("../other"));
  assert.throws(()=>parseJoinCode("oops"));
  assert.throws(()=>parseJoinCode("a".repeat(20)+"."+"bad"));
  assert.deepEqual(parseJoinCode("A".repeat(20)+"."+"x".repeat(43)),
    {roomId:"A".repeat(20),token:"x".repeat(43)});
});

test("only authenticated users can access callable operations",()=>{
  assert.throws(()=>authUid({}));
  assert.equal(authUid({auth:{uid:"u"}}),"u");
  assert.equal(memberName({token:{name:"  도영  "}}),"도영");
  assert.equal(memberName({token:{}}),"멤버");
});
