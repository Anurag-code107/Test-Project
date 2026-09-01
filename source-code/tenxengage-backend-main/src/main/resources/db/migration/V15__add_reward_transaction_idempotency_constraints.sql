CREATE UNIQUE INDEX uq_reward_tx_completion_currency
    ON reward_transactions(completion_id, currency_id)
    WHERE completion_id IS NOT NULL;

CREATE UNIQUE INDEX uq_reward_tx_claim_currency
    ON reward_transactions(claim_action_id, currency_id)
    WHERE claim_action_id IS NOT NULL;
