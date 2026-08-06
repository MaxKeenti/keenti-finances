package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.port.out.FinancialAccountTransferRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
@UserScoped
public class PanacheFinancialAccountTransferRepository implements FinancialAccountTransferRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<FinancialAccountTransfer> findAll() {
        return FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "ORDER BY transferDate DESC, createdAt DESC, id DESC")
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public FinancialAccountTransfer save(FinancialAccountTransfer transfer) {
        FinancialAccountTransferEntity entity = new FinancialAccountTransferEntity();
        entity.user = em.getReference(UserEntity.class, userContext.getUserId());
        entity.sourceAccount = em.getReference(FinancialAccountEntity.class, transfer.sourceAccountId());
        entity.destinationAccount = em.getReference(FinancialAccountEntity.class, transfer.destinationAccountId());
        entity.amount = transfer.amount();
        entity.transferDate = transfer.transferDate();
        entity.notes = transfer.notes();
        entity.createdAt = LocalDateTime.now();
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    private FinancialAccountTransfer toDomain(FinancialAccountTransferEntity entity) {
        return new FinancialAccountTransfer(entity.id, entity.sourceAccount.id,
            entity.destinationAccount.id, entity.amount, entity.transferDate,
            entity.notes, entity.createdAt);
    }
}
