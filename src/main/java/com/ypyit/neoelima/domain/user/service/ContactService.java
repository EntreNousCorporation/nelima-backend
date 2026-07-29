package com.ypyit.neoelima.domain.user.service;


import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;

import java.util.Set;
import java.util.UUID;

public interface ContactService {

    ContactDto create(ContactCreationForm form) throws BusinessException;

    ContactDto update(UUID id, ContactUpdateForm form) throws BusinessException;

    Set<ContactEntity> createOrUpdate(Set<ContactUpdateForm> slotsForm) throws BusinessException;
}
