package com.ypyit.neoelima.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Objects;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {NullCheck.NullCheckValidator.class})
public @interface NullCheck {

    String message() default "{null.content.not.allowed.in.list}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Getter
    @Setter
    class NullCheckValidator implements ConstraintValidator<NullCheck, Collection<?>> {

        private NullCheck nullCheck;

        @Override
        public void initialize(NullCheck nullCheck) {
            this.nullCheck = nullCheck;
        }

        @Override
        public boolean isValid(Collection<?> values, ConstraintValidatorContext context) {
            if (CollectionUtils.isEmpty(values)) {
                return true;
            }
            return values.stream().noneMatch(Objects::isNull);
        }
    }
}
