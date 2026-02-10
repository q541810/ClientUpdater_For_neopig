package com.xiaojiang.clientupdater.screens;

import com.xiaojiang.clientupdater.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ModWhitelistScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.clientupdater.whitelist.title");
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final List<String> localExtraMods;
    private final Set<String> workingWhitelist;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private int page = 0;
    private Component saveMessage = Component.empty();
    private int saveMessageColor = 0xAAAAAA;

    public ModWhitelistScreen(Screen parent, List<String> localExtraMods) {
        super(TITLE);
        this.parent = parent;
        this.localExtraMods = sanitizeAndSort(localExtraMods);
        this.workingWhitelist = Config.getWhitelistedModFilenames();
    }

    @Override
    protected void init() {
        this.layout.addToHeader(new StringWidget(this.getTitle(), this.font));

        GridLayout listLayout = this.layout.addToContents(new GridLayout()).spacing(6);
        listLayout.defaultCellSetting().alignHorizontallyLeft();
        GridLayout.RowHelper row = listLayout.createRowHelper(2);

        if (this.localExtraMods.isEmpty()) {
            row.addChild(new StringWidget(Component.translatable("gui.clientupdater.whitelist.empty"), this.font));
            row.addChild(Button.builder(Component.translatable("gui.clientupdater.whitelist.off"), b -> {
            }).bounds(0, 0, 56, 20).build()).active = false;
        } else {
            int totalPages = getTotalPages();
            this.page = Math.max(0, Math.min(this.page, totalPages - 1));
            int start = this.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, this.localExtraMods.size());

            for (int i = start; i < end; i++) {
                String modName = this.localExtraMods.get(i);
                row.addChild(new StringWidget(Component.literal(modName), this.font));
                row.addChild(createToggleButton(modName));
            }

            if (totalPages > 1) {
                row.addChild(Button.builder(Component.translatable("gui.clientupdater.whitelist.prev"), btn -> {
                    if (this.page > 0) {
                        this.page--;
                        this.rebuildWidgets();
                    }
                }).bounds(0, 0, 80, 20).build()).active = this.page > 0;

                row.addChild(Button.builder(Component.translatable("gui.clientupdater.whitelist.next"), btn -> {
                    if (this.page < totalPages - 1) {
                        this.page++;
                        this.rebuildWidgets();
                    }
                }).bounds(0, 0, 80, 20).build()).active = this.page < totalPages - 1;
            }
        }

        GridLayout footer = this.layout.addToFooter(new GridLayout()).spacing(8);
        GridLayout.RowHelper footerRow = footer.createRowHelper(2);

        footerRow.addChild(Button.builder(Component.translatable("gui.clientupdater.whitelist.save"), btn -> {
            boolean ok = Config.setModWhitelist(this.workingWhitelist);
            if (ok) {
                this.saveMessage = Component.translatable("gui.clientupdater.whitelist.save.success");
                this.saveMessageColor = 0x55FF55;
            } else {
                this.saveMessage = Component.translatable("gui.clientupdater.whitelist.save.failed");
                this.saveMessageColor = 0xFF5555;
            }
        }).bounds(0, 0, 100, 20).build());

        footerRow.addChild(Button.builder(Component.translatable("gui.clientupdater.whitelist.back"), btn ->
                this.minecraft.setScreen(this.parent)).bounds(0, 0, 100, 20).build());

        this.layout.arrangeElements();
        this.layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        if (!this.saveMessage.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.saveMessage, this.width / 2, this.height - 56, this.saveMessageColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private Button createToggleButton(String modName) {
        boolean enabled = this.workingWhitelist.contains(Config.normalizeFilename(modName));
        Button button = Button.builder(toggleLabel(enabled), btn -> {
            String normalized = Config.normalizeFilename(modName);
            if (normalized.isEmpty()) {
                this.saveMessage = Component.translatable("gui.clientupdater.whitelist.invalid_name");
                this.saveMessageColor = 0xFF5555;
                return;
            }
            if (this.workingWhitelist.contains(normalized)) {
                this.workingWhitelist.remove(normalized);
            } else {
                this.workingWhitelist.add(normalized);
            }
            boolean on = this.workingWhitelist.contains(normalized);
            btn.setMessage(toggleLabel(on));
        }).bounds(0, 0, 56, 20).build();
        return button;
    }

    private Component toggleLabel(boolean enabled) {
        if (enabled) {
            return Component.translatable("gui.clientupdater.whitelist.on")
                    .setStyle(Style.EMPTY.withColor(0x55FF55));
        }
        return Component.translatable("gui.clientupdater.whitelist.off")
                .setStyle(Style.EMPTY.withColor(0xFF5555));
    }

    private int getTotalPages() {
        return Math.max(1, (this.localExtraMods.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static List<String> sanitizeAndSort(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        List<String> result = new ArrayList<>(unique);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
