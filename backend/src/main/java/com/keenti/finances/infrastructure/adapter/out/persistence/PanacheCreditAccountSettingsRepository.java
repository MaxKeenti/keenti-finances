package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheCreditAccountSettingsRepository implements CreditAccountSettingsRepository {
    @Inject EntityManager em;
    @Inject UserContext userContext;

    @Override
    public Optional<CreditAccountSettings> findByAccountId(Long accountId) {
        return em.createQuery("""
                SELECT settings FROM CreditAccountSettingsEntity settings
                JOIN settings.account account
                WHERE settings.accountId = :accountId AND account.user.id = :userId
                """, CreditAccountSettingsEntity.class)
            .setParameter("accountId", accountId).setParameter("userId", userContext.getUserId())
            .getResultStream().findFirst().map(this::toDomain);
    }

    @Override
    public CreditAccountSettings save(CreditAccountSettings settings) {
        CreditAccountSettingsEntity entity = em.find(CreditAccountSettingsEntity.class, settings.accountId());
        if (entity == null) {
            entity = new CreditAccountSettingsEntity();
            entity.accountId = settings.accountId();
        }
        entity.creditLimit = settings.creditLimit();
        entity.statementClosingDay = settings.statementClosingDay();
        entity.paymentDueDay = settings.paymentDueDay();
        if (!em.contains(entity)) {
            em.persist(entity);
        }
        em.flush();
        return toDomain(entity);
    }

    private CreditAccountSettings toDomain(CreditAccountSettingsEntity entity) {
        return new CreditAccountSettings(entity.accountId, entity.creditLimit, entity.statementClosingDay, entity.paymentDueDay);
    }
}
