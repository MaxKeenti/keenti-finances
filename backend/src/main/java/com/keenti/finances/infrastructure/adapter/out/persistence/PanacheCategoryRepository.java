package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.port.out.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheCategoryRepository implements CategoryRepository {

    @Override
    public List<Category> findAll() {
        return CategoryEntity.<CategoryEntity>listAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Category> findById(Long id) {
        return CategoryEntity.<CategoryEntity>findByIdOptional(id)
                .map(this::toDomain);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = new CategoryEntity();
        entity.name = category.getName();
        entity.type = category.getType();
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Category update(Category category) {
        CategoryEntity entity = CategoryEntity.findById(category.getId());
        entity.name = category.getName();
        entity.type = category.getType();
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

    private Category toDomain(CategoryEntity e) {
        return new Category(e.id, e.name, e.type);
    }
}
