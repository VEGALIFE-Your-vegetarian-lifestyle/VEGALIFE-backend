-- V51__add_rejection_reason.sql
-- The admin-supplied reason for a rejection, readable from the row it describes.
--
-- moderation_log already records why a decision was taken, but it is an append-only event
-- stream: it holds every action ever applied to an id, in order. Answering "why is this
-- rejected right now" from it means picking the latest REJECT_* row and remembering that a
-- later DELETE_RECIPE/CREATE_DISH row carries a NULL reason. These columns hold the current
-- answer directly, so the review UI does not need to re-derive state on every read.
--
-- Deliberately NOT added to dish/ingredient: their rejection reason is admin-facing only and
-- they have no owner to surface it to.
--
-- Nullable because every row rejected before this migration has its reason recorded nowhere
-- but moderation_log, and because the reason itself remains optional (BR-ADMIN-002 records it
-- as optional today). Cleared when an admin approves the row again: it is current state, not
-- history — history stays in moderation_log, untouched by this change.

ALTER TABLE post ADD COLUMN rejection_reason TEXT;

ALTER TABLE recipe ADD COLUMN rejection_reason TEXT;
