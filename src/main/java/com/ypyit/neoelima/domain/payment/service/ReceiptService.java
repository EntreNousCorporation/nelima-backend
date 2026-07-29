package com.ypyit.neoelima.domain.payment.service;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Consultation des reçus.
 *
 * <p>Lecture seule : un reçu est une pièce comptable, il ne se modifie ni ne se supprime. Une
 * correction passe par une écriture d'annulation, prévue après la V1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final CurrentUserProvider currentUserProvider;

    public Page<ReceiptEntity> search(UUID requestedEstablishmentId, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;

        UUID scope = this.currentUserProvider.resolveEstablishmentScope(requestedEstablishmentId);
        if (Objects.nonNull(scope)) {
            builder.and(receipt.establishment.id.eq(scope));
        }
        return this.receiptRepository.findAll(builder, pageable);
    }

    public ReceiptEntity findById(UUID id) {
        ReceiptEntity receipt = this.receiptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Receipt with provided id %s not found", id)));

        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.nonNull(scope) && !scope.equals(receipt.getEstablishment().getId())) {
            throw new AccessDeniedException("Receipt " + id + " belongs to another establishment");
        }
        return receipt;
    }
}
