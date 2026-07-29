package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.dto.RoleDto;
import com.ypyit.neoelima.domain.user.mapper.RoleMapper;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    private final RoleMapper roleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RoleDto> findAll() throws BusinessException {
        try {
            return this.roleMapper.toDtos(this.roleRepository.findAll());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
