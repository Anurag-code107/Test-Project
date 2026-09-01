-- Retire action.redemption.redeem_company.
--
-- Company-wallet redemption is gone (design §12.3): a company does not redeem for itself, it
-- distributes to its sellers. The company path's endpoints, service methods, export scope and UI were
-- all removed; distribution is gated by action.redemption.distribute instead. This drops the now
-- unreachable key so it cannot be granted and cannot appear in an admin permission picker.
--
-- Everything that still needs a payout gate (the XTRM redemption-profile endpoints) now reads
-- action.redemption.redeem alone. V52 granted PARTNER_ADMIN that key precisely so this narrowing
-- takes nothing away — before V52 a Partner Admin held only redeem_company, which is why redeeming
-- as a seller returned 403.
--
-- These tables key on permission_key (VARCHAR) rather than a FK to permissions.id, so there is no
-- referential order to respect; dependents are cleared first only for readability.

-- Guard. Dev was verified to have no such role, but staging/prod data is not dev data: if any role
-- still holds redeem_company WITHOUT redeem, deleting the key silently removes that role's only
-- payout permission. Fail the migration instead of locking someone out.
DO $$
DECLARE
    orphaned INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphaned
    FROM client_role_permissions crp
    WHERE crp.permission_key = 'action.redemption.redeem_company'
      AND crp.granted = TRUE
      AND NOT EXISTS (
          SELECT 1 FROM client_role_permissions other
          WHERE other.client_role_id = crp.client_role_id
            AND other.permission_key = 'action.redemption.redeem'
            AND other.granted = TRUE
      );

    IF orphaned > 0 THEN
        RAISE EXCEPTION
            'V55 aborted: % client role(s) hold action.redemption.redeem_company without '
            'action.redemption.redeem. Grant them action.redemption.redeem first, or they lose '
            'payout access when this key is dropped.', orphaned;
    END IF;
END $$;

DELETE FROM user_permission_overrides    WHERE permission_key = 'action.redemption.redeem_company';
DELETE FROM company_permission_overrides WHERE permission_key = 'action.redemption.redeem_company';
DELETE FROM client_permission_grants     WHERE permission_key = 'action.redemption.redeem_company';
DELETE FROM client_role_permissions      WHERE permission_key = 'action.redemption.redeem_company';
DELETE FROM permissions                  WHERE permission_key = 'action.redemption.redeem_company';

-- NOTE: the effectivePermissions Redis cache (@Cacheable, 5 min TTL) still holds pre-migration sets
-- keyed by user. It expires on its own, but evict it after deploying so an admin who was mid-session
-- does not keep a stale grant for the key that no longer exists.
