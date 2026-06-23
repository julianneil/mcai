package com.modai.mcai.client.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class QuestContextProvider {
    private static final int MAX_ACTIVE_QUESTS = 10;
    private static final int MAX_PINNED_QUESTS = 8;
    private static final int MAX_NEXT_QUESTS = 5;

    public String buildContext() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "FTB Quests context unavailable: no player loaded.";
        }

        try {
            Object questFile = clientQuestFile();
            if (questFile == null) {
                return "FTB Quests context unavailable: quest file not loaded.";
            }

            Object teamData = teamData(player);
            if (teamData == null) {
                return "FTB Quests context unavailable: no team data for the current player.";
            }

            Map<Long, Object> quests = new LinkedHashMap<>();
            List<Object> chapters = new ArrayList<>();
            collectQuestsAndChapters(questFile, quests, chapters);

            StringBuilder context = new StringBuilder();
            context.append("FTB Quests context:\n");
            context.append("Team: ").append(stringValue(invoke(teamData, "getName"))).append('\n');
            context.append("Chapters: ").append(chapters.size()).append('\n');
            context.append("Quests: ").append(quests.size()).append('\n');

            long started = quests.values().stream().filter(quest -> booleanValue(quest, "isStarted", teamData)).count();
            long completed = quests.values().stream().filter(quest -> booleanValue(quest, "isCompleted", teamData) || booleanValue(quest, "isCompletedRaw", teamData)).count();
            context.append("Started quests: ").append(started).append('\n');
            context.append("Completed quests: ").append(completed).append('\n');

            List<String> pinned = pinnedQuests(teamData, quests);
            context.append("Pinned quests: ").append(pinned.size()).append('\n');
            if (!pinned.isEmpty()) {
                context.append("Pinned list:\n");
                for (int i = 0; i < Math.min(pinned.size(), MAX_PINNED_QUESTS); i++) {
                    context.append("- ").append(pinned.get(i)).append('\n');
                }
            }

            List<String> active = activeQuests(quests, teamData);
            if (!active.isEmpty()) {
                context.append("Active quests:\n");
                for (int i = 0; i < Math.min(active.size(), MAX_ACTIVE_QUESTS); i++) {
                    context.append("- ").append(active.get(i)).append('\n');
                }
            }

            List<String> next = nextProgressions(quests, teamData);
            if (!next.isEmpty()) {
                context.append("Next progression suggestions:\n");
                for (String line : next) {
                    context.append("- ").append(line).append('\n');
                }
            }

            return context.toString().trim();
        } catch (ReflectiveOperationException error) {
            return "FTB Quests context unavailable: " + error.getClass().getSimpleName();
        }
    }

    public String buildSummary() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "FTB Quests unavailable: no player loaded.";
        }

        try {
            Object questFile = clientQuestFile();
            if (questFile == null) {
                return "FTB Quests unavailable: quest file not loaded.";
            }

            Object teamData = teamData(player);
            if (teamData == null) {
                return "FTB Quests unavailable: no team data for the current player.";
            }

            Map<Long, Object> quests = new LinkedHashMap<>();
            List<Object> chapters = new ArrayList<>();
            collectQuestsAndChapters(questFile, quests, chapters);

            long started = quests.values().stream().filter(quest -> booleanValue(quest, "isStarted", teamData)).count();
            long completed = quests.values().stream().filter(quest -> booleanValue(quest, "isCompleted", teamData) || booleanValue(quest, "isCompletedRaw", teamData)).count();
            long pinned = pinnedQuestIds(teamData).size();

            return "FTB Quests: " + chapters.size() + " chapters, " + quests.size() + " quests, " + started + " started, " + completed + " completed, " + pinned + " pinned.";
        } catch (ReflectiveOperationException error) {
            return "FTB Quests unavailable: " + error.getClass().getSimpleName();
        }
    }

    public String buildNextProgressionSummary() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "FTB Quests unavailable: no player loaded.";
        }

        try {
            Object questFile = clientQuestFile();
            if (questFile == null) {
                return "FTB Quests unavailable: quest file not loaded.";
            }

            Object teamData = teamData(player);
            if (teamData == null) {
                return "FTB Quests unavailable: no team data for the current player.";
            }

            Map<Long, Object> quests = new LinkedHashMap<>();
            List<Object> chapters = new ArrayList<>();
            collectQuestsAndChapters(questFile, quests, chapters);

            List<String> next = nextProgressions(quests, teamData);
            if (next.isEmpty()) {
                return "FTB Quests: no clear next progression found.";
            }

            StringBuilder context = new StringBuilder();
            context.append("FTB Quests next steps:\n");
            for (String line : next) {
                context.append("- ").append(line).append('\n');
            }
            return context.toString().trim();
        } catch (ReflectiveOperationException error) {
            return "FTB Quests unavailable: " + error.getClass().getSimpleName();
        }
    }

    private void collectQuestsAndChapters(Object questFile, Map<Long, Object> quests, List<Object> chapters) throws ReflectiveOperationException {
        invoke(questFile, "forAllChapters", (Consumer<Object>) chapters::add);
        invoke(questFile, "forAllQuests", (Consumer<Object>) quest -> {
            try {
                long id = longValue(quest, "getMovableID");
                quests.put(id, quest);
            } catch (ReflectiveOperationException ignored) {
                // No-op.
            }
        });
    }

    private List<String> pinnedQuests(Object teamData, Map<Long, Object> quests) throws ReflectiveOperationException {
        List<String> pinned = new ArrayList<>();
        for (Object rawId : pinnedQuestIds(teamData)) {
            long id = ((Number) rawId).longValue();
            Object quest = quests.get(id);
            if (quest == null) {
                continue;
            }
            pinned.add(questLine(quest, teamData));
        }
        return pinned;
    }

    private List<Object> pinnedQuestIds(Object teamData) throws ReflectiveOperationException {
        Object player = Minecraft.getInstance().player;
        Object ids = invoke(teamData, "getPinnedQuestIds", player);
        List<Object> values = new ArrayList<>();
        if (ids instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> activeQuests(Map<Long, Object> quests, Object teamData) throws ReflectiveOperationException {
        List<String> active = new ArrayList<>();
        for (Object quest : quests.values()) {
            if (!booleanValue(quest, "isVisible", teamData)) {
                continue;
            }
            if (booleanValue(quest, "isCompleted", teamData) || booleanValue(quest, "isCompletedRaw", teamData)) {
                continue;
            }
            active.add(questLine(quest, teamData));
        }
        return active;
    }

    private List<String> nextProgressions(Map<Long, Object> quests, Object teamData) throws ReflectiveOperationException {
        List<Object> pinnedIds = pinnedQuestIds(teamData);
        List<QuestSuggestion> suggestions = new ArrayList<>();

        for (Object quest : quests.values()) {
            if (!booleanValue(quest, "isVisible", teamData)) {
                continue;
            }

            boolean completed = booleanValue(quest, "isCompleted", teamData) || booleanValue(quest, "isCompletedRaw", teamData);
            if (completed) {
                continue;
            }

            boolean started = booleanValue(quest, "isStarted", teamData);
            boolean pinned = isPinned(quest, pinnedIds);
            boolean dependenciesComplete = booleanValue(teamData, "areDependenciesComplete", quest);
            boolean dependenciesVisible = booleanValue(teamData, "areDependenciesVisible", quest);
            boolean canStartTasks = booleanValue(teamData, "canStartTasks", quest);
            long progress = safeLongValue(teamData, 0L, "getRelativeProgress", quest);
            long maxProgress = safeLongValue(quest, 0L, "getMaxProgress");

            int score = 0;
            if (pinned) {
                score += 40;
            }
            if (started) {
                score += 30;
            }
            if (canStartTasks) {
                score += 25;
            }
            if (dependenciesComplete) {
                score += 20;
            }
            if (dependenciesVisible) {
                score += 10;
            }
            if (maxProgress > 0 && progress > 0) {
                score += Math.min(10, (int) ((progress * 10) / maxProgress));
            }
            if (!started && !canStartTasks && !dependenciesComplete) {
                score -= 20;
            }

            String reason = nextProgressReason(started, pinned, dependenciesComplete, canStartTasks, dependenciesVisible, progress, maxProgress);
            suggestions.add(new QuestSuggestion(score, questLine(quest, teamData), reason));
        }

        suggestions.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.score(), left.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.description().compareToIgnoreCase(right.description());
        });

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(suggestions.size(), MAX_NEXT_QUESTS); i++) {
            QuestSuggestion suggestion = suggestions.get(i);
            lines.add(suggestion.description() + " - " + suggestion.reason());
        }
        return lines;
    }

    private String questLine(Object quest, Object teamData) throws ReflectiveOperationException {
        String title = stringValue(invoke(quest, "getAltTitle"));
        if (title.isBlank()) {
            title = stringValue(invoke(quest, "getButtonText"));
        }
        Object chapter = invoke(quest, "getQuestChapter");
        String chapterTitle = chapter == null ? "" : stringValue(invoke(chapter, "getAltTitle"));
        long id = longValue(quest, "getMovableID");
        long progress = longValue(teamData, "getRelativeProgress", quest);
        long maxProgress = longValue(quest, "getMaxProgress");
        if (maxProgress <= 0) {
            return (chapterTitle.isBlank() ? title : chapterTitle + " -> " + title) + " (#" + id + ")";
        }
        return (chapterTitle.isBlank() ? title : chapterTitle + " -> " + title) + " (" + progress + "/" + maxProgress + ", #" + id + ")";
    }

    private boolean isPinned(Object quest, List<Object> pinnedIds) throws ReflectiveOperationException {
        long id = longValue(quest, "getMovableID");
        for (Object pinnedId : pinnedIds) {
            if (pinnedId instanceof Number number && number.longValue() == id) {
                return true;
            }
        }
        return false;
    }

    private String nextProgressReason(boolean started, boolean pinned, boolean dependenciesComplete, boolean canStartTasks, boolean dependenciesVisible, long progress, long maxProgress) {
        List<String> reasons = new ArrayList<>();
        if (pinned) {
            reasons.add("pinned");
        }
        if (started) {
            reasons.add("already started");
        }
        if (canStartTasks) {
            reasons.add("ready now");
        } else if (dependenciesComplete) {
            reasons.add("dependencies complete");
        } else if (dependenciesVisible) {
            reasons.add("dependencies visible");
        } else {
            reasons.add("probably locked");
        }
        if (maxProgress > 0 && progress > 0) {
            reasons.add(progress + "/" + maxProgress + " progress");
        }
        return String.join(", ", reasons);
    }

    private long safeLongValue(Object target, long fallback, String methodName, Object... args) {
        try {
            return longValue(target, methodName, args);
        } catch (ReflectiveOperationException error) {
            return fallback;
        }
    }

    private Object clientQuestFile() throws ReflectiveOperationException {
        Class<?> clientQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
        Field instanceField = clientQuestFileClass.getField("INSTANCE");
        return instanceField.get(null);
    }

    private Object teamData(Player player) throws ReflectiveOperationException {
        Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
        Method getMethod = teamDataClass.getMethod("get", Player.class);
        return getMethod.invoke(null, player);
    }

    private void invoke(Object target, String methodName, Consumer<Object> consumer) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, 1);
        method.invoke(target, consumer);
    }

    private Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args.length);
        return method.invoke(target, args);
    }

    private Method findMethod(Class<?> type, String methodName, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName + " with " + parameterCount + " parameters");
    }

    private boolean booleanValue(Object target, String methodName, Object... args) {
        try {
            Object value = invoke(target, methodName, args);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private long longValue(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Object value = invoke(target, methodName, args);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private record QuestSuggestion(int score, String description, String reason) {
    }
}
