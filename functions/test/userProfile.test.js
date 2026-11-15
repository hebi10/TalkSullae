"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const {validateProfile,memberDisplayName}=require("../src/userProfile");

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
