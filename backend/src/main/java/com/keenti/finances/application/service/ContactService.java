package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.out.ContactRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContactService implements ContactUseCase {

    private static final Logger LOG = Logger.getLogger(ContactService.class);

    @Inject
    ContactRepository contactRepository;

    @Override
    public List<Contact> list() {
        List<Contact> contacts = contactRepository.findAll();
        LOG.infof("contact.list count=%d", contacts.size());
        return contacts;
    }

    @Override
    public Optional<Contact> getById(Long id) {
        Optional<Contact> result = contactRepository.findById(id);
        LOG.infof("contact.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Contact create(Contact contact) {
        Contact created = contactRepository.save(contact);
        LOG.infof("contact.create id=%d name=%s", created.getId(), created.getName());
        return created;
    }

    @Override
    @Transactional
    public Contact update(Long id, Contact contact) {
        contactRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Contact not found: " + id));
        Contact updated = contactRepository.update(new Contact(id, contact.getName(), contact.getPhone(), contact.getEmail()));
        LOG.infof("contact.update id=%d name=%s", id, updated.getName());
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        contactRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Contact not found: " + id));
        contactRepository.deleteById(id);
        LOG.infof("contact.delete id=%d", id);
    }
}
