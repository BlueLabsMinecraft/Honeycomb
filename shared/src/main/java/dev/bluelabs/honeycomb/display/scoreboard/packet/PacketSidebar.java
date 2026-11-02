package dev.bluelabs.honeycomb.display.scoreboard.packet;

import dev.bluelabs.honeycomb.display.scoreboard.Sidebar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws a sidebar by sending scoreboard packets straight to one player.
 *
 * <p>Folia has no private scoreboards: its scoreboard manager throws
 * {@link UnsupportedOperationException}. Packets do not need a board, only a
 * connection, so this class writes the objective, the display slot and the rows
 * over the wire instead. Every lookup is reflective and cached, and version
 * differences are handled by trying the known shapes in order, which keeps the
 * same code working on 1.21.x and on 26.x.</p>
 */
public final class PacketSidebar implements Sidebar {

    private static final String OBJECTIVE = "honeycomb";
    private static final int MAX_ROWS = 15;
    private static final int MODE_ADD = 0;
    private static final int MODE_REMOVE = 1;
    private static final int MODE_CHANGE = 2;

    private static final Support SUPPORT = Support.detect();

    private final Object connection;
    private final boolean hideNumbers;
    private final List<String> owners = new ArrayList<>();
    private final List<Component> rendered = new ArrayList<>();
    private final Object board;
    private Object objective;
    private Component title;
    private boolean added;
    private boolean reported;

    /**
     * @return true when this server has everything needed to send scoreboard packets
     */
    public static boolean available() {
        return SUPPORT != null;
    }

    /**
     * @return why packets are unavailable, empty when they are
     */
    public static String unavailableReason() {
        return SUPPORT == null ? Support.problem : "";
    }

    /**
     * @param player      the player to draw for
     * @param hideNumbers whether score numbers should be hidden
     * @throws ReflectiveOperationException when the server classes do not match
     */
    public PacketSidebar(Player player, boolean hideNumbers) throws ReflectiveOperationException {
        this.connection = SUPPORT.connectionOf(player);
        this.hideNumbers = hideNumbers;
        this.board = SUPPORT.newBoard();
    }

    @Override
    public void update(Component newTitle, List<Component> newLines) {
        try {
            if (!added || !newTitle.equals(title)) {
                rebuild(newTitle);
            }
            apply(newLines);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report(failure);
        }
    }

