package net.dvmn2.commanderplugin.storage;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.commanderplugin.model.Rule;
import net.dvmn2.commanderplugin.model.TriggerEvent;
import net.dvmn2.commanderplugin.storage.dto.RuleDto;
import net.dvmn2.commanderplugin.storage.dto.RulesFileDto;
import net.dvmn2.commanderplugin.util.Placeholders;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes {@code rules.json}.
 *
 * <p>All disk access runs on one dedicated thread, so a read can never overtake a pending write. Writes are
 * atomic. {@link #load()} blocks the caller until the file is read; the file is small and size-limited.
 * Invalid entries are skipped with a warning; an unreadable file is moved to {@code rules.json.broken}.
 *
 * <pre>
 * {
 *   "version": 1,
 *   "rules": [
 *     {
 *       "name": "session-count",
 *       "enabled": true,
 *       "events": ["join", "quit"],
 *       "delay-seconds": 0.0,
 *       "permission": "",
 *       "worlds": [],
 *       "commands": ["scoreboard players add {player} SessionCount 1"]
 *     }
 *   ]
 * }
 * </pre>
 */
public final class RuleStore {

    private static final String FILE_NAME = "rules.json";
    private static final String BROKEN_SUFFIX = ".broken";
    private static final int FORMAT_VERSION = 1;
    /** Largest accepted file, bytes. */
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    /** How long shutdown waits for queued writes, seconds. */
    private static final long SHUTDOWN_WAIT_SECONDS = 10L;

    private final Path file;
    private final Logger log;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CommanderPlugin-Storage");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean modified;

    public RuleStore(Path dataFolder, Logger log) {
        this.file = dataFolder.resolve(FILE_NAME);
        this.log = log;
    }

    /**
     * Loads the rules, waiting for queued writes first. Creates an empty file on first run.
     *
     * @return the rules, or null if the file could not be read (the error is logged)
     */
    @Nullable
    public List<Rule> load() {
        try {
            return writer.submit(this::readFile).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warning("Interrupted while loading " + FILE_NAME);
            return null;
        } catch (ExecutionException ex) {
            log.log(Level.SEVERE, "Failed to load " + FILE_NAME, ex.getCause());
            return null;
        }
    }

    /** Queues an asynchronous save of an immutable snapshot. */
    public void saveAsync(List<Rule> snapshot) {
        modified = true;
        writer.execute(() -> {
            try {
                writeFile(snapshot);
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Failed to save " + FILE_NAME, ex);
            }
        });
    }

    /** Waits for queued writes, then synchronously saves the snapshot if anything was changed. */
    public void shutdown(List<Rule> finalSnapshot) {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warning("Timed out waiting for pending writes of " + FILE_NAME);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (!modified) {
            return;
        }
        try {
            writeFile(finalSnapshot);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to save " + FILE_NAME, ex);
        }
    }

    // ---------- Reading ----------

    private List<Rule> readFile() throws IOException {
        if (!Files.exists(file)) {
            writeFile(List.of());
            return List.of();
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            quarantine("it is larger than " + MAX_FILE_BYTES + " bytes");
            return List.of();
        }
        RulesFileDto dto;
        try {
            dto = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), RulesFileDto.class);
        } catch (JsonParseException ex) {
            quarantine("it is not valid JSON (" + ex.getMessage() + ")");
            return List.of();
        }
        if (dto == null || dto.rules == null) {
            return List.of();
        }
        if (dto.version > FORMAT_VERSION) {
            log.warning(FILE_NAME + " has format version " + dto.version + " but this plugin supports "
                    + FORMAT_VERSION + "; unknown fields are ignored");
        }
        List<Rule> rules = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (RuleDto ruleDto : dto.rules) {
            position++;
            Rule rule = ruleDto == null ? null : toRule(ruleDto, position, seen);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    @Nullable
    private Rule toRule(RuleDto dto, int position, Set<String> seen) {
        String name = dto.name == null ? "" : dto.name.toLowerCase(Locale.ROOT);
        String where = "Rule #" + position + " in " + FILE_NAME;
        if (!Rule.isValidName(name)) {
            log.warning(where + " skipped: invalid name");
            return null;
        }
        if (!seen.add(name)) {
            log.warning(where + " skipped: duplicate name '" + name + "'");
            return null;
        }
        String prefix = "Rule '" + name + "': ";

        Set<TriggerEvent> events = EnumSet.noneOf(TriggerEvent.class);
        for (String key : orEmpty(dto.events)) {
            TriggerEvent event = key == null ? null : TriggerEvent.fromKey(key);
            if (event == null) {
                log.warning(prefix + "unknown event '" + key + "' ignored");
            } else {
                events.add(event);
            }
        }

        List<String> commands = new ArrayList<>();
        for (String raw : orEmpty(dto.commands)) {
            String command = raw == null ? null : Rule.normalizeCommand(raw);
            if (command == null) {
                log.warning(prefix + "invalid command ignored");
                continue;
            }
            String unsupported = Placeholders.firstUnsupported(command, events);
            if (unsupported != null) {
                log.warning(prefix + "command ignored, placeholder " + unsupported
                        + " is unknown or not available for the rule's events: /" + command);
                continue;
            }
            commands.add(command);
        }

        List<String> worlds = new ArrayList<>();
        for (String world : orEmpty(dto.worlds)) {
            if (world != null && Rule.isValidWorld(world)) {
                worlds.add(world);
            } else {
                log.warning(prefix + "invalid world name ignored");
            }
        }

        String permission = dto.permission == null ? "" : dto.permission.strip();
        if (!permission.isEmpty() && !Rule.isValidPermission(permission)) {
            log.warning(prefix + "invalid permission ignored");
            permission = "";
        }
        return new Rule(name, dto.enabled, events, dto.delaySeconds, permission, worlds, commands);
    }

    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    private void quarantine(String reason) throws IOException {
        Path broken = file.resolveSibling(FILE_NAME + BROKEN_SUFFIX);
        Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
        log.warning(FILE_NAME + " was moved to " + broken.getFileName() + " because " + reason
                + "; starting with no rules");
    }

    // ---------- Writing ----------

    private void writeFile(List<Rule> rules) throws IOException {
        RulesFileDto dto = new RulesFileDto();
        dto.version = FORMAT_VERSION;
        for (Rule rule : rules) {
            dto.rules.add(toDto(rule));
        }
        writeAtomically(file, gson.toJson(dto).getBytes(StandardCharsets.UTF_8));
    }

    private static RuleDto toDto(Rule rule) {
        RuleDto dto = new RuleDto();
        dto.name = rule.name();
        dto.enabled = rule.enabled();
        for (TriggerEvent event : rule.events()) {
            dto.events.add(event.getKey());
        }
        dto.delaySeconds = rule.delaySeconds();
        dto.permission = rule.permission();
        dto.worlds.addAll(rule.worlds());
        dto.commands.addAll(rule.commands());
        return dto;
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
