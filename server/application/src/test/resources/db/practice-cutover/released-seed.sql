INSERT INTO workspace(id,account_login,account_type,display_name,slug,status)
VALUES(992101,'cutover','ORG','Cutover','practice-v1-cutover','ACTIVE'),(992201,'other','ORG','Other','practice-v1-other','ACTIVE');
INSERT INTO identity_provider(id,type,server_url) VALUES(992102,'GITHUB','https://practice-v1.example');
INSERT INTO "user"(id,native_id,provider_id,login,type) VALUES(992103,103,992102,'developer','USER'),(992104,104,992102,'reviewer','USER'),(992105,105,992102,'automation','BOT');
INSERT INTO account(id,display_name,status,app_role) VALUES(992106,'Signed-in developer','ACTIVE','USER');
INSERT INTO identity_link(id,account_id,provider_id,subject,linked_via) VALUES(992107,992106,992102,'103','OAUTH_LOGIN');
INSERT INTO workspace_membership(workspace_id,user_id,role,hidden,created_at) VALUES(992101,992103,'OWNER',false,'2026-01-01'),(992101,992104,'MEMBER',true,'2026-01-01'),(992201,992104,'MEMBER',false,'2026-01-01'),(992101,992105,'MEMBER',false,'2026-01-01');
INSERT INTO practice_review_person_target(workspace_id,user_id) VALUES(992101,992104);
INSERT INTO account_ai_choice(account_id,ai_choice,updated_at) VALUES(992106,'IN_HOUSE_ONLY','2026-01-01T00:00:00Z');
INSERT INTO repository(id,native_id,provider_id,name,name_with_owner,is_archived,is_disabled,is_private,has_discussions_enabled)
VALUES(992108,108,992102,'project','cutover/project',false,false,false,false);
INSERT INTO issue(id,native_id,provider_id,issue_type,number,repository_id,author_id,title,comments_count,is_locked,created_at)
VALUES(992109,109,992102,'PULL_REQUEST',1,992108,992103,'Add retry',0,false,'2026-01-01T00:00:00Z');
INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,evidence_requirements,review_when,automated_review_policy,delivery_behavior,created_at)
VALUES(992121,992101,'explains-why','Explain why','Explain the purpose of the change.','scm.pull_request','AUTHOR','["scm.pull_request.opened"]','[{"sourceKind":"scm.pull-request.core","stance":"REQUIRED"}]','{"state":["OPEN"]}',
'{"sourceContractVersion":"1.3.0","automatedReview":{"mode":"LANGUAGE_MODEL","evidenceSufficiency":"SUFFICIENT_WHEN_REQUIREMENTS_MET"},"whenEvidenceIsInsufficient":"SKIP_AUTOMATED_REVIEW","knownLimitations":[],"insufficiencyReason":null}','{"summaryOnly":true}','2026-01-01');
INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,signals,evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,created_at)
SELECT 992131,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,'2026-01-01T00:00:00Z' FROM practice WHERE id=992121;
UPDATE practice SET current_revision_id=992131 WHERE id=992121;
INSERT INTO agent_job(id,workspace_id,job_type,status,config_snapshot,job_token,retry_count,created_at,completed_at,available_at,trace_id,metadata,delivery_status)
VALUES('00000000-0000-0000-0000-000000992111',992101,'PULL_REQUEST_REVIEW','COMPLETED','{}','released-complete',0,'2026-01-01T00:00:00Z','2026-01-01T00:01:00Z','2026-01-01T00:00:00Z',repeat('a',32),'{"pull_request_id":992109,"about_user_id":992103,"observation_origin":"LIVE","commit_sha":"released-head"}','DELIVERED'),
('00000000-0000-0000-0000-000000992112',992101,'PULL_REQUEST_REVIEW','RUNNING','{}','released-running',1,'2026-01-02T00:00:00Z',NULL,'2026-01-02T00:00:00Z',repeat('b',32),'{"pull_request_id":992109,"about_user_id":992104,"subject_role":"REVIEWER","review_id":992119}','PENDING'),
('00000000-0000-0000-0000-000000992113',992101,'PULL_REQUEST_REVIEW','COMPLETED','{}','released-pending',0,'2026-01-03T00:00:00Z','2026-01-03T00:01:00Z','2026-01-03T00:00:00Z',repeat('c',32),'{"pull_request_id":992109,"about_user_id":992103}','PENDING');
INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,artifact_kind,artifact_id,about_user_id,summary,outcome,severity,evidence,evidence_rationale,observed_at)
VALUES('00000000-0000-0000-0000-000000992141','released-negative','00000000-0000-0000-0000-000000992111',992101,992121,992131,'scm.pull_request',992109,992103,'Explain the retry','NOT_MET','INFO',
'{"citations":[{"sourceKind":"scm.pull-request.core","artifactPath":"context/description.md","revision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","path":"description.md","startLine":1,"endLine":1,"quote":"Add retry"}]}','The description gives no reason.','2026-01-01T00:00:30Z');
INSERT INTO observation_invalidation(id,workspace_id,observation_id,reason,invalidated_at,invalidated_by_account_id,provider_copy,provider_copy_retry_at)
VALUES('00000000-0000-0000-0000-000000992151',992101,'00000000-0000-0000-0000-000000992141','Wrong evidence','2026-01-01T00:02:00Z',992106,'NONE','2026-01-01T00:02:00Z');
INSERT INTO feedback(id,workspace_id,agent_job_id,recipient_user_id,about_user_id,channel,position,delivery_state,source,body,created_at,delivered_at)
VALUES('00000000-0000-0000-0000-000000992161',992101,'00000000-0000-0000-0000-000000992111',992103,992103,'IN_CONTEXT',0,'DELIVERED','AGENT','Say why.','2026-01-01T00:01:00Z','2026-01-01T00:01:30Z');
INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
VALUES('00000000-0000-0000-0000-000000992161','00000000-0000-0000-0000-000000992141','PRIMARY',0);
INSERT INTO feedback_placement(id,feedback_id,placement_type,posted_comment_ref,posted_comment_url,created_at)
VALUES('00000000-0000-0000-0000-000000992171','00000000-0000-0000-0000-000000992161','SUMMARY','provider:123','https://practice-v1.example/comment/123','2026-01-01T00:01:30Z');

