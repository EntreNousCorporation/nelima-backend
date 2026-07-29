package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.user.mapper.ContactMapper;
import com.ypyit.neoelima.domain.user.repository.ContactRepository;
import com.ypyit.neoelima.domain.user.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;

    @Override
    public ContactDto create(ContactCreationForm form) throws BusinessException {
        try {
            ContactEntity contact = this.contactMapper.toEntity(form);
            return this.contactMapper.toDto(this.contactRepository.save(contact));
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public ContactDto update(UUID id, ContactUpdateForm form) throws BusinessException {
        try {
            ContactEntity contact = this.contactRepository.findById(id).orElseThrow(() ->
                    new NotFoundException(String.format("Cannot find contact with provided id %s", id)));
            this.contactMapper.toUpdate(form, contact);
            return this.contactMapper.toDto(this.contactRepository.save(contact));
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Set<ContactEntity> createOrUpdate(Set<ContactUpdateForm> contactsForm) throws BusinessException {
        Set<ContactEntity> contacts = new HashSet<>();
        if (CollectionUtils.isEmpty(contactsForm)) {
            return Collections.emptySet();
        }
        try {
            contactsForm.forEach(slotUpdateForm -> {
                if (Objects.nonNull(slotUpdateForm.getId())) {
                    ContactDto updated = this.update(slotUpdateForm.getId(), slotUpdateForm);
                    contacts.add(this.contactMapper.toEntity(updated));
                } else {
                    ContactDto created = this.create(this.contactMapper.toCreate(slotUpdateForm));
                    contacts.add(this.contactMapper.toEntity(created));
                }
            });
            return contacts;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}

