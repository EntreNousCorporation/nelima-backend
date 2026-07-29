package com.ypyit.neoelima.common.validator;

import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.utils.CredentialsUtils;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.validator.routines.EmailValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ContactValueCheck.ContactCreationValueCheck.class, ContactValueCheck.ContactUpdateValueCheck.class})
public @interface ContactValueCheck {

    String message() default "{contact.value.must.be.valid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Getter
    @Setter
    class ContactCreationValueCheck implements ConstraintValidator<ContactValueCheck, ContactCreationForm> {

        private ContactValueCheck contactValueCheck;

        @Override
        public void initialize(ContactValueCheck contactValueCheck) {
            this.contactValueCheck = contactValueCheck;
        }

        @Override
        public boolean isValid(ContactCreationForm contact, ConstraintValidatorContext context) {
            if (Objects.isNull(contact)) {
                return true;
            }
            if (ContactType.EMAIL.equals(contact.getType())) {
                if (FunctionalUtils.isTrue(contact.getWhatsApp())) {
                    return false;
                }
                return EmailValidator.getInstance().isValid(contact.getValue());
            }
            return CredentialsUtils.isValidPhoneNumber(contact.getValue());
        }
    }

    @Getter
    @Setter
    class ContactUpdateValueCheck implements ConstraintValidator<ContactValueCheck, ContactUpdateForm> {

        private ContactValueCheck contactValueCheck;

        @Override
        public void initialize(ContactValueCheck contactValueCheck) {
            this.contactValueCheck = contactValueCheck;
        }

        @Override
        public boolean isValid(ContactUpdateForm contact, ConstraintValidatorContext context) {
            if (Objects.isNull(contact)) {
                return true;
            }
            if (ContactType.EMAIL.equals(contact.getType())) {
                return EmailValidator.getInstance().isValid(contact.getValue());
            }
            return CredentialsUtils.isValidPhoneNumber(contact.getValue());
        }
    }
}