-- The review mirror is absent. Its event and job still identify the same parent pull request.
INSERT INTO artifact_signal(id,workspace_id,artifact_kind,artifact_id,signal_name,revision,occurred_at,
    discovered_via,state,job_id,state_changed_at,actor_user_id)
VALUES('00000000-0000-0000-0000-000000992181',992101,'scm.pull_request',992109,
    'scm.pull_request.reviewed','event~992119','2026-01-02T00:00:00Z','EVENT','TRIGGERED',
    '00000000-0000-0000-0000-000000992112','2026-01-02T00:00:00Z',992104);

INSERT INTO chat_thread(id,user_id,workspace_id,surface,created_at)
VALUES('00000000-0000-0000-0000-000000992191',992103,992101,'WEB','2026-01-01T00:03:00Z');
INSERT INTO chat_message(id,thread_id,role,status,parts,created_at)
VALUES('00000000-0000-0000-0000-000000992192','00000000-0000-0000-0000-000000992191','ASSISTANT','completed',
 '[{"type":"text","text":"Unrelated assistant text."},{"type":"data-observation","id":"00000000-0000-0000-0000-000000992195","data":{"observationId":"00000000-0000-0000-0000-000000992141","text":"Words shown for this result."}}]',
 '2026-01-01T00:03:00Z');
INSERT INTO feedback(id,workspace_id,agent_job_id,recipient_user_id,about_user_id,channel,position,delivery_state,source,created_at)
VALUES('00000000-0000-0000-0000-000000992193',992101,'00000000-0000-0000-0000-000000992111',992103,992103,'IN_CHAT',1,'UNCONFIRMED','AGENT','2026-01-01T00:03:00Z');
INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
VALUES('00000000-0000-0000-0000-000000992193','00000000-0000-0000-0000-000000992141','PRIMARY',0);
INSERT INTO feedback_placement(id,feedback_id,placement_type,chat_message_id,created_at)
VALUES('00000000-0000-0000-0000-000000992194','00000000-0000-0000-0000-000000992193','CONVERSATION_TURN',
 '00000000-0000-0000-0000-000000992192','2026-01-01T00:03:00Z');

UPDATE workspace SET practice_skip_drafts=true,practice_repository_coverage_mode='ALL_MONITORED',
    practice_person_coverage_mode='ALL_ELIGIBLE' WHERE id=992101;
