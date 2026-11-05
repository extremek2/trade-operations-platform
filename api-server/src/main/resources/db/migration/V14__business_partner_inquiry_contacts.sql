ALTER TABLE business_partner
    ADD COLUMN inquiry_email VARCHAR(320),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE business_partner_inquiry_cc (
    business_partner_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    PRIMARY KEY (business_partner_id, email)
);
