package org.example;

import io.mateu.mdd.core.annotations.Action;
import lombok.MateuMDDEntity;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.EntityManager;


@MateuMDDEntity
@Slf4j
public class Entidad {

    private String name;

    @Action
    public void test1() {
        new Limpia().getNombre().toLowerCase();
    }

    @Action
    public void test2(String surname, int age) {
        System.out.println("Hola " + getName() + " " + surname + ", of " + age);
    }

    @Action
    public void test3(EntityManager em, String surname, int age) {
        System.out.println("Hola " + getName() + " " + surname + ", of " + age);
    }

    @Action
    public static void test4() {
        System.out.println(test5());
    }


    @Action
    public static String test5() {
        return "hola!";
    }

    @Action
    public String test6(EntityManager em, String surname, int age) {
        return "Hola!";
    }

    @Action
    public String test7(String surname, int age) {
        return "Hola!";
    }

}
