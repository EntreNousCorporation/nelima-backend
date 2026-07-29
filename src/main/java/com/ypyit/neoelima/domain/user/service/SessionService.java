package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.entity.UserSessionEntity;

import java.util.Optional;
import java.util.UUID;

public interface SessionService {

    UUID startSession(UUID userId) throws BusinessException;

    void endSession(UUID sessionId) throws BusinessException;

    boolean existsById(UUID id) throws BusinessException;

    Optional<UserSessionEntity> getBySessionId(UUID sessionId) throws BusinessException;

}