INSERT INTO consent_decision(id,account_id,purpose,granted,mechanism,notice_version,notice_sha256,occurred_at)
SELECT 992251,992106,'PRIVACY_NOTICE_ACKNOWLEDGEMENT',true,'ACCOUNT_SETTINGS',version,sha256,'2026-01-01T00:00:00Z'
FROM consent_notice WHERE version='2026-08-30';

INSERT INTO feedback(id,workspace_id,agent_job_id,recipient_user_id,about_user_id,channel,position,delivery_state,source,body,created_at,delivered_at)
VALUES('00000000-0000-0000-0000-000000992231',992101,'00000000-0000-0000-0000-000000992111',992103,992103,'IN_CONTEXT',200,'DELIVERED','AGENT','Say why.','2026-01-01T00:01:00Z','2026-01-01T00:01:30Z'),
('00000000-0000-0000-0000-000000992232',992101,'00000000-0000-0000-0000-000000992111',992103,992103,'IN_CONTEXT',201,'DELIVERED','AGENT','Say why.','2026-01-01T00:01:00Z','2026-01-01T00:01:30Z');
INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
VALUES('00000000-0000-0000-0000-000000992231','00000000-0000-0000-0000-000000992141','PRIMARY',0),
('00000000-0000-0000-0000-000000992232','00000000-0000-0000-0000-000000992141','PRIMARY',0);
INSERT INTO feedback_placement(id,feedback_id,placement_type,posted_comment_ref,posted_comment_url,created_at)
VALUES('00000000-0000-0000-0000-000000992233','00000000-0000-0000-0000-000000992231','SUMMARY','provider:124','https://practice-v1.example/comment/124','2026-01-01T00:01:30Z'),
('00000000-0000-0000-0000-000000992234','00000000-0000-0000-0000-000000992232','SUMMARY','provider:125','https://practice-v1.example/comment/125','2026-01-01T00:01:30Z');
INSERT INTO feedback_dispatch(id,destination_key,workspace_id,agent_job_id,feedback_id,destination,state,body,
    practice_slugs,package_content,delivered_placements,write_started,inline_write_started,
    delivered_external_ref,next_attempt_at,attempt_count,last_error,created_at,updated_at)
SELECT d.id::uuid,d.id,992101,'00000000-0000-0000-0000-000000992111',d.feedback::uuid,'APPROVED_REVIEW_PACKAGE',d.state,'Say why.',
    '["explains-why"]',jsonb_build_object('mrNote','Say why.','summaryContributors',jsonb_build_array('released-negative'),
        'inlineMarker','<!-- hephaestus-approved-package:'||d.feedback||' -->'),'[]',true,false,
    d.external,'2026-01-01T00:01:30Z',d.attempts,d.error,'2026-01-01T00:01:00Z','2026-01-01T00:01:30Z'
FROM (VALUES
    ('00000000-0000-0000-0000-000000992241','00000000-0000-0000-0000-000000992161','PENDING','provider:123',2,NULL::text),
    ('00000000-0000-0000-0000-000000992242','00000000-0000-0000-0000-000000992231','SENT','provider:124',3,NULL::text),
    ('00000000-0000-0000-0000-000000992243','00000000-0000-0000-0000-000000992232','FAILED','provider:125',4,'transport failed')) d(id,feedback,state,external,attempts,error);