    @Override
    public void dispose() {
        try {
            if (objective != null) {
                SUPPORT.send(connection, SUPPORT.objectivePacket(objective, MODE_REMOVE));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The player is leaving anyway.
        }
        owners.clear();
        rendered.clear();
        objective = null;
        added = false;
    }

    private void rebuild(Component newTitle) throws ReflectiveOperationException {
        this.title = newTitle;
        this.objective = SUPPORT.objective(board, OBJECTIVE, newTitle);
        SUPPORT.send(connection, SUPPORT.objectivePacket(objective, added ? MODE_CHANGE : MODE_ADD));
        SUPPORT.send(connection, SUPPORT.displayPacket(objective));
        added = true;
        rendered.clear();
        owners.clear();
    }

    private void apply(List<Component> lines) throws ReflectiveOperationException {
        int wanted = Math.min(lines.size(), MAX_ROWS);
        for (int index = wanted; index < owners.size(); index++) {
            SUPPORT.resetScore(connection, owners.get(index), OBJECTIVE);
        }
        while (owners.size() > wanted) {
            owners.remove(owners.size() - 1);
            rendered.remove(rendered.size() - 1);
        }
        for (int index = 0; index < wanted; index++) {
            Component line = lines.get(index);
            if (index < owners.size() && line.equals(rendered.get(index))) {
                continue;
            }
            if (index >= owners.size()) {
                owners.add("hcr" + index);
                rendered.add(line);
            } else {
                rendered.set(index, line);
            }
            SUPPORT.send(connection, SUPPORT.scorePacket(owners.get(index), OBJECTIVE,
                    MAX_ROWS - index, line, hideNumbers ? SUPPORT.blankFormat : null));
        }
    }

    private void report(Throwable failure) {
        if (reported) {
            return;
        }
        reported = true;
        Bukkit.getLogger().warning("[Honeycomb] Scoreboard packets failed: "
                + failure.getClass().getName() + ": " + failure.getMessage());
    }

    /** Cached reflective view of the server classes this needs. */
    private static final class Support {

        private static String problem = "not detected";
        private static Method sendMethod;

        private final Class<?> packetClass;
        private final Class<?> scoreboardClass;
        private final Class<?> objectivePacketClass;
        private final Class<?> displayPacketClass;
        private final Class<?> scorePacketClass;
        private final Class<?> resetPacketClass;
        private final Object sidebarSlot;
        private final Object criteria;
        private final Object renderType;
        private final Method asVanilla;
        private final Object blankFormat;

        private Support(Class<?> packetClass, Class<?> scoreboardClass, Class<?> objectivePacketClass,
                        Class<?> displayPacketClass, Class<?> scorePacketClass, Class<?> resetPacketClass,
                        Object sidebarSlot, Object criteria, Object renderType, Method asVanilla,
                        Object blankFormat) {
            this.packetClass = packetClass;
            this.scoreboardClass = scoreboardClass;
            this.objectivePacketClass = objectivePacketClass;
            this.displayPacketClass = displayPacketClass;
            this.scorePacketClass = scorePacketClass;
            this.resetPacketClass = resetPacketClass;
            this.sidebarSlot = sidebarSlot;
            this.criteria = criteria;
            this.renderType = renderType;
            this.asVanilla = asVanilla;
            this.blankFormat = blankFormat;
        }

        private static Support detect() {
            try {
                Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> scoreboard = Class.forName("net.minecraft.world.scores.Scoreboard");
                Class<?> objective = Class.forName("net.minecraft.world.scores.Objective");
                Class<?> objectivePacket =
                        Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");
                Class<?> displayPacket =
                        Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
                Class<?> scorePacket =
                        Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket");
                Class<?> resetPacket = optional("net.minecraft.network.protocol.game.ClientboundResetScorePacket");
                Object slot = enumConstant(Class.forName("net.minecraft.world.scores.DisplaySlot"), "SIDEBAR");
                Object criteria = criteriaDummy();
                Object renderType = renderType();
                Method asVanilla = Class.forName("io.papermc.paper.adventure.PaperAdventure")
                        .getMethod("asVanilla", Component.class);
                Object blankFormat = blankInstance();
                // Touch the objective class once so a missing class fails here and not per tick.
                objective.getName();
                return new Support(packet, scoreboard, objectivePacket, displayPacket, scorePacket,
                        resetPacket, slot, criteria, renderType, asVanilla, blankFormat);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                return null;
            }
        }

        private static Class<?> optional(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException absent) {
                return null;
            }
        }

        private static Object blankInstance() {
            try {
                Field instance = Class.forName("net.minecraft.network.chat.numbers.BlankFormat")
                        .getField("INSTANCE");
                return instance.get(null);
            } catch (ReflectiveOperationException | RuntimeException absent) {
                return null;
            }
        }

        private static Object enumConstant(Class<?> type, String name) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
            throw new IllegalStateException("No " + name + " on " + type.getSimpleName());
        }

        private static Object criteriaDummy() throws ReflectiveOperationException {
            for (String name : new String[]{"net.minecraft.world.scores.criteria.ObjectiveCriteria",
                    "net.minecraft.world.scores.ScoreCriteria"}) {
                try {
                    return Class.forName(name).getField("DUMMY").get(null);
                } catch (ClassNotFoundException | NoSuchFieldException absent) {
                    // Try the next name.
                }
            }
            throw new ClassNotFoundException("score criteria");
        }

