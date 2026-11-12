package com.epicplayera10.iaedit.hook.process;

import com.sk89q.worldedit.extension.platform.Actor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PasteTracker {

    private static final long EXPIRY_MILLIS = 60_000L;
    private static final Map<UUID, Long> PASTING = new ConcurrentHashMap<>();
    private static volatile Set<String> commands = Set.of("paste");

    private PasteTracker() {}

    public static void commands(List<String> names) {
        commands = names.stream().map(PasteTracker::normalize).filter(name -> !name.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public static void onCommand(Actor actor, String arguments) {
        if (actor == null || actor.getUniqueId() == null) return;
        if (commands.contains(normalize(arguments))) {
            PASTING.put(actor.getUniqueId(), System.currentTimeMillis());
        } else {
            PASTING.remove(actor.getUniqueId());
        }
    }

    public static boolean consume(Actor actor) {
        if (actor.getUniqueId() == null) return false;
        Long since = PASTING.remove(actor.getUniqueId());
        return since != null && System.currentTimeMillis() - since <= EXPIRY_MILLIS;
    }

    public static void clear() {
        PASTING.clear();
    }

    private static String normalize(String input) {
        String trimmed = input == null ? "" : input.trim();
        int space = trimmed.indexOf(' ');
        String name = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
        while (name.startsWith("/")) name = name.substring(1);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
