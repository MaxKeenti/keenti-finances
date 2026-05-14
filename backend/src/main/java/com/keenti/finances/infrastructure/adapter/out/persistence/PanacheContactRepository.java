package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.port.out.ContactRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheContactRepository implements ContactRepository {

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
        ContactEntity entity = new ContactEntity();
        entity.name = contact.getName();
        entity.phone = contact.getPhone();
        entity.email = contact.getEmail();
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

    private Contact toDomain(ContactEntity e) {
        return new Contact(e.id, e.name, e.phone, e.email);
    }
}
