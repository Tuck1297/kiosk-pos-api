-- Store which resource an idempotent request produced, so a retry with the same
-- Idempotency-Key replays the original result by re-reading that row (an order or
-- a payment) instead of storing a serialized response body.
ALTER TABLE idempotency_key ADD COLUMN resource_id UUID;
