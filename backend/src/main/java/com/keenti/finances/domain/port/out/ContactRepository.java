package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.TrashItem;
import java.util.List;
import java.util.Optional;

public interface ContactRepository {
    List<Contact> findAll();
    Optional<Contact> findById(Long id);
    Contact save(Contact contact);
    Contact update(Contact contact);
    void deleteById(Long id);
    void softDeleteById(Long id);
    void restoreById(Long id);
    Optional<TrashItem> findDeletedById(Long id);
    List<TrashItem> findAllDeleted();
}
