package net.dvmn2.commanderplugin.model;

import java.util.regex.Pattern;

/** Validation patterns shared by the model types. Values that fail them are never put into commands. */
final class Patterns {

    static final Pattern RULE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");
    static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9_./-]{1,64}");
    static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,9}");
    static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    static final Pattern PERMISSION = Pattern.compile("[A-Za-z0-9_.*-]{1,128}");

    private Patterns() {
    }
}
