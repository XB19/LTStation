package com.ltstation.center.sw.form;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Nom d'origine de la fenêtre (clé de la mise en page, ex. "CardManage" ou "NormalStatue.Inventory"). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SwWindow {
    String value();

    /** Fenêtre ouverte avant la connexion (assistant StationCreate, licence…). */
    boolean anonymous() default false;
}
