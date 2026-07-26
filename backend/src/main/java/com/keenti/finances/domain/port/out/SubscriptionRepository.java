package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.TrashItem;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository {
    List<Subscription> findAll();
    Optional<Subscription> findById(Long id);
    Optional<Subscription> findByIdForUpdate(Long id);
    Subscription save(Subscription subscription);
    Subscription update(Subscription subscription);
    void deleteById(Long id);
    Optional<Subscription> findByTokenUuid(String tokenUuid);
    void softDeleteById(Long id);
    void restoreById(Long id);
    Optional<TrashItem> findDeletedById(Long id);
    List<TrashItem> findAllDeleted();
}
