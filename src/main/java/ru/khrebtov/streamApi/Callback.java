package ru.khrebtov.streamApi;

@FunctionalInterface
public interface Callback {
    void call(String value);
}
