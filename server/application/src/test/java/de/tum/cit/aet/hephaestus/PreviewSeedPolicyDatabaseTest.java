package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Tag("database")
class PreviewSeedPolicyDatabaseTest {
    @Test
    void shouldStopEveryLivePathAndPreserveTerminalRowsWhenReleasedDataIsCloned() throws Exception {
        var database = PostgreSQLTestContainer.createDatabase("preview_seed_policy");
        // Keep this fixture on the released schema when later cutovers enter master.xml.
        try (var connection =
                        DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
                var liquibase = new Liquibase(
                        "db/master.xml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            var contexts = new Contexts("prod");
            var labels = new LabelExpression();
            var pending = liquibase.listUnrunChangeSets(contexts, labels);
            int throughReleasedSchema = IntStream.range(0, pending.size())
                            .filter(index -> pending.get(index).getFilePath().endsWith("1791941746633_changelog.xml"))
                            .max()
                            .orElseThrow()
                    + 1;
            liquibase.update(throughReleasedSchema, contexts, labels);
        }
        var policy = readPolicy();
        try (var connection =
                        DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
                var statement = connection.createStatement()) {
            statement.execute(RELEASED_ROWS);
            var sent = scalar(
                    connection, "SELECT to_jsonb(d)::text FROM feedback_dispatch d WHERE id=md5('dispatch:5')::uuid");
            var projected = scalar(
                    connection,
                    "SELECT (to_jsonb(d)-'projection_owner'-'projection_expires_at')::text FROM feedback_dispatch d WHERE id=md5('dispatch:6')::uuid");
            var reasons = scalar(connection, "SELECT jsonb_object_agg(id,reason)::text FROM observation_invalidation");
            var terminalSignal = scalar(
                    connection,
                    "SELECT to_jsonb(s)::text FROM artifact_signal s WHERE id=md5('signal:SUPPRESSED')::uuid");
            assertThat(scalar(connection, "SELECT coalesce(to_regclass('public.review_job')::text,'absent')"))
                    .isEqualTo("absent");
            var completed =
                    scalar(connection, "SELECT to_jsonb(j)::text FROM agent_job j WHERE delivery_status='DELIVERED'");
            assertThat(Long.parseLong(scalar(connection, policy.verification())))
                    .isPositive();
            statement.execute(policy.sql());
            assertThat(scalar(connection, policy.verification())).isEqualTo("0");
            assertThat(scalar(connection, "SELECT silent_mode_engaged::text FROM instance_settings WHERE id=1"))
                    .isEqualTo("true");
            assertThat(scalar(connection, "SELECT practice_delivery_status FROM workspace WHERE id=997001"))
                    .isEqualTo("PAUSED");
            assertThat(scalar(
                            connection,
                            "SELECT count(*) FROM connection WHERE workspace_id=997001 AND state='SUSPENDED'"))
                    .isEqualTo("4");
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM artifact_signal WHERE workspace_id=997001 AND state='SUPPRESSED' AND state_reason='GATE_SKIPPED' AND job_id IS NULL"))
                    .isEqualTo("4");
            assertThat(scalar(
                            connection,
                            "SELECT to_jsonb(s)::text FROM artifact_signal s WHERE id=md5('signal:SUPPRESSED')::uuid"))
                    .isEqualTo(terminalSignal);
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM feedback_dispatch WHERE state='FAILED' AND last_error LIKE '%preview%' AND lease_owner IS NULL AND lease_expires_at IS NULL"))
                    .isEqualTo("4");
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM observation_invalidation WHERE provider_copy='UNRESOLVED' AND provider_copy_retry_at='infinity'::timestamptz"))
                    .isEqualTo("2");
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM observation_invalidation WHERE provider_copy IN ('PENDING','UNRESOLVED') AND provider_copy_retry_at <= now()"))
                    .isEqualTo("0");
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM agent_job WHERE status='CANCELLED' AND worker_id IS NULL AND completed_at IS NOT NULL"))
                    .isEqualTo("2");
            assertThat(scalar(connection, "SELECT jsonb_object_agg(id,reason)::text FROM observation_invalidation"))
                    .isEqualTo(reasons);
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT (to_jsonb(d)-'projection_owner'-'projection_expires_at')::text FROM feedback_dispatch d WHERE id=md5('dispatch:6')::uuid"))
                    .isEqualTo(projected);
            assertThat(
                            scalar(
                                    connection,
                                    "SELECT count(*) FROM feedback_dispatch WHERE projection_owner IS NOT NULL OR projection_expires_at IS NOT NULL"))
                    .isEqualTo("0");
            assertThat(scalar(
                            connection,
                            "SELECT to_jsonb(d)::text FROM feedback_dispatch d WHERE id=md5('dispatch:5')::uuid"))
                    .isEqualTo(sent);
            assertThat(scalar(
                            connection, "SELECT to_jsonb(j)::text FROM agent_job j WHERE delivery_status='DELIVERED'"))
                    .isEqualTo(completed);
            statement.execute(policy.sql());
            assertThat(scalar(connection, policy.verification())).isEqualTo("0");
            statement.execute(
                    "UPDATE observation_invalidation SET provider_copy='PENDING' WHERE id=md5('correction:1')::uuid");
            assertThat(scalar(connection, policy.verification())).isEqualTo("1");
            statement.execute(policy.sql());
            statement.execute(
                    "UPDATE observation_invalidation SET provider_copy_retry_at=now()+interval '1 day' WHERE id=md5('correction:1')::uuid");
            assertThat(scalar(connection, policy.verification())).isEqualTo("1");
            statement.execute(policy.sql());
            statement.execute(
                    "UPDATE feedback_dispatch SET projection_owner='fixture-projector', projection_expires_at=now()+interval '1 day' WHERE id=md5('dispatch:6')::uuid");
            assertThat(scalar(connection, policy.verification())).isEqualTo("1");
            statement.execute(policy.sql());
            statement.execute("UPDATE instance_settings SET silent_mode_engaged=false WHERE id=1");
            assertThat(scalar(connection, policy.verification())).isEqualTo("1");
            statement.execute(policy.sql());
            assertThat(scalar(connection, "SELECT silent_mode_engaged::text FROM instance_settings WHERE id=1"))
                    .isEqualTo("true");
            connection.setAutoCommit(false);
            try {
                statement.execute("DELETE FROM instance_settings WHERE id=1");
                assertThat(scalar(connection, policy.verification())).isEqualTo("1");
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            var verified = PostgreSQLTestContainer.getInstance()
                    .execInContainer(
                            "psql",
                            "-U",
                            database.username(),
                            "-d",
                            "preview_seed_policy",
                            "-tAX",
                            "-v",
                            "ON_ERROR_STOP=1",
                            "-c",
                            policy.verification());
            assertThat(verified.getExitCode()).isZero();
            assertThat(verified.getStdout()).isEqualTo("0\n");
        }
    }

    @Test
    void shouldParkClonedHephWorkWithoutEndingOrBillingItsAttempts() throws Exception {
        var database = PostgreSQLTestContainer.createMigratedDatabase("preview_seed_policy_heph");
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/practice-native/result-parent.sql"),
                new ClassPathResource("db/practice-native/feedback-parent.sql"));
        populator.execute(new DriverManagerDataSource(database.jdbcUrl(), database.username(), database.password()));
        var policy = readPolicy();
        try (var connection =
                        DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
                var statement = connection.createStatement()) {
            statement.execute(CLONED_HEPH_ROWS);
            String turns =
                    "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM mentor_turn t WHERE workspace_id=978001";
            String history = """
                SELECT jsonb_build_object(
                    'measurements', (SELECT jsonb_agg(to_jsonb(u) ORDER BY turn_id,attempt)
                        FROM mentor_turn_attempt_usage u WHERE workspace_id=978001),
                    'ledger', (SELECT jsonb_agg(to_jsonb(e) ORDER BY id)
                        FROM llm_usage_event e WHERE workspace_id=978001),
                    'copies', (SELECT jsonb_agg(to_jsonb(c) ORDER BY id)
                        FROM feedback_copy c WHERE workspace_id=978001),
                    'feedback', (SELECT jsonb_agg(to_jsonb(f) ORDER BY id)
                        FROM feedback f WHERE workspace_id=978001))::text
                """;
            String facts = """
                SELECT jsonb_agg(to_jsonb(t)-'status'-'worker_id'-'leased_until' ORDER BY id)::text
                FROM mentor_turn t WHERE workspace_id=978001
                """;
            String terminal = """
                SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM mentor_turn t WHERE workspace_id=978001
                    AND request_id IN ('preview-done','preview-failed','preview-stopped')
                """;
            String binding =
                    "SELECT (to_jsonb(b)-'enabled'-'updated_at')::text FROM workspace_agent_binding b WHERE id=997101";
            var bindingBefore = scalar(connection, binding);
            var historyBefore = scalar(connection, history);
            var factsBefore = scalar(connection, facts);
            var terminalBefore = scalar(connection, terminal);
            assertThat(scalar(connection, """
                SELECT count(*) FROM mentor_turn WHERE workspace_id=978001
                    AND (status IN ('QUEUED','RUNNING') OR (status='STOPPED' AND leased_until IS NOT NULL))
                """)).isEqualTo("3");
            assertThat(scalar(connection, """
                SELECT count(*) FROM feedback_copy WHERE workspace_id=978001 AND kind='CHAT_TURN'
                    AND state='WRITE_STARTED' AND posted_at IS NULL AND readback='UNKNOWN'
                """)).isEqualTo("1");
            assertThat(Long.parseLong(scalar(connection, policy.verification())))
                    .isPositive();

            statement.execute(policy.sql());

            assertThat(scalar(connection, """
                SELECT count(*) FROM mentor_turn WHERE workspace_id=978001
                    AND request_id IN ('native-turn','preview-queued','preview-held')
                    AND status='STOPPED' AND worker_id IS NULL AND leased_until IS NULL
                """))
                    .as("cloned queued work and both kinds of held lease are parked")
                    .isEqualTo("3");
            assertThat(scalar(connection, """
                SELECT count(*) FROM workspace_agent_binding WHERE id=997101 AND workspace_id=978001
                    AND purpose='MENTOR' AND NOT enabled
                """)).isEqualTo("1");
            assertThat(scalar(connection, binding)).isEqualTo(bindingBefore);
            assertThat(scalar(connection, facts)).isEqualTo(factsBefore);
            assertThat(scalar(connection, terminal)).isEqualTo(terminalBefore);
            assertThat(scalar(connection, history)).isEqualTo(historyBefore);
            assertThat(scalar(connection, policy.verification())).isEqualTo("0");
            var parked = scalar(connection, turns);

            statement.execute(policy.sql());

            assertThat(scalar(connection, turns)).isEqualTo(parked);
            assertThat(scalar(connection, history)).isEqualTo(historyBefore);
            assertThat(scalar(connection, policy.verification())).isEqualTo("0");
        }
    }

    // A cutover test can use the same policy with its own migrated schema and fixtures.
    static SeedPolicy readPolicy() throws Exception {
        var compose = Path.of(System.getProperty("basedir", "."), "../../docker/preview/compose.app.yaml");
        var sources = new YamlPropertySourceLoader().load("preview", new FileSystemResource(compose));
        var command = Objects.requireNonNull(sources.getFirst().getProperty("services.seed-loader.command[2]"))
                .toString()
                .replace("$$", "$");
        return new SeedPolicy(
                extract(command, "<<'SQL'\\n([\\s\\S]*?)\\nSQL\\n"),
                extract(command, "LIVE=\\$\\(psql[^\\n]*-c \"([\\s\\S]*?)\"\\)"));
    }

    private static String extract(String command, String expression) {
        var match = Pattern.compile(expression).matcher(command);
        assertThat(match.find()).as("seed-loader SQL block: %s", expression).isTrue();
        return Objects.requireNonNull(match.group(1));
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            var value = Objects.requireNonNull(rows.getString(1));
            assertThat(rows.next()).isFalse();
            return value;
        }
    }

