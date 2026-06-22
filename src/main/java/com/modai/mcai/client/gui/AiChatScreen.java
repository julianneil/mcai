package com.modai.mcai.client.gui;

import com.modai.mcai.Config;
import com.modai.mcai.client.AiChatManager;
import com.modai.mcai.client.OllamaClient.AiMessage;
import com.modai.mcai.client.recipe.RecipeTracker;
import com.modai.mcai.client.recipe.RecipeTracker.HighlightRole;
import com.modai.mcai.client.recipe.RecipeTracker.RecipeTreeNode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class AiChatScreen extends Screen {
    private static final int PADDING = 16;
    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int CLEAR_BUTTON_WIDTH = 54;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_INSET = 8;
    private static final int SCROLL_STEP_LINES = 3;
    private static final int RECIPE_TREE_MAX_LINES = 7;
    private static final int RECIPE_TREE_INDENT = 10;

    private final List<Component> transcript = new ArrayList<>();
    private final List<FormattedCharSequence> wrappedTranscript = new ArrayList<>();

    private EditBox input;
    private Button sendButton;
    private Button clearButton;
    private boolean waitingForReply;
    private boolean transcriptDirty = true;
    private int cachedLineWidth = -1;
    private int scrollOffsetLines;

    public AiChatScreen() {
        super(Component.translatable("screen.mcai.ai_chat"));
    }

    @Override
    protected void init() {
        int inputY = this.height - PADDING - INPUT_HEIGHT;
        int inputWidth = this.width - (PADDING * 2) - BUTTON_WIDTH - BUTTON_GAP;

        input = new EditBox(this.font, PADDING, inputY, inputWidth, INPUT_HEIGHT, Component.translatable("screen.mcai.ai_chat.input"));
        input.setMaxLength(1024);
        input.setHint(Component.translatable("screen.mcai.ai_chat.input_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(input);

        sendButton = Button.builder(Component.translatable("screen.mcai.ai_chat.send"), button -> sendMessage())
                .bounds(PADDING + inputWidth + BUTTON_GAP, inputY, BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        addRenderableWidget(sendButton);

        clearButton = Button.builder(Component.translatable("screen.mcai.ai_chat.clear"), button -> clearConversation())
                .bounds(this.width - PADDING - CLEAR_BUTTON_WIDTH, PADDING - 2, CLEAR_BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        addRenderableWidget(clearButton);

        setInitialFocus(input);
        refreshTranscript();
        updateControls();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int panelLeft = PADDING;
        int panelTop = PADDING + 34;
        int panelRight = this.width - PADDING;
        int panelBottom = this.height - PADDING - INPUT_HEIGHT - 10;
        int treeBottom = renderRecipeTreePanel(guiGraphics, panelLeft, panelTop, panelRight);
        int transcriptTop = treeBottom + (treeBottom > panelTop ? 6 : 0);
        int textX = panelLeft + PANEL_INSET;
        int textY = transcriptTop + PANEL_INSET;
        int lineWidth = panelRight - panelLeft - (PANEL_INSET * 2) - 6;

        guiGraphics.fill(panelLeft, transcriptTop, panelRight, panelBottom, 0xD0101010);
        guiGraphics.renderOutline(panelLeft, transcriptTop, panelRight - panelLeft, panelBottom - transcriptTop, 0xFF666666);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        rebuildWrappedTranscriptIfNeeded(lineWidth);
        renderTranscript(guiGraphics, textX, textY, panelBottom - PANEL_INSET);
        renderScrollbar(guiGraphics, panelRight - 5, transcriptTop + PANEL_INSET, panelBottom - PANEL_INSET);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF);
        guiGraphics.drawString(this.font, statusText(), PADDING, PADDING + 14, 0xB8B8B8, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input.isFocused() && keyCode == 257) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = maxScrollOffset();
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (scrollY > 0) {
            scrollOffsetLines = Math.min(maxScroll, scrollOffsetLines + SCROLL_STEP_LINES);
        } else if (scrollY < 0) {
            scrollOffsetLines = Math.max(0, scrollOffsetLines - SCROLL_STEP_LINES);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendMessage() {
        if (waitingForReply || input == null) {
            return;
        }

        String message = input.getValue().trim();
        if (message.isEmpty()) {
            return;
        }

        input.setValue("");
        waitingForReply = true;
        addTranscriptLine(Component.literal("You: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
        addTranscriptLine(Component.literal("MCAI is thinking...").withStyle(ChatFormatting.YELLOW));
        updateControls();

        AiChatManager.get().ask(message,
                reply -> {
                    removeThinkingLine();
                    addTranscriptLine(Component.literal("MCAI: ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(reply).withStyle(ChatFormatting.WHITE)));
                    waitingForReply = false;
                    updateControls();
                },
                error -> {
                    removeThinkingLine();
                    addTranscriptLine(Component.literal("MCAI error: ").withStyle(ChatFormatting.RED)
                            .append(Component.literal(errorText(error)).withStyle(ChatFormatting.WHITE)));
                    waitingForReply = false;
                    updateControls();
                });
    }

    private void clearConversation() {
        if (waitingForReply) {
            return;
        }

        AiChatManager.get().clearHistory();
        transcript.clear();
        markTranscriptDirty();
        updateControls();
    }

    private String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private void refreshTranscript() {
        transcript.clear();
        for (AiMessage message : AiChatManager.get().getHistory()) {
            if ("user".equals(message.role())) {
                transcript.add(Component.literal("You: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(message.content()).withStyle(ChatFormatting.WHITE)));
            } else if ("assistant".equals(message.role())) {
                transcript.add(Component.literal("MCAI: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(message.content()).withStyle(ChatFormatting.WHITE)));
            }
        }
        markTranscriptDirty();
    }

    private void addTranscriptLine(Component line) {
        transcript.add(line);
        markTranscriptDirty();
    }

    private void removeThinkingLine() {
        if (!transcript.isEmpty()) {
            Component lastLine = transcript.get(transcript.size() - 1);
            if (lastLine.getString().equals("MCAI is thinking...")) {
                transcript.remove(transcript.size() - 1);
                markTranscriptDirty();
            }
        }
    }

    private void rebuildWrappedTranscriptIfNeeded(int lineWidth) {
        if (!transcriptDirty && cachedLineWidth == lineWidth) {
            return;
        }

        wrappedTranscript.clear();
        for (Component line : transcript) {
            wrappedTranscript.addAll(this.font.split(line, lineWidth));
        }
        cachedLineWidth = lineWidth;
        transcriptDirty = false;
        scrollOffsetLines = Math.min(scrollOffsetLines, maxScrollOffset());
    }

    private int renderRecipeTreePanel(GuiGraphics guiGraphics, int left, int top, int right) {
        RecipeTracker tracker = RecipeTracker.get();
        if (tracker.recipeTree().isEmpty()) {
            return top;
        }

        List<TreeLine> treeLines = new ArrayList<>();
        collectTreeLines(tracker.recipeTree().get(), 0, treeLines);
        int visibleLines = Math.min(treeLines.size(), RECIPE_TREE_MAX_LINES);
        int panelHeight = PANEL_INSET + this.font.lineHeight + 4 + (visibleLines * this.font.lineHeight) + PANEL_INSET;
        int bottom = top + panelHeight;

        guiGraphics.fill(left, top, right, bottom, 0xD0181818);
        guiGraphics.renderOutline(left, top, right - left, panelHeight, 0xFF6A6A6A);
        guiGraphics.drawString(this.font, Component.literal("Tracked recipe tree").withStyle(ChatFormatting.YELLOW), left + PANEL_INSET, top + PANEL_INSET, 0xFFFFF27A, false);

        int y = top + PANEL_INSET + this.font.lineHeight + 4;
        for (int i = 0; i < visibleLines; i++) {
            TreeLine line = treeLines.get(i);
            int x = left + PANEL_INSET + (line.depth() * RECIPE_TREE_INDENT);
            Component prefix = Component.literal(line.depth() == 0 ? "» " : "- ").withStyle(ChatFormatting.DARK_GRAY);
            Component row = prefix.copy()
                    .append(line.node().name().copy().withStyle(line.node().role().color()))
                    .append(Component.literal(roleSuffix(line.node().role())).withStyle(ChatFormatting.GRAY));
            guiGraphics.drawString(this.font, row, x, y, roleTextColor(line.node().role()), false);
            y += this.font.lineHeight;
        }

        if (treeLines.size() > visibleLines) {
            Component more = Component.literal("+ " + (treeLines.size() - visibleLines) + " more ingredients...").withStyle(ChatFormatting.DARK_GRAY);
            guiGraphics.drawString(this.font, more, left + PANEL_INSET, bottom - PANEL_INSET - this.font.lineHeight, 0xAAAAAA, false);
        }

        return bottom;
    }

    private void collectTreeLines(RecipeTreeNode node, int depth, List<TreeLine> lines) {
        if (lines.size() >= RECIPE_TREE_MAX_LINES + 1) {
            return;
        }

        lines.add(new TreeLine(node, depth));
        for (RecipeTreeNode child : node.children()) {
            collectTreeLines(child, depth + 1, lines);
            if (lines.size() >= RECIPE_TREE_MAX_LINES + 1) {
                return;
            }
        }
    }

    private String roleSuffix(HighlightRole role) {
        return switch (role) {
            case TARGET -> "  target";
            case INTERMEDIATE -> "  crafted";
            case BASE -> "  base";
        };
    }

    private int roleTextColor(HighlightRole role) {
        return switch (role) {
            case TARGET -> 0xFF8EFFFF;
            case INTERMEDIATE -> 0xFFFFF27A;
            case BASE -> 0xFFA8FFB3;
        };
    }

    private void renderTranscript(GuiGraphics guiGraphics, int textX, int textY, int maxY) {
        if (wrappedTranscript.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("screen.mcai.ai_chat.empty").withStyle(ChatFormatting.DARK_GRAY), textX, textY, 0xAAAAAA, false);
            return;
        }

        int visibleLines = visibleLineCount(textY, maxY);
        int start = Math.max(0, wrappedTranscript.size() - visibleLines - scrollOffsetLines);
        int end = Math.min(wrappedTranscript.size(), start + visibleLines);
        int y = textY;

        for (int i = start; i < end && y < maxY; i++) {
            guiGraphics.drawString(this.font, wrappedTranscript.get(i), textX, y, 0xF0F0F0, false);
            y += this.font.lineHeight;
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int x, int top, int bottom) {
        int maxScroll = maxScrollOffset();
        if (maxScroll <= 0) {
            return;
        }

        int trackHeight = bottom - top;
        int visibleLines = Math.max(1, visibleLineCount(top, bottom));
        int totalLines = Math.max(visibleLines, wrappedTranscript.size());
        int thumbHeight = Math.max(12, trackHeight * visibleLines / totalLines);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = top + (maxScroll - scrollOffsetLines) * travel / maxScroll;

        guiGraphics.fill(x, top, x + 2, bottom, 0x66333333);
        guiGraphics.fill(x - 1, thumbTop, x + 3, thumbTop + thumbHeight, 0xFF888888);
    }

    private int visibleLineCount(int textY, int maxY) {
        return Math.max(1, (maxY - textY) / this.font.lineHeight);
    }

    private int maxScrollOffset() {
        int panelTop = PADDING + 34;
        int panelBottom = this.height - PADDING - INPUT_HEIGHT - 10;
        int treeBottom = RecipeTracker.get().recipeTree().isPresent()
                ? panelTop + recipeTreePanelHeight()
                : panelTop;
        int transcriptTop = treeBottom + (treeBottom > panelTop ? 6 : 0);
        int visibleLines = visibleLineCount(transcriptTop + PANEL_INSET, panelBottom - PANEL_INSET);
        return Math.max(0, wrappedTranscript.size() - visibleLines);
    }

    private int recipeTreePanelHeight() {
        RecipeTracker tracker = RecipeTracker.get();
        if (tracker.recipeTree().isEmpty()) {
            return 0;
        }

        List<TreeLine> treeLines = new ArrayList<>();
        collectTreeLines(tracker.recipeTree().get(), 0, treeLines);
        int visibleLines = Math.min(treeLines.size(), RECIPE_TREE_MAX_LINES);
        return PANEL_INSET + this.font.lineHeight + 4 + (visibleLines * this.font.lineHeight) + PANEL_INSET;
    }

    private void markTranscriptDirty() {
        transcriptDirty = true;
        scrollOffsetLines = 0;
    }

    private Component statusText() {
        RecipeTracker tracker = RecipeTracker.get();
        String status = "Model: " + Config.OLLAMA_MODEL.get()
                + " | Context: "
                + contextFlag("Inv", Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Player", Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Mods", Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Recipes", Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean());
        Component component = Component.literal(status);
        if (tracker.isTracking()) {
            component = component.copy()
                    .append(Component.literal(" | Tracking: "))
                    .append(tracker.targetName().copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" (" + tracker.highlightedItemCount() + " items)"));
        }
        return component;
    }

    private String contextFlag(String label, boolean enabled) {
        return label + " " + (enabled ? "on" : "off");
    }

    private void updateControls() {
        if (input != null) {
            input.active = !waitingForReply;
        }
        if (sendButton != null) {
            sendButton.active = !waitingForReply;
        }
        if (clearButton != null) {
            clearButton.active = !waitingForReply && !transcript.isEmpty();
        }
    }

    private record TreeLine(RecipeTreeNode node, int depth) {
    }
}
