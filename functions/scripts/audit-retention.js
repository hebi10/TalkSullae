"use strict";
// Read-only maintenance tool. Never read chat text or print document paths.
const {initializeApp,applicationDefault,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {scanRetention}=require("../src/retentionAudit");

async function main(){
  const args=process.argv.slice(2);
  if(args[0]!=="--project" || args[1]!=="talksullae" ||
     (args.length!==2 && (args.length!==4 || args[2]!=="--max-documents")) ||
     process.env.FIRESTORE_EMULATOR_HOST)throw new Error("Expected explicit talksullae production project");
  const maxDocuments=args.length===4?Number(args[3]):2000;
  if(!Number.isSafeInteger(maxDocuments)||maxDocuments<1||maxDocuments>100000)throw new Error("Invalid audit document limit");
  const app=initializeApp({projectId:"talksullae",credential:applicationDefault()});
  try{
    const report=await scanRetention(getFirestore(app),{maxDocuments});
    console.log(JSON.stringify({project:"talksullae",readOnly:true,...report},null,2));
  }finally{await deleteApp(app);}
}
main().catch(()=>{console.error("Retention audit failed. Check approved read-only access and query indexes.");process.exitCode=1;});
