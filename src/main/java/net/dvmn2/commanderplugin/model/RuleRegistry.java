package net.dvmn2.commanderplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable set of rules indexed by name and by event.
 *
 * <p>Safe to read from any thread. Edits create a new registry and keep the creation order.
 */
public final class RuleRegistry {

    private static final RuleRegistry EMPTY = new RuleRegistry(new LinkedHashMap<>());

    private final Map<String, Rule> byName;
    private final List<Rule> all;
    private final Map<TriggerEvent, List<Rule>> byEvent;

    private RuleRegistry(Map<String, Rule> rules) {
        this.byName = Collections.unmodifiableMap(rules);
        this.all = List.copyOf(rules.values());
        EnumMap<TriggerEvent, List<Rule>> index = new EnumMap<>(TriggerEvent.class);
        for (Rule rule : all) {
            for (TriggerEvent event : rule.events()) {
                index.computeIfAbsent(event, key -> new ArrayList<>()).add(rule);
            }
        }
        index.replaceAll((event, list) -> List.copyOf(list));
        this.byEvent = Collections.unmodifiableMap(index);
    }

    public static RuleRegistry empty() {
        return EMPTY;
    }

    /** Builds a registry; a later rule replaces an earlier rule with the same name. */
    public static RuleRegistry of(Collection<Rule> rules) {
        Map<String, Rule> map = new LinkedHashMap<>();
        for (Rule rule : rules) {
            map.put(rule.name(), rule);
        }
        return new RuleRegistry(map);
    }

    /** Finds a rule by its (already lowercase) name. */
    @Nullable
    public Rule find(String name) {
        return byName.get(name);
    }

    /** Returns all rules in creation order. */
    public List<Rule> all() {
        return all;
    }

    /** Returns the rules listening to the event, in creation order. */
    public List<Rule> forEvent(TriggerEvent event) {
        return byEvent.getOrDefault(event, List.of());
    }

    public int size() {
        return all.size();
    }

    /** Returns a copy with the rule added, or replaced in place if the name exists. */
    public RuleRegistry with(Rule rule) {
        Map<String, Rule> copy = new LinkedHashMap<>(byName);
        copy.put(rule.name(), rule);
        return new RuleRegistry(copy);
    }

    /** Returns a copy without the rule. */
    public RuleRegistry without(String name) {
        Map<String, Rule> copy = new LinkedHashMap<>(byName);
        copy.remove(name);
        return new RuleRegistry(copy);
    }
}