    record SeedPolicy(String sql, String verification) {}

    private static final String CLONED_HEPH_ROWS = """
        INSERT INTO instance_settings(id) VALUES(1) ON CONFLICT DO NOTHING;
        UPDATE instance_settings SET silent_mode_engaged=false;
        INSERT INTO llm_connection(id,slug,display_name,base_url,api_protocol,enabled,created_at)
        VALUES(997101,'preview-heph','Preview Heph','https://preview-policy.example','openai-responses',true,now());
        INSERT INTO llm_model(id,connection_id,slug,display_name,upstream_model_id,created_at)
        VALUES(997101,997101,'preview-heph','Preview Heph','cloned-model',now());
        INSERT INTO workspace_agent_binding(id,workspace_id,purpose,enabled,instance_model_id,created_at)
        VALUES(997101,978001,'MENTOR',true,997101,now());
        INSERT INTO mentor_conversation(id,workspace_id,account_id,created_at,last_activity_at)
        VALUES(md5('preview-held')::uuid,978001,978007,'2026-01-05T00:00:00Z','2026-01-05T00:00:00Z');
        INSERT INTO mentor_turn(id,workspace_id,conversation_id,request_id,status,surface,attempt,
            created_at,started_at,finished_at,worker_id,leased_until,input_tokens,llm_calls)
        SELECT md5(request)::uuid,978001,
            CASE WHEN request='preview-held' THEN md5('preview-held')::uuid
                ELSE '00000000-0000-0000-0000-000000978051'::uuid END,
            request,status,'APP',CASE WHEN status='QUEUED' THEN 0 ELSE 1 END,'2026-01-05T00:00:00Z',
            CASE WHEN status<>'QUEUED' THEN '2026-01-05T00:01:00Z'::timestamptz END,
            CASE WHEN request IN ('preview-done','preview-failed','preview-stopped')
                THEN '2026-01-05T00:02:00Z'::timestamptz END,
            CASE WHEN request='preview-held' THEN 'staging-worker' END,
            CASE WHEN request='preview-held' THEN '2026-01-05T00:03:00Z'::timestamptz END,
            CASE WHEN status='QUEUED' THEN 0 ELSE 40 END,CASE WHEN status='QUEUED' THEN 0 ELSE 2 END
        FROM (VALUES('preview-queued','QUEUED'),('preview-held','STOPPED'),('preview-done','DONE'),
            ('preview-failed','FAILED'),('preview-stopped','STOPPED')) seed(request,status);
        UPDATE mentor_turn SET worker_id='staging-worker',leased_until='2026-01-05T00:03:00Z',
            input_tokens=40,llm_calls=2,partial='{"messages":[]}',
            admission='{"model":"cloned-model","price":{"fundingSource":"INSTANCE","pricingState":"NO_CHARGE",
                "appliedPriceId":null,"appliedWorkspaceModelId":null,"per1mInputUsd":"0","per1mOutputUsd":"0",
                "per1mCacheReadUsd":"0","per1mCacheWriteUsd":"0"},"route":{"apiProtocol":"openai-responses",
                "baseUrl":"https://preview-policy.example","connectionScope":"INSTANCE","connectionId":997101,
                "modelId":997101},"contextWindow":32768,"maxOutputTokens":8192,"reasoningEffort":null}'
        WHERE id='00000000-0000-0000-0000-000000978053';
        INSERT INTO mentor_entry(id,workspace_id,conversation_id,seq,kind,surface,text,turn_id,created_at)
        SELECT md5(t.request_id||':question')::uuid,t.workspace_id,t.conversation_id,
            CASE WHEN request_id='native-turn' THEN 2 ELSE 3 END,'USER','APP','A cloned question.',t.id,
            '2026-01-05T00:00:00Z' FROM mentor_turn t
        WHERE t.workspace_id=978001 AND request_id IN ('native-turn','preview-queued');
        INSERT INTO mentor_turn_attempt_usage(turn_id,attempt,workspace_id,model,funding_source,pricing_state,
            occurred_at,calls,input_tokens,applied_per_1m_input_usd,applied_per_1m_output_usd,
            applied_per_1m_cache_read_usd,applied_per_1m_cache_write_usd)
        SELECT id,attempt,workspace_id,'cloned-model','INSTANCE','NO_CHARGE',finished_at,2,40,0,0,0,0
        FROM mentor_turn WHERE workspace_id=978001 AND request_id IN ('native-turn','preview-held','preview-done');
        INSERT INTO llm_usage_event(id,workspace_id,job_type,source_type,source_id,source_attempt,model,
            input_tokens,total_calls,cost_usd,occurred_at,pricing_state,usage_provenance,measurement_verification)
        VALUES(md5('preview-heph-ledger')::uuid,978001,'MENTOR_TURN','MENTOR_TURN',md5('preview-done')::uuid,
            1,'cloned-model',40,2,0,'2026-01-05T00:02:00Z','NO_CHARGE','PROXY','VERIFIED');
        UPDATE feedback_copy SET state='WRITE_STARTED',write_started_at=now(),
            mentor_turn_id='00000000-0000-0000-0000-000000978053',readback='UNKNOWN',
            stop_reason='DELIVERY_UNCONFIRMED'
        WHERE id='00000000-0000-0000-0000-000000978045';
        """;

