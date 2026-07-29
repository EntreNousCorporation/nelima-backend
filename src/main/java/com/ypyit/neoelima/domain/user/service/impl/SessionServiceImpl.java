package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.entity.UserSessionEntity;
import com.ypyit.neoelima.domain.user.repository.SessionRepository;
import com.ypyit.neoelima.domain.user.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {
    private final SessionRepository sessionRepository;

    @Override
    public UUID startSession(UUID userId) throws BusinessException {
        try {
            UserSessionEntity session = UserSessionEntity
                    .builder()
                    .userId(userId)
                    .sessionStartTime(LocalDateTime.now())
                    .build();
            return sessionRepository.save(session).getId();
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public void endSession(UUID sessionId) throws BusinessException {
        try {
            sessionRepository.findById(sessionId)
                    .ifPresent(session -> {
                        session.setSessionEndTime(LocalDateTime.now());
                        sessionRepository.save(session);
                    });
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public boolean existsById(UUID id) throws BusinessException {
        try {
            return sessionRepository.existsById(id);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Optional<UserSessionEntity> getBySessionId(UUID sessionId) throws BusinessException {
        try {
            return sessionRepository.findById(sessionId);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