INSERT INTO feedback_approval(id,feedback_id,workspace_id,actor_account_id,decision,content_digest,decided_at)
VALUES('00000000-0000-0000-0000-000000992261','00000000-0000-0000-0000-000000992161',992101,992106,'APPROVED',repeat('a',64),'2026-01-01T00:01:00Z');
INSERT INTO feedback_withdrawal(id,feedback_id,workspace_id,reason,restoration_reason,restored_at,restored_by_account_id,withdrawn_at,withdrawn_by_account_id)
VALUES('00000000-0000-0000-0000-000000992262','00000000-0000-0000-0000-000000992161',992101,'Scope correction','Scope confirmed','2026-01-01T00:02:30Z',992106,'2026-01-01T00:02:00Z',992106);
INSERT INTO reaction(id,reactor_user_id,action,explanation,created_at,feedback_id,usefulness)
VALUES('00000000-0000-0000-0000-000000992263',992103,'ADDRESSED','Added the purpose.','2026-01-01T00:03:00Z','00000000-0000-0000-0000-000000992161','HELPFUL');
INSERT INTO delivery_policy_evaluation(id,workspace_id,agent_job_id,feedback_id,admitted_revision,evaluated_revision,resolver_version,surface,stage,allowed,decisive_reason,checks,facts,evaluated_at)
VALUES('00000000-0000-0000-0000-000000992264',992101,'00000000-0000-0000-0000-000000992111','00000000-0000-0000-0000-000000992161',3,4,'released-v1','IN_CONTEXT','EGRESS',false,'OUTSIDE_CURRENT_COVERAGE','{}','{}','2026-01-01T00:01:00Z');
INSERT INTO public_review_completion(job_id,workspace_id,accepted_at,accepted_attempt,accepted_worker_id,admission_attempt,admission_digest,contract_version,review,review_digest)
VALUES('00000000-0000-0000-0000-000000992111',992101,'2026-01-01T00:01:00Z',0,'released-worker',0,repeat('a',64),2,NULL,repeat('b',64));
INSERT INTO review_composition_basis(job_id,workspace_id,admission_attempt,admission_digest,basis_digest,basis_version,byte_size,copy_id,created_at,expires_at,payload)
VALUES('00000000-0000-0000-0000-000000992111',992101,0,repeat('a',64),repeat('b',64),1,2,'00000000-0000-0000-0000-000000992171','2026-01-01T00:00:00Z','2027-01-01T00:00:00Z','{}');
INSERT INTO repository_to_monitor(id,workspace_id,name_with_owner,generated_paths)
VALUES(992271,992101,'cutover/project','[]');
INSERT INTO practice_review_repository_target(workspace_id,repository_monitor_id,base_branches)
VALUES(992101,992271,'["main"]');
INSERT INTO review_sweep_schedule(id,workspace_id,artifact_kind,cadence,lookback_days,enabled,next_run_at,created_by_account_id,created_at,updated_at)
VALUES('00000000-0000-0000-0000-000000992272',992101,'scm.pull_request','DAILY',2,true,'2026-01-02T00:00:00Z',992106,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');
INSERT INTO review_backfill_run(id,workspace_id,artifact_kind,from_at,to_at,status,estimated_artifacts,requested_by_account_id,created_at,updated_at)
VALUES('00000000-0000-0000-0000-000000992273',992101,'scm.pull_request','2026-01-01T00:00:00Z','2026-01-02T00:00:00Z','AWAITING_CONFIRMATION',1,992106,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');

INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,evidence_requirements,
    review_when,automated_review_policy,delivery_behavior,created_at)
SELECT n,workspace_id,'send-practice-'||n,name,criteria,applies_to,subject,signals,evidence_requirements,
    review_when,automated_review_policy,delivery_behavior,created_at
FROM practice CROSS JOIN generate_series(992401,992406) n WHERE id=992121;
INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,signals,
    evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,created_at)
SELECT id+10,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,criteria,
    automated_review_policy,delivery_behavior,created_at FROM practice WHERE id BETWEEN 992401 AND 992406;
UPDATE practice SET current_revision_id=id+10 WHERE id BETWEEN 992401 AND 992406;

-- Separate unmarked results keep pending sends eligible without borrowing marked history.
INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,
    artifact_kind,artifact_id,about_user_id,summary,outcome,severity,evidence,evidence_rationale,observed_at)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,'send-case-'||n,
    '00000000-0000-0000-0000-000000992111',992101,n+100,n+110,'scm.pull_request',992109,992103,
    'Explain this change.','NOT_MET','INFO','{"citations":[]}','The purpose is missing.','2026-01-01T00:00:30Z'
FROM generate_series(992301,992306) n;
INSERT INTO feedback(id,workspace_id,agent_job_id,recipient_user_id,about_user_id,channel,position,
    delivery_state,source,body,created_at,delivered_at,suppression_reason)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,992101,'00000000-0000-0000-0000-000000992111',
    992103,992103,'IN_CONTEXT',n-992100,state,'AGENT','Explain this change.',
    '2026-01-01T00:01:00Z',CASE WHEN state IN('DELIVERED','PARTIALLY_FAILED') THEN '2026-01-01T00:01:30Z'::timestamptz END,
    CASE WHEN state='SUPPRESSED' THEN 'OUTSIDE_CURRENT_COVERAGE' END