    private static final String RELEASED_ROWS = """
        INSERT INTO workspace(id,account_login,account_type,display_name,slug,status,
            practice_review_auto_trigger_enabled,practice_review_manual_trigger_enabled,public_activity_enabled)
        VALUES(997001,'preview-policy','ORG','Preview policy','preview-policy','ACTIVE',true,true,true);
        INSERT INTO instance_settings(id) VALUES(1) ON CONFLICT DO NOTHING;
        UPDATE instance_settings SET public_activity_allowed=true,silent_mode_engaged=false;
        INSERT INTO account(id,display_name,app_role,status,created_at,updated_at,version)
        VALUES(997001,'Preview person','USER','ACTIVE',now(),now(),0);
        INSERT INTO identity_provider(id,type,server_url) VALUES(997001,'GITHUB','https://preview-policy.example');
        INSERT INTO "user"(id,native_id,provider_id,login) VALUES(997001,997001,997001,'preview-policy');
        INSERT INTO artifact_signal(id,workspace_id,artifact_kind,artifact_id,signal_name,revision,
            occurred_at,discovered_via,state,state_reason,state_changed_at)
        SELECT md5('signal:'||state)::uuid,997001,'scm.pull_request',997001,'scm.pull_request.created',state,
            now(),'SYNC',state,CASE WHEN state='SUPPRESSED' THEN 'GATE_SKIPPED' END,now()
        FROM (VALUES('RECORDED'),('DEFERRED'),('PENDING'),('SUPPRESSED')) AS seed(state);
        INSERT INTO agent_job(id,workspace_id,job_type,status,delivery_status,config_snapshot,job_token,job_token_hash,
            retry_count,created_at,available_at,trace_id,worker_id,completed_at)
        SELECT md5('job:'||n)::uuid,997001,'PRACTICE_REVIEW',status,delivery,'{}',
            CASE WHEN delivery='DELIVERED' THEN 'cleared-by-preview-clone:'||md5('job:'||n)::uuid ELSE 'fixture:'||n END,
            CASE WHEN delivery='DELIVERED' THEN NULL ELSE md5(n::text)||md5(n::text) END,
            0,now(),now(),repeat('a',32),'fixture-worker',CASE WHEN status='COMPLETED' THEN now() END
        FROM (VALUES(1,'QUEUED',NULL),(2,'RUNNING',NULL),(3,'COMPLETED','PENDING'),(4,'COMPLETED','DELIVERED'))
            AS seed(n,status,delivery);
        INSERT INTO feedback_dispatch(id,destination_key,workspace_id,agent_job_id,destination,state,body,practice_slugs,
            package_content,delivered_placements,lease_owner,lease_expires_at,projection_owner,projection_expires_at,delivered_external_ref,
            next_attempt_at,attempt_count,created_at,updated_at)
        SELECT md5('dispatch:'||n)::uuid,'preview:'||n,997001,md5('job:4')::uuid,'AUTOMATIC_REVIEW_PACKAGE',state,
            CASE WHEN n=6 THEN 'Posted fixture body' ELSE '' END,
            '[]','{}','[]',CASE WHEN state='CLAIMED' THEN 'fixture-worker' END,
            CASE WHEN state='CLAIMED' THEN now()+lease END,
            CASE WHEN n IN (1,6) THEN 'fixture-projector' END,
            CASE WHEN n IN (1,6) THEN now()+interval '1 day' END,
            CASE WHEN n=6 THEN 'fixture-review:6' END,now()-interval '1 day',0,now(),now()
        FROM (VALUES(1,'PENDING',interval '0'),(2,'UNCERTAIN',interval '0'),(3,'CLAIMED',interval '-1 day'),
            (4,'CLAIMED',interval '1 day'),(5,'SENT',interval '0'),(6,'SENT',interval '0')) AS seed(n,state,lease);
        INSERT INTO practice(id,workspace_id,slug,name,criteria,created_at,automated_review_policy,delivery_behavior,signals,evidence_requirements,review_when)
        VALUES(997001,997001,'preview-policy','Preview policy','Explain the change.',now(),'{}','{"summaryOnly":false}','["scm.pull_request.created"]','[]','{}');
        INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,criteria,signals,
            evidence_requirements,review_when,subject,automated_review_policy,delivery_behavior,created_at)
        SELECT 997001,id,1,slug,name,applies_to,criteria,signals,evidence_requirements,review_when,subject,
            automated_review_policy,delivery_behavior,now() FROM practice WHERE id=997001;
        UPDATE practice SET current_revision_id=997001 WHERE id=997001;
        INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,artifact_kind,artifact_id,
            about_user_id,summary,outcome,observed_at)
        SELECT md5('observation:'||n)::uuid,'preview:'||n,md5('job:4')::uuid,997001,997001,'scm.pull_request',997001,
            997001,'Fixture','MET',now() FROM generate_series(1,2) AS n;
        INSERT INTO observation_invalidation(id,workspace_id,observation_id,invalidated_at,invalidated_by_account_id,
            provider_copy,provider_copy_retry_at,reason)
        SELECT md5('correction:'||n)::uuid,997001,md5('observation:'||n)::uuid,now(),997001,provider_copy,
            now()-interval '1 day',CASE WHEN n=1 THEN repeat('r',500) ELSE 'Fixture correction' END
        FROM (VALUES(1,'PENDING'),(2,'UNRESOLVED')) AS seed(n,provider_copy);
        INSERT INTO review_sweep_schedule(id,workspace_id,artifact_kind,cadence,lookback_days,enabled,next_run_at,
            created_by_account_id,created_at,updated_at)
        VALUES(md5('schedule')::uuid,997001,'scm.pull_request','DAILY',1,true,now(),997001,now(),now());
        INSERT INTO llm_connection(id,slug,display_name,base_url,api_protocol,api_key,enabled,created_at)
        VALUES(997001,'preview-policy','Preview policy','https://preview-policy.example','openai-responses','fixture',true,now());
        INSERT INTO llm_model(id,connection_id,slug,display_name,upstream_model_id,created_at)
        VALUES(997001,997001,'preview-policy','Preview policy','fixture',now());
        INSERT INTO workspace_agent_binding(id,workspace_id,purpose,enabled,instance_model_id)
        VALUES(997001,997001,'PRACTICE_REVIEW',true,997001);
        INSERT INTO workspace_llm_connection(id,workspace_id,slug,display_name,base_url,api_protocol,api_key,enabled,created_at)
        VALUES(997001,997001,'preview-policy','Preview policy','https://preview-policy.example','openai-responses','fixture',true,now());
        INSERT INTO connection(id,workspace_id,kind,instance_key,state,credentials_encrypted,credentials_alg,
            credentials_key_version,credentials_rotation_failed_at,config)
        SELECT 997000+n,997001,kind,'preview:'||n,CASE WHEN kind='GITLAB' THEN 'SUSPENDED' ELSE 'ACTIVE' END,'fixture'::bytea,'fixture',1,now(),'{"webhookSecret":"fixture"}'
        FROM (VALUES(1,'GITHUB'),(2,'GITLAB'),(3,'SLACK'),(4,'OUTLINE')) AS seed(n,kind);
        INSERT INTO sync_job(id,workspace_id,connection_id,kind,type,trigger_source,status,heartbeat_at)
        SELECT 997000+n,997001,997000+n,kind,'INITIAL','SYSTEM',status,now()
        FROM (VALUES(1,'PENDING','GITHUB'),(2,'RUNNING','GITLAB')) AS seed(n,status,kind);
        INSERT INTO issued_jwt(jti,account_id,expires_at) VALUES(md5('jwt')::uuid,997001,now()+interval '1 day');
        INSERT INTO jwt_signing_key(kid,public_key_pem,private_key_pem,encryption_key_id)
        VALUES('fixture','fixture','fixture'::bytea,'fixture');
        INSERT INTO login_provider(id,registration_id,type,display_name,base_url,client_id,client_secret,scopes,enabled,seeded_from_env)
        VALUES(997001,'preview-policy','GITHUB','Preview policy','https://preview-policy.example','fixture','fixture','fixture',true,true);
        """;
}
