package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.port.in.CategoryUseCase;
import com.keenti.finances.domain.port.out.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CategoryService implements CategoryUseCase {

    private static final Logger LOG = Logger.getLogger(CategoryService.class);
    private static final Set<String> VALID_TYPES = Set.of("INGRESS", "EGRESS", "BOTH");

    @Inject
    CategoryRepository categoryRepository;

    @Override
    public List<Category> list() {
        List<Category> categories = categoryRepository.findAll();
        LOG.infof("category.list count=%d", categories.size());
        return categories;
    }

    @Override
    public Optional<Category> getById(Long id) {
        Optional<Category> result = categoryRepository.findById(id);
        LOG.infof("category.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Category create(Category category) {
        if (!VALID_TYPES.contains(category.getType())) {
            throw new BadRequestException("Invalid category type: " + category.getType());
        }
        if (categoryRepository.existsByName(category.getName())) {
            throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Category name already exists\"}")
                    .build());
        }
        Category created = categoryRepository.save(category);
        LOG.infof("category.create id=%d name=%s type=%s", created.getId(), created.getName(), created.getType());
        return created;
    }

    @Override
    @Transactional
    public Category update(Long id, Category category) {
        if (!VALID_TYPES.contains(category.getType())) {
            throw new BadRequestException("Invalid category type: " + category.getType());
        }
        categoryRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Category not found: " + id));
        if (categoryRepository.existsByName(category.getName())) {
            Category existing = categoryRepository.findById(id).get();
            if (!existing.getName().equals(category.getName())) {
                throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                        .entity("{\"error\":\"Category name already exists\"}")
                        .build());
            }
        }
        Category updated = categoryRepository.update(new Category(id, category.getName(), category.getType(), category.getColor()));
        LOG.infof("category.update id=%d name=%s type=%s", id, updated.getName(), updated.getType());
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        categoryRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Category not found: " + id));
        categoryRepository.deleteById(id);
        LOG.infof("category.delete id=%d", id);
    }
}
