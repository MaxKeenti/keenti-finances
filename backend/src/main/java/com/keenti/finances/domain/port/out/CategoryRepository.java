package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> findAll();
    Optional<Category> findById(Long id);
    Category save(Category category);
    Category update(Category category);
    void deleteById(Long id);
    boolean existsByName(String name);
}
