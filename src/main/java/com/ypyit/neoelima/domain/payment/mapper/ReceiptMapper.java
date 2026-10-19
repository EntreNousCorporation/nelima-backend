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

    @org.mapstruct.Mapping(target = "studentId", source = "paymentIntent.installment.studentFee.student.id")
    @org.mapstruct.Mapping(target = "studentPhotoId", source = "paymentIntent.installment.studentFee.student.photoDocumentId")
    ReceiptDto toDto(ReceiptEntity receipt);

    List<ReceiptDto> toDtos(List<ReceiptEntity> receipts);
}
