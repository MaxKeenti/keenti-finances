package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.TrashItem;
import java.util.List;
import java.util.Optional;

public interface CategoryUseCase {
    List<Category> list();
    Optional<Category> getById(Long id);
    Category create(Category category);
    Category update(Long id, Category category);
    void delete(Long id);
    void restore(Long id);
    void permanentDelete(Long id);
    List<TrashItem> listDeleted();
}
