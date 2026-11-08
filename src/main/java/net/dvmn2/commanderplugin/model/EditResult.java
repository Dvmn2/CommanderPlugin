package net.dvmn2.commanderplugin.model;

/** Outcome of a rule edit operation. */
public enum EditResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_EXISTS,
    INVALID_NAME,
    INVALID_VALUE,
    INVALID_INDEX,
    UNSUPPORTED_PLACEHOLDER,
    ALREADY_PRESENT,
    NOT_PRESENT,
    LIMIT_REACHED
}
