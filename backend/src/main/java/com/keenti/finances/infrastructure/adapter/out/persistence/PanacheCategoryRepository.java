package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.out.CategoryRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.persistence.HibernateSessions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;

@ApplicationScoped
@com.keenti.finances.infrastructure.adapter.in.rest.UserScoped
public class PanacheCategoryRepository implements CategoryRepository {

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Override
    public List<Category> findAll() {
        return CategoryEntity.<CategoryEntity>find("deletedAt IS NULL")
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Category> findById(Long id) {
        return CategoryEntity.<CategoryEntity>find("id = ?1 AND deletedAt IS NULL", id)
                .firstResultOptional()
                .map(this::toDomain);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = toEntity(category);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Category update(Category category) {
        CategoryEntity entity = CategoryEntity.findById(category.getId());
        entity.name = category.getName();
        entity.type = category.getType();
        entity.hue = category.getHue();
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        CategoryEntity.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return CategoryEntity.count("name", name) > 0;
    }

    @Override
    public void softDeleteById(Long id) {
        CategoryEntity.update("deletedAt = ?1 WHERE id = ?2", LocalDateTime.now(), id);
    }

    @Override
    public void restoreById(Long id) {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            CategoryEntity entity = CategoryEntity.findById(id);
            if (entity != null) {
                entity.deletedAt = null;
            }
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    @SuppressWarnings("null")
    public Optional<TrashItem> findDeletedById(Long id) {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            return CategoryEntity.<CategoryEntity>find(
                    "id = ?1 AND deletedAt IS NOT NULL", id)
                    .firstResultOptional()
                    .map(e -> new TrashItem(e.id, "category", e.name, e.deletedAt));
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    @SuppressWarnings("null")
    public List<TrashItem> findAllDeleted() {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            return CategoryEntity.<CategoryEntity>find(
                    "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
                    .stream()
                    .map(e -> new TrashItem(e.id, "category", e.name, e.deletedAt))
                    .toList();
        } finally {
            session.enableFilter("softDelete");
        }
    }

    private CategoryEntity toEntity(Category c) {
        CategoryEntity e = new CategoryEntity();
        e.name = c.getName();
        e.type = c.getType();
        e.hue = c.getHue();
        e.user = UserEntity.findById(userContext.getUserId());
        return e;
    }

    private Category toDomain(CategoryEntity e) {
        return new Category(e.id, e.name, e.type, e.hue);
    }
}
