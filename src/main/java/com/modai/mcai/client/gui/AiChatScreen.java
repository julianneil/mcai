package com.modai.mcai.client.gui;

import com.modai.mcai.Config;
import com.modai.mcai.client.AiChatManager;
import com.modai.mcai.client.OllamaClient.AiMessage;
import com.modai.mcai.client.recipe.JeiRecipeBridge;
import com.modai.mcai.client.recipe.RecipeTracker;
import com.modai.mcai.client.recipe.RecipeTracker.HighlightRole;
import com.modai.mcai.client.recipe.RecipeTracker.RecipeTreeNode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AiChatScreen extends Screen {
    private static final int PADDING = 16;
    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int CLEAR_BUTTON_WIDTH = 54;
    private static final int RETRY_BUTTON_WIDTH = 58;
    private static final int EXPORT_BUTTON_WIDTH = 60;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_INSET = 8;
    private static final int SCROLL_STEP_LINES = 3;
    private static final int RECIPE_BRANCH_ROW_GAP = 16;
    private static final int RECIPE_BRANCH_CHILD_GAP = 12;
    private static final int RECIPE_BRANCH_MAX_WIDTH = 220;

    private final LinkedList<Component> transcript = new LinkedList<>();
    private final List<FormattedCharSequence> wrappedTranscript = new ArrayList<>();
    private final List<BranchHitBox> branchHitBoxes = new ArrayList<>();

    private EditBox input;
    private Button sendButton;
    private Button clearButton;
    private Button retryButton;
    private Button exportButton;
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

        int clearX = this.width - PADDING - CLEAR_BUTTON_WIDTH;
        int exportX = clearX - BUTTON_GAP - EXPORT_BUTTON_WIDTH;
        int retryX = exportX - BUTTON_GAP - RETRY_BUTTON_WIDTH;

        retryButton = Button.builder(Component.translatable("screen.mcai.ai_chat.retry"), button -> retryConversation())
                .bounds(retryX, PADDING - 2, RETRY_BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        addRenderableWidget(retryButton);

        exportButton = Button.builder(Component.translatable("screen.mcai.ai_chat.export"), button -> exportConversation())
                .bounds(exportX, PADDING - 2, EXPORT_BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        addRenderableWidget(exportButton);

        clearButton = Button.builder(Component.translatable("screen.mcai.ai_chat.clear"), button -> clearConversation())
                .bounds(clearX, PADDING - 2, CLEAR_BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        addRenderableWidget(clearButton);

        setInitialFocus(input);
        refreshTranscript();
        updateControls();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        branchHitBoxes.clear();

        int panelLeft = PADDING;
        int panelTop = PADDING + 34;
        int panelRight = this.width - PADDING;
        int panelBottom = this.height - PADDING - INPUT_HEIGHT - 10;
        int textX = panelLeft + PANEL_INSET;
        int lineWidth = panelRight - panelLeft - (PANEL_INSET * 2) - 6;

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        int treeBottom = renderRecipeBranchPanel(guiGraphics, panelLeft, panelTop, panelRight);
        int transcriptTop = treeBottom + (treeBottom > panelTop ? 6 : 0);
        int textY = transcriptTop + PANEL_INSET;
        guiGraphics.fill(panelLeft, transcriptTop, panelRight, panelBottom, 0xD0101010);
        guiGraphics.renderOutline(panelLeft, transcriptTop, panelRight - panelLeft, panelBottom - transcriptTop, 0xFF666666);

        rebuildWrappedTranscriptIfNeeded(lineWidth);
        renderTranscript(guiGraphics, textX, textY, panelBottom - PANEL_INSET);
        renderScrollbar(guiGraphics, panelRight - 5, transcriptTop + PANEL_INSET, panelBottom - PANEL_INSET);
        renderBranchTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (BranchHitBox hitBox : branchHitBoxes) {
                if (!hitBox.contains(mouseX, mouseY)) {
                    continue;
                }

                if (!hitBox.stack().isEmpty()) {
                    JeiRecipeBridge.OpenResult result = JeiRecipeBridge.showRecipesFor(hitBox.stack());
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                                .append(result.message()), false);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void retryConversation() {
        if (waitingForReply || !AiChatManager.get().canRetryLastResponse()) {
            return;
        }

        removeLastAssistantLine();
        addTranscriptLine(Component.literal("MCAI is retrying the last reply...").withStyle(ChatFormatting.YELLOW));
        waitingForReply = true;
        updateControls();

        boolean started = AiChatManager.get().retryLastResponse(
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

        if (!started) {
            waitingForReply = false;
            removeThinkingLine();
            addTranscriptLine(Component.literal("MCAI: No assistant reply to retry yet.").withStyle(ChatFormatting.RED));
            updateControls();
        }
    }

    private void exportConversation() {
        if (waitingForReply) {
            return;
        }

        AiChatManager.HistoryExportResult result = AiChatManager.get().exportHistory();
        addTranscriptLine(result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
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
            Component lastLine = transcript.getLast();
            if (lastLine.getString().equals("MCAI is thinking...")) {
                transcript.removeLast();
                markTranscriptDirty();
            }
        }
    }

    private void removeLastAssistantLine() {
        if (transcript.isEmpty()) {
            return;
        }

        Component lastLine = transcript.getLast();
        String text = lastLine.getString();
        if (text.startsWith("MCAI: ")) {
            transcript.removeLast();
            markTranscriptDirty();
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

    private int renderRecipeBranchPanel(GuiGraphics guiGraphics, int left, int top, int right) {
        RecipeTracker tracker = RecipeTracker.get();
        if (tracker.recipeTree().isEmpty()) {
            return top;
        }

        branchHitBoxes.clear();

        BranchNode root = buildBranchNode(tracker.recipeTree().get(), 0);
        BranchLayout layout = measureBranchLayout(root);
        int titleHeight = this.font.lineHeight + 4;
        int panelHeight = PANEL_INSET + titleHeight + layout.height() + PANEL_INSET;
        int bottom = top + panelHeight;
        int panelWidth = right - left;
        int contentTop = top + PANEL_INSET + titleHeight;
        int originX = left + Math.max(PANEL_INSET, (panelWidth - layout.width()) / 2);

        guiGraphics.fill(left, top, right, bottom, 0xD0181818);
        guiGraphics.renderOutline(left, top, right - left, panelHeight, 0xFF6A6A6A);
        guiGraphics.drawString(this.font, Component.literal("Tracked recipe branch").withStyle(ChatFormatting.YELLOW), left + PANEL_INSET, top + PANEL_INSET, 0xFFFFF27A, false);
        guiGraphics.drawString(this.font, Component.literal("Hover a branch for details").withStyle(ChatFormatting.DARK_GRAY), right - PANEL_INSET - this.font.width("Hover a branch for details"), top + PANEL_INSET, 0x808080, false);

        renderBranchNode(guiGraphics, layout, originX, contentTop);

        return bottom;
    }

    private BranchNode buildBranchNode(RecipeTreeNode node, int depth) {
        List<BranchNode> children = new ArrayList<>();
        int maxDepth = Config.RECIPE_BRANCH_MAX_DEPTH.getAsInt();
        int maxChildren = Config.RECIPE_BRANCH_MAX_CHILDREN.getAsInt();
        if (depth < maxDepth - 1) {
            int limit = Math.min(node.children().size(), maxChildren);
            for (int i = 0; i < limit; i++) {
                children.add(buildBranchNode(node.children().get(i), depth + 1));
            }
            int hidden = node.children().size() - limit;
            if (hidden > 0) {
                children.add(new BranchNode(ItemStack.EMPTY, Component.literal("+" + hidden + " more"), HighlightRole.BASE, Component.literal("Additional ingredients hidden to keep the branch readable"), List.of()));
            }
        } else if (!node.children().isEmpty()) {
            children.add(new BranchNode(ItemStack.EMPTY, Component.literal("+" + node.children().size() + " more"), HighlightRole.BASE, Component.literal("Additional ingredients hidden to keep the branch readable"), List.of()));
        }

        return new BranchNode(node.stack(), node.name(), node.role(), node.reason(), List.copyOf(children));
    }

    private BranchLayout measureBranchLayout(BranchNode node) {
        List<BranchLayout> children = new ArrayList<>();
        int childrenWidth = 0;
        int childrenHeight = 0;
        for (BranchNode child : node.children()) {
            BranchLayout childLayout = measureBranchLayout(child);
            children.add(childLayout);
            childrenWidth += childLayout.width();
            childrenHeight = Math.max(childrenHeight, childLayout.height());
        }
        if (!children.isEmpty()) {
            childrenWidth += RECIPE_BRANCH_CHILD_GAP * (children.size() - 1);
        }

        int nodeWidth = Math.min(RECIPE_BRANCH_MAX_WIDTH, Math.max(72, this.font.width(node.label().getString()) + 18));
        int width = Math.max(nodeWidth, childrenWidth);
        int height = this.font.lineHeight + 10;
        if (!children.isEmpty()) {
            height += RECIPE_BRANCH_ROW_GAP + childrenHeight;
        }

        BranchLayout layout = new BranchLayout(node, width, height, children);
        int nodeX = (width - nodeWidth) / 2;
        layout.setNode(nodeX, 0, nodeWidth, this.font.lineHeight + 10);

        if (!children.isEmpty()) {
            int childX = (width - childrenWidth) / 2;
            int childY = this.font.lineHeight + 10 + RECIPE_BRANCH_ROW_GAP;
            for (BranchLayout childLayout : children) {
                childLayout.place(childX, childY);
                childX += childLayout.width() + RECIPE_BRANCH_CHILD_GAP;
            }
        }

        return layout;
    }

    private void renderBranchNode(GuiGraphics guiGraphics, BranchLayout layout, int originX, int originY) {
        int x = originX + layout.nodeX();
        int y = originY + layout.nodeY();
        int width = layout.nodeWidth();
        int height = layout.nodeHeight();
        int borderColor = roleBorderColor(layout.node().role());
        int fillColor = roleFillColor(layout.node().role());
        branchHitBoxes.add(new BranchHitBox(x, y, width, height, layout.node().reason(), layout.node().stack()));

        guiGraphics.fill(x, y, x + width, y + height, fillColor);
        guiGraphics.fill(x, y, x + width, y + 1, borderColor);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        guiGraphics.fill(x, y, x + 1, y + height, borderColor);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor);

        String text = layout.node().label().getString();
        String clipped = text;
        int available = width - 10;
        if (this.font.width(text) > available) {
            clipped = this.font.plainSubstrByWidth(text, available);
            if (!clipped.equals(text) && clipped.length() < text.length()) {
                clipped = clipped.trim();
                if (!clipped.endsWith("...")) {
                    clipped = clipped + "...";
                }
            }
        }
        int textX = x + Math.max(4, (width - this.font.width(clipped)) / 2);
        int textY = y + Math.max(3, (height - this.font.lineHeight) / 2);
        guiGraphics.drawString(this.font, clipped, textX, textY, roleTextColor(layout.node().role()), false);

        if (layout.children().isEmpty()) {
            return;
        }

        int parentCenterX = x + width / 2;
        int branchY = y + height + RECIPE_BRANCH_ROW_GAP / 2;
        int leftMost = Integer.MAX_VALUE;
        int rightMost = Integer.MIN_VALUE;
        for (BranchLayout child : layout.children()) {
            int childCenterX = originX + child.nodeX() + child.nodeWidth() / 2;
            leftMost = Math.min(leftMost, childCenterX);
            rightMost = Math.max(rightMost, childCenterX);
        }

        guiGraphics.fill(parentCenterX, y + height, parentCenterX + 1, branchY, borderColor);
        if (layout.children().size() > 1) {
            guiGraphics.fill(leftMost, branchY, rightMost + 1, branchY + 1, borderColor);
        }
        for (BranchLayout child : layout.children()) {
            int childCenterX = originX + child.nodeX() + child.nodeWidth() / 2;
            guiGraphics.fill(childCenterX, branchY, childCenterX + 1, originY + child.nodeY(), borderColor);
            renderBranchNode(guiGraphics, child, originX, originY);
        }
    }

    private void renderBranchTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (BranchHitBox hitBox : branchHitBoxes) {
            if (mouseX < hitBox.x() || mouseX >= hitBox.x() + hitBox.width() || mouseY < hitBox.y() || mouseY >= hitBox.y() + hitBox.height()) {
                continue;
            }

            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.literal("MCAI branch").withStyle(ChatFormatting.YELLOW),
                    hitBox.reason().copy().withStyle(ChatFormatting.GRAY),
                    hitBox.stack().isEmpty()
                            ? Component.literal("Click to inspect").withStyle(ChatFormatting.DARK_GRAY)
                            : Component.literal("Click to open in JEI").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
    }

    private int roleTextColor(HighlightRole role) {
        return switch (role) {
            case TARGET -> 0xFF8EFFFF;
            case INTERMEDIATE -> 0xFFFFF27A;
            case BASE -> 0xFFA8FFB3;
        };
    }

    private int roleBorderColor(HighlightRole role) {
        return switch (role) {
            case TARGET -> 0xFF8EFFFF;
            case INTERMEDIATE -> 0xFFFFF27A;
            case BASE -> 0xFFA8FFB3;
        };
    }

    private int roleFillColor(HighlightRole role) {
        return switch (role) {
            case TARGET -> 0xD0203B4F;
            case INTERMEDIATE -> 0xD03D3320;
            case BASE -> 0xD0203D28;
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
                ? panelTop + recipeBranchPanelHeight()
                : panelTop;
        int transcriptTop = treeBottom + (treeBottom > panelTop ? 6 : 0);
        int visibleLines = visibleLineCount(transcriptTop + PANEL_INSET, panelBottom - PANEL_INSET);
        return Math.max(0, wrappedTranscript.size() - visibleLines);
    }

    private int recipeBranchPanelHeight() {
        RecipeTracker tracker = RecipeTracker.get();
        if (tracker.recipeTree().isEmpty()) {
            return 0;
        }

        BranchNode root = buildBranchNode(tracker.recipeTree().get(), 0);
        BranchLayout layout = measureBranchLayout(root);
        return PANEL_INSET + this.font.lineHeight + 4 + layout.height() + PANEL_INSET;
    }

    private void markTranscriptDirty() {
        transcriptDirty = true;
        scrollOffsetLines = 0;
    }

    private Component statusText() {
        RecipeTracker tracker = RecipeTracker.get();
        String status = "Model: " + Config.OLLAMA_MODEL.get()
                + " | Profile: " + Config.CONTEXT_PROFILE.get()
                + " | Tone: " + Config.RESPONSE_TONE.get()
                + " | Mode: " + Config.CHAT_MODE.get()
                + " | Share: " + AiChatManager.get().describeShareWhitelistCompact()
                + " | Context: "
                + contextFlag("Inv", Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Player", Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Mods", Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean())
                + ", " + contextFlag("Quests", Config.INCLUDE_QUEST_CONTEXT.getAsBoolean())
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
        if (retryButton != null) {
            retryButton.active = !waitingForReply && AiChatManager.get().canRetryLastResponse();
        }
        if (exportButton != null) {
            exportButton.active = !waitingForReply && !AiChatManager.get().getHistory().isEmpty();
        }
    }

    private record BranchNode(ItemStack stack, Component label, HighlightRole role, Component reason, List<BranchNode> children) {
    }

    private static final class BranchLayout {
        private final BranchNode node;
        private final int width;
        private final int height;
        private final List<BranchLayout> children;
        private int nodeX;
        private int nodeY;
        private int nodeWidth;
        private int nodeHeight;

        private BranchLayout(BranchNode node, int width, int height, List<BranchLayout> children) {
            this.node = node;
            this.width = width;
            this.height = height;
            this.children = children;
        }

        private void setNode(int x, int y, int width, int height) {
            this.nodeX = x;
            this.nodeY = y;
            this.nodeWidth = width;
            this.nodeHeight = height;
        }

        private void place(int x, int y) {
        setNode(x + (width - nodeWidth) / 2, y, nodeWidth, nodeHeight);
            if (children.isEmpty()) {
                return;
            }

            int childWidth = 0;
            for (BranchLayout child : children) {
                childWidth += child.width();
            }
            childWidth += RECIPE_BRANCH_CHILD_GAP * (children.size() - 1);

            int childX = x + (width - childWidth) / 2;
            int childY = y + nodeHeight + RECIPE_BRANCH_ROW_GAP;
            for (BranchLayout child : children) {
                child.place(childX, childY);
                childX += child.width() + RECIPE_BRANCH_CHILD_GAP;
            }
        }

        private BranchNode node() {
            return node;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private List<BranchLayout> children() {
            return children;
        }

        private int nodeX() {
            return nodeX;
        }

        private int nodeY() {
            return nodeY;
        }

        private int nodeWidth() {
            return nodeWidth;
        }

        private int nodeHeight() {
            return nodeHeight;
        }
    }

    private record BranchHitBox(int x, int y, int width, int height, Component reason, ItemStack stack) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
