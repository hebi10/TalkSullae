"use strict";

const {Timestamp}=require("firebase-admin/firestore");

const REPORT_SLA_MS=24*60*60*1000;
const MAX_SCAN=50;

/** Counts only; never writes UIDs, text, tokens or secret game state to logs. */
async function inspectSafetyBacklog(db,now=Date.now){
  const [incoming,manual,attention]=await Promise.all([
    db.collection("safetyReports").where("status","in",["received","under_review"]).limit(MAX_SCAN+1).get(),
    db.collection("accountDeletionJobs").where("status","==","manual-review")
      .limit(MAX_SCAN+1).get(),
    db.collection("accountDeletionJobs").where("status","==","needs-attention")
      .limit(MAX_SCAN+1).get()
  ]);
  const old=incoming.docs.filter(row=>{
    const created=row.get("createdAt");
    return created instanceof Timestamp && created.toMillis()<=now()-REPORT_SLA_MS;
  }).length;
  return {
    reportsOver24Hours:old,
    reportsChecked:Math.min(incoming.size,MAX_SCAN),
    deletionManualReview:Math.min(manual.size,MAX_SCAN),
    deletionNeedsAttention:Math.min(attention.size,MAX_SCAN),
    scanCapReached:[incoming,manual,attention].some(z=>z.size>MAX_SCAN)
  };
}

module.exports={inspectSafetyBacklog,REPORT_SLA_MS,MAX_SCAN};
