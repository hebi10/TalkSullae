"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const {validateProfile,memberDisplayName,storedAvatar,profileAvatar}=require("../src/userProfile");

test("nickname is normalized and avatar is restricted",()=>{
  assert.deepEqual(validateProfile({nickname:"  해비  ",avatar:"고양이"}),
    {nickname:"해비",avatar:"고양이"});
  assert.equal(memberDisplayName({exists:true,get:()=>"해비"},{}),"해비");
  assert.equal(memberDisplayName({exists:false},{token:{name:"기존이름"}}),"기존이름");
});

test("rejects invalid profile values and untrusted fields",()=>{
  for(const data of [
    {},{nickname:"가",avatar:"토끼"},
    {nickname:"가".repeat(17),avatar:"토끼"},
    {nickname:"가\n나",avatar:"부엉이"},
    {nickname:"좋은이름",avatar:"관리자"},
    {nickname:"닉네임",avatar:"고양이",uid:"foreign"},
    {nickname:17,avatar:"토끼"},
    {nickname:"   ",avatar:"토끼"}
  ]){
    assert.throws(()=>validateProfile(data));
  }
});

test("all eight profile avatars remain valid while unknown values are rejected",()=>{
  for(const avatar of ["부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄"]){
    assert.equal(validateProfile({nickname:"테스트",avatar}).avatar,avatar);
  }
  assert.throws(()=>validateProfile({nickname:"테스트",avatar:"기타"}));
});

test("extended avatars preserve a legacy-compatible profile field",()=>{
 assert.deepEqual(storedAvatar("펭귄"),{avatar:"부엉이",avatarV2:"펭귄"});
 assert.deepEqual(storedAvatar("토끼"),{avatar:"토끼",avatarV2:"토끼"});
 const snap=values=>({exists:true,get:key=>values[key]});
 assert.equal(profileAvatar(snap({avatar:"부엉이",avatarV2:"펭귄"})),"펭귄");
 assert.equal(profileAvatar(snap({avatar:"여우"})),"여우");
 assert.equal(profileAvatar(snap({avatar:"알 수 없음"})),"부엉이");
});
