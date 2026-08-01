package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.FundingTrigger;
import com.keenti.finances.domain.port.out.FundingTriggerRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheFundingTriggerRepository implements FundingTriggerRepository {

    @Inject
    UserContext userContext;

    @Override
    public List<FundingTrigger> findAllByBoxId(Long boxId) {
        return FundingTriggerEntity.<FundingTriggerEntity>find(
                "box.id = ?1 ORDER BY id", boxId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<FundingTrigger> findById(Long boxId, Long triggerId) {
        return FundingTriggerEntity.<FundingTriggerEntity>find(
                "id = ?1 AND box.id = ?2", triggerId, boxId)
            .firstResultOptional()
            .map(this::toDomain);
    }

    @Override
    public List<FundingTrigger> findEnabledByCategoryId(Long categoryId) {
        return FundingTriggerEntity.<FundingTriggerEntity>find(
                "enabled = true AND category.id = ?1 "
                    + "AND category.deletedAt IS NULL AND box.archived = false ORDER BY id",
                categoryId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean existsByBoxAndCategory(Long boxId, Long categoryId, Long excludingId) {
        if (excludingId == null) {
            return FundingTriggerEntity.count(
                "box.id = ?1 AND category.id = ?2", boxId, categoryId) > 0;
        }
        return FundingTriggerEntity.count(
            "box.id = ?1 AND category.id = ?2 AND id <> ?3",
            boxId, categoryId, excludingId) > 0;
    }

    @Override
    public FundingTrigger save(FundingTrigger trigger) {
        LocalDateTime now = LocalDateTime.now();
        FundingTriggerEntity entity = new FundingTriggerEntity();
        entity.user = UserEntity.findById(userContext.getUserId());
        entity.box = BoxEntity.<BoxEntity>find("id = ?1", trigger.boxId()).firstResult();
        entity.category = CategoryEntity.<CategoryEntity>find(
            "id = ?1 AND deletedAt IS NULL", trigger.categoryId()).firstResult();
        apply(entity, trigger);
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public FundingTrigger update(FundingTrigger trigger) {
        FundingTriggerEntity entity = FundingTriggerEntity.<FundingTriggerEntity>find(
                "id = ?1 AND box.id = ?2", trigger.id(), trigger.boxId())
            .firstResult();
        entity.category = CategoryEntity.<CategoryEntity>find(
            "id = ?1 AND deletedAt IS NULL", trigger.categoryId()).firstResult();
        apply(entity, trigger);
        entity.updatedAt = LocalDateTime.now();
        return toDomain(entity);
    }

    @Override
    public void delete(Long boxId, Long triggerId) {
        FundingTriggerEntity.delete("id = ?1 AND box.id = ?2", triggerId, boxId);
    }

    private void apply(FundingTriggerEntity entity, FundingTrigger trigger) {
        entity.strategy = trigger.strategy().name();
        entity.fixedAmount = trigger.fixedAmount();
        entity.percentage = trigger.percentage();
        entity.enabled = trigger.enabled();
    }

    private FundingTrigger toDomain(FundingTriggerEntity entity) {
        return new FundingTrigger(
            entity.id,
            entity.box.id,
            entity.box.name,
            entity.category.id,
            entity.category.name,
            FundingTrigger.Strategy.valueOf(entity.strategy),
            entity.fixedAmount,
            entity.percentage,
            entity.enabled,
            entity.createdAt,
            entity.updatedAt
        );
    }
}
