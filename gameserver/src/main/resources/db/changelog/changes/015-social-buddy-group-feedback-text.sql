-- Buddy groups: Tiger Woods PGA Tour 2004's Messenger files each buddy under a group name
ALTER TABLE social.BUDDY ADD COLUMN GROUP_NAME varchar(64) NULL;

-- Abuse reports: Tiger Woods PGA Tour 2004 sends them without a feedback type, with the room and the recent chat as evidence
ALTER TABLE social.FEEDBACK ADD COLUMN ROOM varchar(64) NULL;
ALTER TABLE social.FEEDBACK ADD COLUMN TEXT text NULL;
INSERT INTO social.FEEDBACK_TYPE (LABEL, NUMBER) VALUES ('Report abuse', 0);
