-- Keep subscription history but normalize expired/duplicate active rows before
-- enforcing the application's one-active-subscription-per-user invariant.
UPDATE subscriptions
SET active = FALSE
WHERE active = TRUE
  AND (expiry_date IS NULL OR expiry_date <= CURRENT_TIMESTAMP);

WITH ranked_active AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY expiry_date DESC NULLS LAST,
                        start_date DESC NULLS LAST,
                        id DESC
           ) AS row_number
    FROM subscriptions
    WHERE active = TRUE
)
UPDATE subscriptions s
SET active = FALSE
FROM ranked_active r
WHERE s.id = r.id
  AND r.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_one_active_per_user
    ON subscriptions (user_id)
    WHERE active = TRUE;
