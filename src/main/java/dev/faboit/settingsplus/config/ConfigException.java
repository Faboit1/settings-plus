package dev.faboit.settingsplus.config;

/** Thrown when {@code settings.yml} cannot be parsed. The message names the offending config path. */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }
}
