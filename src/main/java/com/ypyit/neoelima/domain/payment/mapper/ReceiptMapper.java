package com.ypyit.neoelima.domain.payment.mapper;

import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReceiptMapper {

    ReceiptDto toDto(ReceiptEntity receipt);

    List<ReceiptDto> toDtos(List<ReceiptEntity> receipts);
}
