package net.dvmn2.commanderplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** On-disk form of one rule. Any field may be missing or null in a hand-edited file. */
public final class RuleDto {

    public String name;
    public boolean enabled = true;
    public List<String> events = new ArrayList<>();
    public double delaySeconds;
    public String permission = "";
    public List<String> worlds = new ArrayList<>();
    public List<String> commands = new ArrayList<>();
}
