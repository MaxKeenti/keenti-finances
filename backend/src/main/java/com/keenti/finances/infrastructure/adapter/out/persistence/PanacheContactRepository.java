package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheContactRepository implements ContactRepository {

    @Inject
    UserContext userContext;

    @Override
    public List<Contact> findAll() {
        return ContactEntity.<ContactEntity>listAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Contact> findById(Long id) {
        return ContactEntity.<ContactEntity>findByIdOptional(id)
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
        entity.user = UserEntity.findById(userContext.getUserId());
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        ContactEntity.deleteById(id);
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
