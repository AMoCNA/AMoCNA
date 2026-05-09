package com.kubiki.themis.knowledge;

import java.util.function.BiFunction;
import java.util.function.Function;

public record Result<T>(T value, String error) {

    public static <T> Result<T> success(T value) {
        return new Result<>(value, null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public <U> Result<U> map(Function<T, U> mapper) {
        if (isSuccess()) {
            return Result.success(mapper.apply(value));
        } else {
            return Result.failure(error);
        }
    }

    public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
        if (isSuccess()) {
            return mapper.apply(value);
        } else {
            return Result.failure(error);
        }
    }

    public static <T1, T2, R> Result<R> combine(Result<T1> r1, Result<T2> r2, BiFunction<T1, T2, R> combiner) {
        if (r1.isSuccess() && r2.isSuccess()) {
            return Result.success(combiner.apply(r1.value(), r2.value()));
        }
        return Result.failure(r1.isSuccess() ? r2.error() : r1.error());
    }
}
