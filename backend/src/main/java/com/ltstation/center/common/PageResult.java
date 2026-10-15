package com.ltstation.center.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Résultat paginé renvoyé aux tableaux du frontend. */
public record PageResult<T>(List<T> rows, long total) {

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements());
    }

    public static <T> PageResult<T> of(List<T> rows, long total) {
        return new PageResult<>(rows, total);
    }
}
