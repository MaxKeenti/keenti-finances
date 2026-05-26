package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hibernate.Session;

@ApplicationScoped
@com.keenti.finances.infrastructure.adapter.in.rest.UserScoped
public class PanacheContactRepository implements ContactRepository {

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Override
    public List<Contact> findAll() {
        return ContactEntity.<ContactEntity>listAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Contact> findById(Long id) {
        return ContactEntity.<ContactEntity>find("id = ?1", id)
                .firstResultOptional()
                .map(this::toDomain);
    }

    @Override
    public Contact save(Contact contact) {
        ContactEntity entity = toEntity(contact);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Contact update(Contact contact) {
        ContactEntity entity = ContactEntity.findById(contact.getId());
        entity.name = contact.getName();
        entity.phone = contact.getPhone();
        entity.email = contact.getEmail();
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        ContactEntity.deleteById(id);
    }

    @Override
    public void softDeleteById(Long id) {
        ContactEntity.update("deletedAt = ?1 WHERE id = ?2", LocalDateTime.now(), id);
    }

    @Override
    public void restoreById(Long id) {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            ContactEntity entity = ContactEntity.findById(id);
            if (entity != null) {
                entity.deletedAt = null;
            }
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    public Optional<TrashItem> findDeletedById(Long id) {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            return ContactEntity.<ContactEntity>find(
                    "id = ?1 AND deletedAt IS NOT NULL", id)
                    .firstResultOptional()
                    .map(e -> new TrashItem(e.id, "contact", e.name, e.deletedAt));
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    public List<TrashItem> findAllDeleted() {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            return ContactEntity.<ContactEntity>find(
                    "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
                    .stream()
                    .map(e -> new TrashItem(e.id, "contact", e.name, e.deletedAt))
                    .collect(Collectors.toList());
        } finally {
            session.enableFilter("softDelete");
        }
    }

    private ContactEntity toEntity(Contact c) {
        ContactEntity e = new ContactEntity();
        e.name = c.getName();
        e.phone = c.getPhone();
        e.email = c.getEmail();
        e.user = UserEntity.findById(userContext.getUserId());
        return e;
    }

    private Contact toDomain(ContactEntity e) {
        return new Contact(e.id, e.name, e.phone, e.email);
    }
}
