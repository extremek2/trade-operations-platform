package com.tradeoperationsplatform.apiserver;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class DeploymentRehearsalTest {
    @Test void dumpRestoreMigrateAndInvalidateRestoredAccessOnDisposableDatabases() throws Exception {
        try(var database=new PostgreSQLContainer<>("postgres:15-alpine")) {
            database.withEnv("APP_DB_USER","runtime_fixture").withEnv("APP_DB_PASSWORD","test-only-runtime-password");
            database.start();
            var migration=Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword());
            migration.load().migrate();
            exec(database,"psql","-U",database.getUsername(),"-d",database.getDatabaseName(),"-v","ON_ERROR_STOP=1","-c","""
                INSERT INTO organization(name,organization_type) VALUES ('restore fixture','SHIPPER');
                INSERT INTO app_user(email,name) VALUES ('restore@example.test','restore fixture');
                INSERT INTO organization_member(organization_id,user_id,member_role) VALUES (1,1,'OWNER');
                INSERT INTO shipment_case(owner_organization_id,case_number,direction,transport_mode,created_by) VALUES (1,'RESTORE-001','IMPORT','SEA',1);
                """);
            copy(database,"grant-runtime-role.sql");copy(database,"preflight.sql");copy(database,"postflight.sql");copy(database,"invalidate-restored-access.sql");
            exec(database,"psql","-U",database.getUsername(),"-d",database.getDatabaseName(),"-f","/tmp/preflight.sql");
            exec(database,"pg_dump","-U",database.getUsername(),"-d",database.getDatabaseName(),"-Fc","-f","/tmp/before.dump");
            exec(database,"createdb","-U",database.getUsername(),"candidate");
            exec(database,"pg_restore","-U",database.getUsername(),"-d","candidate","--no-owner","--exit-on-error","/tmp/before.dump");
            String candidate="jdbc:postgresql://"+database.getHost()+":"+database.getMappedPort(5432)+"/candidate";
            exec(database,"psql","-U",database.getUsername(),"-d","candidate","-f","/tmp/postflight.sql");
            exec(database,"psql","-U",database.getUsername(),"-d","candidate","-f","/tmp/grant-runtime-role.sql");
            try(var c=DriverManager.getConnection(candidate,"runtime_fixture","test-only-runtime-password");var sql=c.createStatement()) {
                assertThat(sql.execute("SELECT * FROM shipment_case")).isTrue();
                assertThatThrownBy(() -> sql.execute("CREATE TABLE forbidden_table(id integer)")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.execute("DELETE FROM identity_audit_event")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.execute("UPDATE flyway_schema_history SET success=false")).isInstanceOf(SQLException.class);
            }
            assertThat(query(database,database.getJdbcUrl(),"SELECT max(installed_rank) FROM flyway_schema_history")).isEqualTo("10");
            assertThat(query(database,candidate,"SELECT count(*) FROM organization_member WHERE member_role='OWNER'")).isEqualTo("1");
            exec(database,"psql","-U",database.getUsername(),"-d","candidate","-v","ON_ERROR_STOP=1","-c","""
                INSERT INTO business_partner(owner_organization_id,name) VALUES(1,'fixture partner');
                INSERT INTO business_partner_role(business_partner_id,partner_role) VALUES(1,'FORWARDER');
                INSERT INTO case_partner(shipment_case_id,business_partner_id,partner_role) VALUES(1,1,'FORWARDER');
                INSERT INTO external_contact(owner_organization_id,business_partner_id,name,email) VALUES(1,1,'fixture contact','restore@example.test');
                INSERT INTO case_participant(shipment_case_id,case_partner_id,user_id,external_contact_id,participant_role,access_level,status) VALUES(1,1,1,1,'FORWARDER','VIEWER','ACTIVE');
                INSERT INTO case_invitation(participant_id,token_hash,target_email,expires_at,created_by,invitation_status,accepted_at) VALUES(1,'fixture invitation','restore@example.test',NOW()+INTERVAL '7 days',1,'ACCEPTED',NOW());
                INSERT INTO email_auth_token(purpose,invitation_id,target_email,token_hash,expires_at) VALUES('CASE_LOGIN',1,'restore@example.test','fixture token',NOW()+INTERVAL '15 minutes');
                INSERT INTO refresh_session(user_id,session_kind,participant_id,token_hash,expires_at) VALUES(1,'CASE',1,'fixture refresh',NOW()+INTERVAL '1 day');
                INSERT INTO mail_outbox(email_token_id,encrypted_payload,expires_at) VALUES(1,'fixture encrypted payload',NOW()+INTERVAL '15 minutes');
                """);
            exec(database,"pg_dump","-U",database.getUsername(),"-d","candidate","-Fc","-f","/tmp/current.dump");
            exec(database,"createdb","-U",database.getUsername(),"recovered");
            exec(database,"pg_restore","-U",database.getUsername(),"-d","recovered","--no-owner","--exit-on-error","/tmp/current.dump");
            exec(database,"psql","-U",database.getUsername(),"-d","recovered","-f","/tmp/invalidate-restored-access.sql");
            String recovered=candidate.replace("/candidate","/recovered");
            assertThat(query(database,recovered,"SELECT count(*) FROM refresh_session WHERE revoked_at IS NULL")).isEqualTo("0");
            assertThat(query(database,recovered,"SELECT count(*) FROM email_auth_token WHERE revoked_at IS NULL AND consumed_at IS NULL")).isEqualTo("0");
            assertThat(query(database,recovered,"SELECT count(*) FROM mail_outbox WHERE status='PENDING' OR encrypted_payload IS NOT NULL")).isEqualTo("0");
            assertThat(query(database,recovered,"SELECT count(*) FROM case_participant WHERE status='ACTIVE'")).isEqualTo("0");
            assertThat(query(database,recovered,"SELECT count(*) FROM shipment_case WHERE case_number='RESTORE-001'")).isEqualTo("1");
            assertThat(query(database,candidate,"SELECT count(*) FROM case_participant WHERE status='ACTIVE'")).isEqualTo("1");
        }
    }
    void copy(PostgreSQLContainer<?> db,String name) {
        db.copyFileToContainer(MountableFile.forHostPath(Path.of("../ops/sql",name).toAbsolutePath()),"/tmp/"+name);
    }
    void exec(PostgreSQLContainer<?> db,String... command) throws Exception {
        var result=db.execInContainer(command);
        assertThat(result.getExitCode()).as("database rehearsal command: %s",command[0]).isZero();
    }
    String query(PostgreSQLContainer<?> db,String url,String statement) throws Exception {
        try(var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword());var sql=connection.createStatement();var r=sql.executeQuery(statement)) {r.next();return r.getString(1);}
    }
}
