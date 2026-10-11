ALTER TABLE wallets ADD CONSTRAINT check_non_negative_balance CHECK (balance >= 0);
