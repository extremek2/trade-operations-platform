CREATE TABLE organization (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name              TEXT NOT NULL,
    organization_type TEXT NOT NULL CHECK (organization_type IN (
        'SHIPPER', 'FORWARDER', 'CUSTOMS_BROKER', 'CARRIER',
        'TRANSPORTER', 'WAREHOUSE', 'OTHER'
    )),
    business_number   TEXT,
    email             TEXT,
    phone             TEXT,
    status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE app_user (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    email             TEXT NOT NULL UNIQUE CHECK (email = lower(btrim(email))),
    password_hash     TEXT,
    name              TEXT NOT NULL,
    phone             TEXT,
    status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED')),
    email_verified_at TIMESTAMP,
    last_login_at     TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE organization_member (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    user_id         BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    member_role     TEXT NOT NULL CHECK (member_role IN ('OWNER', 'ADMIN', 'OPERATOR', 'VIEWER')),
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    joined_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, user_id)
);

CREATE TABLE shipment_case (
    id                        BIGSERIAL PRIMARY KEY,
    public_id                 UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    owner_organization_id     BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    case_number               TEXT NOT NULL,
    direction                 TEXT NOT NULL CHECK (direction IN ('IMPORT', 'EXPORT')),
    transport_mode            TEXT NOT NULL CHECK (transport_mode IN ('SEA', 'AIR', 'ROAD', 'RAIL', 'MULTIMODAL')),
    current_stage             TEXT NOT NULL DEFAULT 'PREPARATION' CHECK (current_stage IN (
        'PREPARATION', 'BOOKING', 'DEPARTED', 'IN_TRANSIT',
        'ARRIVED', 'CUSTOMS', 'DELIVERY', 'COMPLETED'
    )),
    status                    TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ON_HOLD', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    priority                  TEXT NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('NORMAL', 'ATTENTION', 'URGENT')),
    shipper_reference         TEXT,
    purchase_order_number     TEXT,
    carrier_name              TEXT,
    vessel_name               TEXT,
    voyage_number             TEXT,
    flight_number             TEXT,
    origin_location_code      TEXT,
    origin_location_name      TEXT,
    destination_location_code TEXT,
    destination_location_name TEXT,
    etd                       TIMESTAMP,
    atd                       TIMESTAMP,
    eta                       TIMESTAMP,
    ata                       TIMESTAMP,
    cargo_description         TEXT,
    package_count             INTEGER CHECK (package_count IS NULL OR package_count >= 0),
    gross_weight              NUMERIC CHECK (gross_weight IS NULL OR gross_weight >= 0),
    weight_unit               TEXT,
    container_count           INTEGER CHECK (container_count IS NULL OR container_count >= 0),
    created_by                BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    archived_at               TIMESTAMP,
    created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    version                   BIGINT NOT NULL DEFAULT 0,
    UNIQUE (owner_organization_id, case_number)
);

CREATE TABLE transport_document (
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    shipment_case_id BIGINT NOT NULL REFERENCES shipment_case(id) ON DELETE RESTRICT,
    document_type    TEXT NOT NULL CHECK (document_type IN ('MBL', 'HBL', 'MAWB', 'HAWB', 'BOOKING', 'OTHER')),
    document_number  TEXT NOT NULL,
    issuer_name      TEXT,
    issued_at        TIMESTAMP,
    is_primary       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (shipment_case_id, document_type, document_number)
);

CREATE TABLE business_partner (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    owner_organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name                  TEXT NOT NULL,
    country_code          VARCHAR(2),
    business_number       TEXT,
    status                TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_business_partner_name
    ON business_partner(owner_organization_id, lower(btrim(name)));

CREATE TABLE business_partner_role (
    business_partner_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE CASCADE,
    partner_role        TEXT NOT NULL CHECK (partner_role IN (
        'SUPPLIER', 'FORWARDER', 'CUSTOMS_BROKER', 'CARRIER',
        'TRANSPORTER', 'WAREHOUSE', 'OTHER'
    )),
    PRIMARY KEY (business_partner_id, partner_role)
);

CREATE TABLE external_contact (
    id                    BIGSERIAL PRIMARY KEY,
    owner_organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    business_partner_id   BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    name                  TEXT NOT NULL,
    email                 TEXT NOT NULL CHECK (email = lower(btrim(email))),
    phone                 TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (owner_organization_id, email)
);

CREATE TABLE case_partner (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    shipment_case_id    BIGINT NOT NULL REFERENCES shipment_case(id) ON DELETE RESTRICT,
    business_partner_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    partner_role        TEXT NOT NULL CHECK (partner_role IN ('FORWARDER', 'CUSTOMS_BROKER')),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (shipment_case_id, business_partner_id)
);

CREATE TABLE case_participant (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    shipment_case_id    BIGINT NOT NULL REFERENCES shipment_case(id) ON DELETE RESTRICT,
    case_partner_id     BIGINT NOT NULL REFERENCES case_partner(id) ON DELETE RESTRICT,
    user_id             BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    external_contact_id BIGINT NOT NULL REFERENCES external_contact(id) ON DELETE RESTRICT,
    participant_role    TEXT NOT NULL CHECK (participant_role IN (
        'SHIPPER', 'FORWARDER', 'CUSTOMS_BROKER', 'TRANSPORTER', 'WAREHOUSE', 'VIEWER'
    )),
    access_level        TEXT NOT NULL CHECK (access_level IN ('CONTRIBUTOR', 'VIEWER')),
    status              TEXT NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED')),
    joined_at           TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (status <> 'ACTIVE' OR user_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_case_verified_user ON case_participant(shipment_case_id, user_id)
    WHERE user_id IS NOT NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX uq_case_external_contact ON case_participant(shipment_case_id, external_contact_id)
    WHERE status IN ('INVITED', 'ACTIVE');

CREATE TABLE case_invitation (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    participant_id    BIGINT NOT NULL REFERENCES case_participant(id) ON DELETE CASCADE,
    token_hash        TEXT NOT NULL UNIQUE,
    target_email      TEXT NOT NULL CHECK (target_email = lower(btrim(target_email))),
    invitation_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (invitation_status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at        TIMESTAMP NOT NULL,
    accepted_at       TIMESTAMP,
    revoked_at        TIMESTAMP,
    created_by        BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_session (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id         BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organization_id BIGINT REFERENCES organization(id) ON DELETE CASCADE,
    participant_id  BIGINT REFERENCES case_participant(id) ON DELETE RESTRICT,
    session_kind    TEXT NOT NULL CHECK (session_kind IN ('ACCOUNT', 'ORGANIZATION', 'PLATFORM', 'CASE')),
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (
        (session_kind = 'ORGANIZATION' AND organization_id IS NOT NULL AND participant_id IS NULL)
        OR (session_kind IN ('ACCOUNT', 'PLATFORM') AND organization_id IS NULL AND participant_id IS NULL)
        OR (session_kind = 'CASE' AND organization_id IS NULL AND participant_id IS NOT NULL)
    )
);

CREATE TABLE platform_role_assignment (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    role       TEXT NOT NULL CHECK (role = 'SYSTEM_ADMIN'),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    UNIQUE (user_id, role),
    CHECK ((active AND revoked_at IS NULL) OR (NOT active AND revoked_at IS NOT NULL))
);

CREATE TABLE identity_audit_event (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    actor_type      TEXT NOT NULL CHECK (actor_type IN ('USER', 'BOOTSTRAP', 'SYSTEM')),
    action          TEXT NOT NULL,
    target_type     TEXT NOT NULL,
    target_id       TEXT NOT NULL,
    organization_id BIGINT REFERENCES organization(id) ON DELETE RESTRICT,
    old_value       TEXT,
    new_value       TEXT,
    reason          TEXT NOT NULL,
    request_id      UUID NOT NULL,
    occurred_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE organization_application (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    applicant_user_id        BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    organization_name        TEXT NOT NULL,
    business_number          TEXT,
    applicant_name           TEXT NOT NULL,
    applicant_email          TEXT NOT NULL,
    phone                    TEXT,
    status                   TEXT NOT NULL CHECK (status IN ('PENDING_EMAIL', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    previous_application_id  BIGINT UNIQUE REFERENCES organization_application(id) ON DELETE RESTRICT,
    approved_organization_id BIGINT UNIQUE REFERENCES organization(id) ON DELETE RESTRICT,
    reviewed_by              BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    public_reason            TEXT,
    internal_note            TEXT,
    submitted_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at              TIMESTAMP,
    version                  BIGINT NOT NULL DEFAULT 0,
    CHECK ((status = 'APPROVED') = (approved_organization_id IS NOT NULL)),
    CHECK ((status IN ('APPROVED', 'REJECTED')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND public_reason IS NOT NULL))
);

CREATE TABLE email_auth_token (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id       BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    invitation_id BIGINT REFERENCES case_invitation(id) ON DELETE RESTRICT,
    purpose       TEXT NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'CASE_LOGIN')),
    target_email  TEXT NOT NULL,
    token_hash    TEXT NOT NULL UNIQUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP NOT NULL,
    consumed_at   TIMESTAMP,
    revoked_at    TIMESTAMP,
    CHECK (
        (purpose = 'VERIFY_EMAIL' AND user_id IS NOT NULL AND invitation_id IS NULL)
        OR (purpose = 'CASE_LOGIN' AND user_id IS NULL AND invitation_id IS NOT NULL)
    )
);

CREATE TABLE mail_outbox (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    email_token_id    BIGINT REFERENCES email_auth_token(id) ON DELETE RESTRICT,
    encrypted_payload TEXT,
    status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'EXPIRED', 'FAILED', 'CANCELLED')),
    attempts          INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at           TIMESTAMP,
    last_error        TEXT,
    CHECK (status <> 'PENDING' OR encrypted_payload IS NOT NULL)
);

CREATE TABLE auth_rate_limit (
    bucket_key   TEXT NOT NULL,
    window_start TIMESTAMP NOT NULL,
    requests     INTEGER NOT NULL CHECK (requests > 0),
    PRIMARY KEY (bucket_key, window_start)
);

CREATE INDEX idx_organization_member_user ON organization_member(user_id);
CREATE INDEX idx_shipment_dashboard ON shipment_case(owner_organization_id, status, archived_at);
CREATE INDEX idx_shipment_eta ON shipment_case(eta);
CREATE INDEX idx_transport_document_number ON transport_document(document_number);
CREATE INDEX idx_case_invitation_email ON case_invitation(target_email, expires_at);
CREATE INDEX idx_refresh_session_user ON refresh_session(user_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_session_participant ON refresh_session(participant_id) WHERE participant_id IS NOT NULL;
CREATE INDEX idx_identity_audit_target ON identity_audit_event(target_type, target_id, occurred_at);
CREATE UNIQUE INDEX uq_application_pending_user ON organization_application(applicant_user_id)
    WHERE status IN ('PENDING_EMAIL', 'PENDING_REVIEW');
CREATE INDEX idx_application_review_queue ON organization_application(status, submitted_at, id);
CREATE INDEX idx_application_user ON organization_application(applicant_user_id, submitted_at DESC);
CREATE INDEX idx_email_token_user ON email_auth_token(user_id, created_at DESC);
CREATE INDEX idx_email_token_invitation ON email_auth_token(invitation_id, created_at DESC);
CREATE INDEX idx_mail_outbox_pending ON mail_outbox(available_at, id) WHERE status = 'PENDING';