FROM (VALUES (992311,'PREPARED'),(992312,'UNCONFIRMED'),(992313,'PREPARED'),
    (992314,'PARTIALLY_FAILED'),(992315,'DELIVERED'),(992316,'SUPPRESSED')) f(n,state);
INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,
    ('00000000-0000-0000-0000-000000'||(n-10))::uuid,'PRIMARY',0
FROM generate_series(992311,992316) n;
INSERT INTO feedback_placement(id,feedback_id,placement_type,posted_comment_ref,created_at)
SELECT ('00000000-0000-0000-0000-000000'||(n+20))::uuid,
    ('00000000-0000-0000-0000-000000'||n)::uuid,'SUMMARY',external,
    '2026-01-01T00:01:30Z'
FROM (VALUES (992312,NULL::text),(992314,'provider:updated'),(992315,'provider:correction')) c(n,external);
INSERT INTO feedback_dispatch(id,destination_key,workspace_id,agent_job_id,feedback_id,destination,state,body,
    practice_slugs,package_content,delivered_placements,write_started,inline_write_started,
    delivered_external_ref,next_attempt_at,attempt_count,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-000000'||(n+40))::uuid,'send-case-'||n,
    992101,'00000000-0000-0000-0000-000000992111',('00000000-0000-0000-0000-000000'||n)::uuid,
    'APPROVED_REVIEW_PACKAGE',state,'Explain this change.','["explains-why"]',
    jsonb_build_object('mrNote','Explain this change.','summaryContributors',jsonb_build_array('send-case-'||(n-10)),
        'inlineMarker','<!-- hephaestus-approved-package:00000000-0000-0000-0000-000000'||n||' -->'),
    '[]',n IN(992312,992314,992315),false,external,'2026-01-01T00:01:30Z',1,'2026-01-01T00:01:00Z','2026-01-01T00:01:30Z'
FROM (VALUES (992311,'PENDING',NULL::text),(992312,'PENDING',NULL::text),
    (992313,'UNCERTAIN',NULL::text),(992314,'SENT','provider:updated'),
    (992315,'SENT','provider:correction'),(992316,'PENDING',NULL::text)) d(n,state,external);
-- A confirmed summary can be corrected after a separate inline note failed before writing.
UPDATE feedback SET proposed_placements='[{"type":"INLINE","body":"Check the timeout.","path":"src/retry.ts","startLine":1,"endLine":1,"deliveryKey":"approved:00000000-0000-0000-0000-000000992314:0"}]'
WHERE id='00000000-0000-0000-0000-000000992314';
UPDATE feedback_dispatch SET
    package_content=package_content||'{"diffNotes":[{"body":"Check the timeout.","filePath":"src/retry.ts","startLine":1,"endLine":1,"deliveryKey":"approved:00000000-0000-0000-0000-000000992314:0"}]}',
    delivered_placements='[{"deliveryKey":"approved:00000000-0000-0000-0000-000000992314:0","path":"src/retry.ts","startLine":1,"endLine":1,"disposition":"FAILED","writeMayHaveStarted":false,"placement":"INLINE"}]'
WHERE feedback_id='00000000-0000-0000-0000-000000992314';
INSERT INTO observation_invalidation(id,workspace_id,observation_id,reason,invalidated_at,
    invalidated_by_account_id,provider_copy,provider_copy_retry_at)
SELECT ('00000000-0000-0000-0000-000000'||(n+60))::uuid,992101,
    ('00000000-0000-0000-0000-000000'||(n-10))::uuid,'Wrong evidence.',
    '2026-01-01T00:02:00Z',992106,correction,'2026-01-01T00:03:00Z'
FROM (VALUES (992314,'UPDATED'),(992315,'PENDING')) m(n,correction);

INSERT INTO config_audit_event(id,workspace_id,actor_kind,actor_account_id,acting_account_id,
    entity_type,entity_id,action,changed_keys,old_value,new_value,occurred_at)
VALUES(995901,992101,'SYSTEM',NULL,NULL,'REVIEW_BACKFILL_RUN','original-batch','CREATED',
        ARRAY['lookback'],'{"before":1}','{"after":2}','2026-01-01T00:00:00Z'),
      (995902,992101,'SYSTEM',NULL,NULL,'REVIEW_SWEEP_SCHEDULE','original-schedule','UPDATED',
        ARRAY['paused'],'{"before":false}','{"after":true}','2026-01-02T00:00:00Z'),
      (995903,992101,'SYSTEM',NULL,NULL,'PRACTICE_REVIEW_SETTINGS','original-settings','UPDATED',
        ARRAY['mode'],'{"before":"kept"}','{"after":"kept"}','2026-01-03T00:00:00Z'),
      (995904,NULL,'SYSTEM',NULL,NULL,'CURATED_PRACTICE','catalog-practice','UPDATED',
        ARRAY['criteria'],'{}','{}','2026-01-04T00:00:00Z'),
      (995905,NULL,'SYSTEM',NULL,NULL,'CURATED_PRACTICE_AREA','catalog-group','UPDATED',
        ARRAY['name'],'{}','{}','2026-01-05T00:00:00Z'),
      (995906,992101,'SYSTEM',NULL,NULL,'CURATED_PRACTICE_GROUP','workspace-catalog-history','UPDATED',
        ARRAY['name'],'{}','{}','2026-01-06T00:00:00Z'),
      (995907,992101,'SYSTEM',NULL,NULL,'PRACTICE_AREA','practice-group','UPDATED',
        ARRAY['name'],'{}','{}','2026-01-07T00:00:00Z');

INSERT INTO artifact_signal(id,workspace_id,artifact_kind,artifact_id,signal_name,revision,occurred_at,
    discovered_via,state,state_changed_at,last_attempted_at,actor_user_id)
VALUES('00000000-0000-0000-0000-000000992184',992101,'scm.pull_request',992109,
    'scm.pull_request.synchronized','pending-head','2026-01-02T00:00:00Z','SYNC','PENDING',
    '2026-01-02T00:01:00Z','2026-01-02T00:01:00Z',992103);

-- A response can disagree, close as inapplicable, or rate usefulness without a resolution.
INSERT INTO reaction(id,reactor_user_id,action,explanation,created_at,feedback_id,usefulness)
VALUES('00000000-0000-0000-0000-000000992281',992103,'DISPUTED','The cited revision already explains why.','2026-01-01T00:04:00Z','00000000-0000-0000-0000-000000992161','UNHELPFUL'),
('00000000-0000-0000-0000-000000992282',992103,'NOT_APPLICABLE','This change has no retry behavior.','2026-01-01T00:05:00Z','00000000-0000-0000-0000-000000992161',NULL),
('00000000-0000-0000-0000-000000992283',992103,NULL,NULL,'2026-01-01T00:06:00Z','00000000-0000-0000-0000-000000992161','HELPFUL');

-- Distinct practices keep each released outcome independent of current-claim ordering.
INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,evidence_requirements,
    review_when,automated_review_policy,delivery_behavior,created_at)
