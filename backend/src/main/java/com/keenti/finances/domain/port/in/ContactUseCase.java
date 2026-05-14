package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.Contact;
import java.util.List;
import java.util.Optional;

public interface ContactUseCase {
    List<Contact> list();
    Optional<Contact> getById(Long id);
    Contact create(Contact contact);
    Contact update(Long id, Contact contact);
    void delete(Long id);
}
