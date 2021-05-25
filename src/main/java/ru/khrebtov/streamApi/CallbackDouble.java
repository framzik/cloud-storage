package ru.khrebtov.streamApi;

@FunctionalInterface
public interface CallbackDouble {
    String callDouble(String a, String b);

    default void test() {
        System.out.println("default method");
    }
}
