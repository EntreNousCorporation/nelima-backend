package com.ypyit.neoelima.domain.utils;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
public final class FunctionalUtils {

    private FunctionalUtils() {
        throw new UnsupportedOperationException("FunctionalUtils may not be instantiated");
    }

    public static <T> Optional<T> getOrEmpty(Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
            log.warn("Error while getting data {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static <T> T getOrNull(Supplier<T> supplier) {
        return getOrEmpty(supplier).orElse(null);
    }

    public static <T> Stream<T> safelyGetStream(Collection<T> collection) {
        return Optional.ofNullable(collection).stream().flatMap(Collection::stream);
    }

    public static <T> Stream<T> safelyGetStream(T[] array) {
        return Optional.ofNullable(array).stream().flatMap(Arrays::stream);
    }

    public static <T, R> T getOrNew(R value, Function<R, T> getter, T defaultValue, Consumer<T> setter) {
        T subValue = getter.apply(value);
        if (subValue == null) {
            setter.accept(defaultValue);
            return defaultValue;
        }
        return subValue;
    }

    public static void checkDuplicatedOnCreation(Set<ContactCreationForm> contacts) {
        boolean containsDuplicatedValues = contacts.stream()
                .map(ContactCreationForm::getValue)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().anyMatch(e -> e.getValue() > 1);

        if (containsDuplicatedValues) {
            throw new BadRequestException("Un même contact figure en double.");
        }
    }

    public static void checkDuplicatedOnUpdate(Set<ContactUpdateForm> contacts) {
        boolean containsDuplicatedValues = contacts.stream()
                .map(ContactUpdateForm::getValue)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().anyMatch(e -> e.getValue() > 1);

        if (containsDuplicatedValues) {
            throw new BadRequestException("Un même contact figure en double.");
        }
    }

    public static boolean isFalse(Boolean value) {
        return Objects.isNull(value) || BooleanUtils.isFalse(value);
    }

    public static boolean isTrue(Boolean value) {
        return Objects.nonNull(value) && BooleanUtils.isTrue(value);
    }
}
