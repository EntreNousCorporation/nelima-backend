package com.ypyit.neoelima.common.validator;

import com.ypyit.neoelima.domain.utils.StorageUtils;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {FileExtensionCheck.FileExtensionCheckValidator.class})
public @interface FileExtensionCheck {

    String message() default "{file.extension.not.valid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Getter
    @Setter
    class FileExtensionCheckValidator implements ConstraintValidator<FileExtensionCheck, String> {

        private FileExtensionCheck fileExtensionCheck;

        @Override
        public void initialize(FileExtensionCheck fileExtensionCheck) {
            this.fileExtensionCheck = fileExtensionCheck;
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
            if (StringUtils.isBlank(value)) {
                return true;
            }
            return StorageUtils.isValidExtension(value);
        }
    }
}