SELECT n,workspace_id,'outcome-practice-'||n,name,criteria,applies_to,subject,signals,evidence_requirements,
    review_when,automated_review_policy,delivery_behavior,created_at
FROM practice CROSS JOIN generate_series(992501,992506) n WHERE id=992121;
INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,signals,
    evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,created_at)
SELECT id+10,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,criteria,
    automated_review_policy,delivery_behavior,created_at FROM practice WHERE id BETWEEN 992501 AND 992506;
UPDATE practice SET current_revision_id=id+10 WHERE id BETWEEN 992501 AND 992506;
INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,
    artifact_kind,artifact_id,about_user_id,summary,outcome,severity,evidence,evidence_rationale,observed_at)
SELECT ('00000000-0000-0000-0000-000000'||(n+20))::uuid,'outcome-case-'||n,
    '00000000-0000-0000-0000-000000992111',992101,n,n+10,'scm.pull_request',992109,992103,
    'Retain the released assessment.',outcome,severity,'{"citations":[]}',
    'The assessment is bounded by the captured description.','2026-01-01T00:00:30Z'
FROM (VALUES (992501,'MET',NULL::text),(992502,'NOT_APPLICABLE',NULL::text),
    (992503,'UNDETERMINED',NULL::text),(992504,'NOT_MET','CRITICAL'),
    (992505,'NOT_MET','MAJOR'),(992506,'NOT_MET','MINOR')) cases(n,outcome,severity);
