package net.dvmn2.commanderplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** On-disk form of {@code rules.json}. */
public final class RulesFileDto {

    public int version;
    public List<RuleDto> rules = new ArrayList<>();
}
