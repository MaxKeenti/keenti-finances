package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.out.FinancialAccountTransferRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
                "deletedAt IS NULL ORDER BY transferDate DESC, createdAt DESC, id DESC")
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<FinancialAccountTransfer> findById(Long id) {
        return FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "id = ?1 AND deletedAt IS NULL", id)
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public Optional<FinancialAccountTransfer> findDeletedTransferById(Long id) {
        return FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "id = ?1 AND deletedAt IS NOT NULL", id)
            .firstResultOptional().map(this::toDomain);
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

    @Override
    public FinancialAccountTransfer update(FinancialAccountTransfer transfer) {
        FinancialAccountTransferEntity entity = FinancialAccountTransferEntity
            .<FinancialAccountTransferEntity>find("id = ?1 AND deletedAt IS NULL", transfer.id())
            .firstResultOptional().orElseThrow();
        entity.sourceAccount = em.getReference(FinancialAccountEntity.class, transfer.sourceAccountId());
        entity.destinationAccount = em.getReference(FinancialAccountEntity.class, transfer.destinationAccountId());
        entity.amount = transfer.amount();
        entity.transferDate = transfer.transferDate();
        entity.notes = transfer.notes();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public void softDeleteById(Long id) {
        FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "id = ?1 AND deletedAt IS NULL", id)
            .firstResultOptional().ifPresent(entity -> entity.deletedAt = LocalDateTime.now());
        em.flush();
    }

    @Override
    public void restoreById(Long id) {
        FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "id = ?1 AND deletedAt IS NOT NULL", id)
            .firstResultOptional().ifPresent(entity -> entity.deletedAt = null);
        em.flush();
    }

    @Override
    public void deleteById(Long id) {
        FinancialAccountTransferEntity.delete("id = ?1 AND deletedAt IS NOT NULL", id);
        em.flush();
    }

    @Override
    public Optional<TrashItem> findDeletedById(Long id) {
        return FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "id = ?1 AND deletedAt IS NOT NULL", id)
            .firstResultOptional().map(this::toTrashItem);
    }

    @Override
    public List<TrashItem> findAllDeleted() {
        return FinancialAccountTransferEntity.<FinancialAccountTransferEntity>find(
                "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
            .stream().map(this::toTrashItem).toList();
    }

    private FinancialAccountTransfer toDomain(FinancialAccountTransferEntity entity) {
        return new FinancialAccountTransfer(entity.id, entity.sourceAccount.id,
            entity.destinationAccount.id, entity.amount, entity.transferDate,
            entity.notes, entity.createdAt);
    }

    private TrashItem toTrashItem(FinancialAccountTransferEntity entity) {
        return new TrashItem(entity.id, "transfer", entity.sourceAccount.name + " → "
            + entity.destinationAccount.name + " · " + entity.amount.toPlainString(), entity.deletedAt);
    }
}
