package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.dto.RoleDto;

import java.util.List;

public interface RoleService {

    List<RoleDto> findAll() throws BusinessException;
}
