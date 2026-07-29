package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.dto.PermissionDto;

import java.util.List;

public interface PermissionService {

    List<PermissionDto> findByGroup(String permissionGroup) throws BusinessException;
}
