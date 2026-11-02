-- V24__add_media_purge_channel.sql
-- Media physical purge goes through the existing ADR-005 outbox (issue #39, BR-MEDIA-009).
-- 'MEDIA_PURGE' is 11 chars, fits channel VARCHAR(16).

ALTER TABLE outbound_message
    DROP CONSTRAINT chk_outbound_message_channel;
ALTER TABLE outbound_message
    ADD CONSTRAINT chk_outbound_message_channel
        CHECK (channel IN ('EMAIL','CONTENT_FILTER','MEDIA_PURGE'));
