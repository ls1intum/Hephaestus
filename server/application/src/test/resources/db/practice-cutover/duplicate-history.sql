INSERT INTO agent_job(id,workspace_id,job_type,status,config_snapshot,job_token,retry_count,
    created_at,completed_at,available_at,trace_id,metadata,delivery_status,evidence_snapshot)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,992101,'PULL_REQUEST_REVIEW','COMPLETED','{}',
    'duplicate-history-'||n,0,at,at+interval '1 minute',at,repeat('d',32),
    '{"pull_request_id":992109,"about_user_id":992103,"commit_sha":"released-head"}',
    'DELIVERED',CASE WHEN prior IS NULL THEN '{}'::jsonb ELSE jsonb_build_object('answeredPractices',
        jsonb_build_array(jsonb_build_object('practiceSlug','explains-why','revisionId',992131,
            'reviewId','00000000-0000-0000-0000-000000'||prior))) END
FROM (VALUES (996111,'2026-01-06'::timestamptz,NULL::integer),
    (996112,'2026-01-08'::timestamptz,NULL),(996113,'2026-01-04'::timestamptz,NULL),
    (996114,'2026-01-09'::timestamptz,996111),(996115,'2026-01-10'::timestamptz,996113)) fixture(n,at,prior);
INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,
    artifact_kind,artifact_id,about_user_id,summary,outcome,severity,evidence,evidence_rationale,observed_at,superseded_at)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,'duplicate-history-'||n,
    ('00000000-0000-0000-0000-000000'||job)::uuid,992101,992121,992131,'scm.pull_request',992109,992103,
    'Retained summary '||n,outcome,CASE WHEN outcome='NOT_MET' THEN 'INFO' END,
    jsonb_build_object('citations',jsonb_build_array(jsonb_build_object('sourceKind','scm.pull-request.core',
        'artifactPath','context/description.md','path','description.md','startLine',1,'endLine',1,'quote','Quote '||n))),
    'Retained explanation '||n,at,ended
FROM (VALUES (996141,996111,'NOT_MET','2026-01-06T00:00:30Z'::timestamptz,NULL::timestamptz),
    (996142,996111,'MET','2026-01-06T00:00:30Z'::timestamptz,NULL),
    (996143,996111,'NOT_MET','2026-01-06T00:00:30Z'::timestamptz,'2026-01-07'::timestamptz),
    (996144,996112,'MET','2026-01-08T00:00:30Z'::timestamptz,NULL),
    (996145,996113,'MET','2026-01-04T00:00:30Z'::timestamptz,NULL)) fixture(n,job,outcome,at,ended);
INSERT INTO feedback(id,workspace_id,agent_job_id,recipient_user_id,about_user_id,channel,position,
    delivery_state,source,body,created_at,delivered_at)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,992101,'00000000-0000-0000-0000-000000996111',
    992103,992103,'IN_CONTEXT',n,'DELIVERED','AGENT','Retained advice '||n,'2026-01-06T00:01:00Z','2026-01-06T00:01:30Z'
FROM generate_series(996161,996163) n;
INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,('00000000-0000-0000-0000-000000'||(n-20))::uuid,'PRIMARY',0
FROM generate_series(996161,996163) n;
INSERT INTO feedback_placement(id,feedback_id,placement_type,posted_comment_ref,posted_comment_url,created_at)
SELECT ('00000000-0000-0000-0000-000000'||(n+10))::uuid,('00000000-0000-0000-0000-000000'||n)::uuid,
    'SUMMARY','provider:'||n,'https://practice-v1.example/comment/'||n,'2026-01-06T00:01:30Z'
FROM generate_series(996161,996163) n;