        private static Object renderType() {
            try {
                Class<?> type = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals("INTEGER")) {
                        return constant;
                    }
                }
                Object[] constants = type.getEnumConstants();
                return constants.length == 0 ? null : constants[0];
            } catch (ReflectiveOperationException | RuntimeException absent) {
                return null;
            }
        }

        private Object newBoard() throws ReflectiveOperationException {
            return scoreboardClass.getConstructor().newInstance();
        }

        /**
         * Builds an objective. The server's own factory method is used when it exists,
         * because it hides the constructor changes between versions.
         *
         * @param board   the private data holder
         * @param name    objective name
         * @param display title component
         * @return the objective
         * @throws ReflectiveOperationException when no shape matches
         */
        private Object objective(Object board, String name, Component display)
                throws ReflectiveOperationException {
            Object component = component(display);
            Object[][] attempts = {
                    {name, criteria, component, renderType, Boolean.FALSE, null},
                    {name, criteria, component, renderType, Boolean.FALSE},
                    {name, criteria, component, renderType},
                    {name, criteria, component},
            };
            ReflectiveOperationException last = null;
            for (Object[] args : attempts) {
                try {
                    Method factory = factory(args.length);
                    Object created = factory.invoke(board, args);
                    if (created != null) {
                        return created;
                    }
                } catch (ReflectiveOperationException | RuntimeException mismatch) {
                    last = mismatch instanceof ReflectiveOperationException roe
                            ? roe : new ReflectiveOperationException(mismatch);
                }
            }
            throw last == null ? new NoSuchMethodException("addObjective") : last;
        }

        private Method factory(int arguments) throws NoSuchMethodException {
            for (Method method : scoreboardClass.getMethods()) {
                if (method.getName().equals("addObjective") && method.getParameterCount() == arguments) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new NoSuchMethodException("addObjective/" + arguments);
        }

        private Object objectivePacket(Object objective, int mode) throws ReflectiveOperationException {
            return instantiate(objectivePacketClass, new Object[]{objective, mode});
        }

        private Object displayPacket(Object objective) throws ReflectiveOperationException {
            return instantiate(displayPacketClass, new Object[]{sidebarSlot, objective});
        }

        private Object scorePacket(String owner, String objectiveName, int score,
                                   Component display, Object numberFormat)
                throws ReflectiveOperationException {
            Object component = component(display);
            Object[][] attempts = {
                    {owner, objectiveName, score, Optional.ofNullable(component), Optional.ofNullable(numberFormat)},
                    {owner, objectiveName, score, Optional.ofNullable(component), Optional.empty()},
                    {owner, objectiveName, score, Optional.ofNullable(component)},
                    {owner, objectiveName, score, component, numberFormat},
                    {owner, objectiveName, score, component},
                    {owner, objectiveName, score},
            };
            ReflectiveOperationException last = null;
            for (Object[] args : attempts) {
                try {
                    return instantiate(scorePacketClass, args);
                } catch (ReflectiveOperationException mismatch) {
                    last = mismatch;
                }
            }
            throw last == null ? new NoSuchMethodException("Score") : last;
        }

        private void resetScore(Object connection, String owner, String objectiveName) {
            if (resetPacketClass == null) {
                return;
            }
            try {
                send(connection, instantiate(resetPacketClass, new Object[]{owner, objectiveName}));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A stale row is better than losing the board.
            }
        }

        private Object component(Component display) throws ReflectiveOperationException {
            return asVanilla.invoke(null, display);
        }

        private void send(Object connection, Object packet) throws ReflectiveOperationException {
            Method method = sendMethod != null ? sendMethod : findSend(connection.getClass());
            if (method == null) {
                throw new NoSuchMethodException("send on " + connection.getClass().getSimpleName());
            }
            sendMethod = method;
            try {
                method.invoke(connection, packet);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                throw new ReflectiveOperationException(cause == null ? failure : cause);
            }
        }

        private Method findSend(Class<?> connectionType) throws ReflectiveOperationException {
            Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
            for (Class<?> type = connectionType; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals("send") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(packet)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
            return null;
        }

        private Object connectionOf(Player player) throws ReflectiveOperationException {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            for (Class<?> type = handle.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    String fieldType = field.getType().getName();
                    if (!fieldType.endsWith("PacketListenerImpl") && !fieldType.endsWith(".Connection")) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(handle);
                    if (value != null) {
                        return value;
                    }
                }
            }
            throw new IllegalStateException("No connection on " + handle.getClass().getName());
        }

        private static Object instantiate(Class<?> type, Object[] args) throws ReflectiveOperationException {
            ReflectiveOperationException last = null;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length != args.length) {
                    continue;
                }
                boolean fits = true;
                for (int index = 0; index < parameters.length; index++) {
                    Object arg = args[index];
                    if (arg == null) {
                        if (parameters[index].isPrimitive()) {
                            fits = false;
                            break;
                        }
                        continue;
                    }
                    if (!box(parameters[index]).isInstance(arg)) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) {
                    continue;
                }
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(args);
                } catch (ReflectiveOperationException mismatch) {
                    last = mismatch;
                }
            }
            throw last == null ? new NoSuchMethodException(type.getSimpleName()) : last;
        }

        private static Class<?> box(Class<?> type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (type == int.class) {
                return Integer.class;
            }
            if (type == boolean.class) {
                return Boolean.class;
            }
            if (type == double.class) {
                return Double.class;
            }
            if (type == float.class) {
                return Float.class;
            }
            if (type == long.class) {
                return Long.class;
            }
            if (type == short.class) {
                return Short.class;
            }
            if (type == byte.class) {
                return Byte.class;
            }
            if (type == char.class) {
                return Character.class;
            }
            return Boolean.class;
        }
    }
}
