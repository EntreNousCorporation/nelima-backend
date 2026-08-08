package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentUpdateForm;
import com.ypyit.neoelima.domain.user.mapper.ContactMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", uses = ContactMapper.class, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StudentMapper {

    StudentEntity toEntity(StudentCreationForm creationForm);

    void toUpdate(StudentUpdateForm updateForm, @MappingTarget StudentEntity entity);

    StudentDto toDto(StudentEntity student);

    StudentLiteDto toLiteDto(StudentEntity student);

    List<StudentDto> toDtos(List<StudentEntity> students);

    List<StudentLiteDto> toLiteDtos(List<StudentEntity> students);
}
