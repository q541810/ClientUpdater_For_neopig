package com.xiaojiang.clientupdater.screens;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.xiaojiang.clientupdater.MarkdownParser;
import com.xiaojiang.clientupdater.Update;

import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class UpdateLogScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.clientupdater.updatelog");
    private final String serverURL;
    private final Update update;
    private final boolean need_update;
    private final List<String> localExtraMods;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private static final Logger LOGGER = LogUtils.getLogger();

    private Button whitelistButton;

    private int whitelistButtonX() {
        return this.width - 126;
    }

    private int whitelistButtonY() {
        // Keep it at lower-right, but above bottom-center buttons to avoid overlap.
        return this.height - 78;
    }

    public UpdateLogScreen(String serverurl, Update up, boolean needupdate) {
        this(serverurl, up, needupdate, Collections.emptyList());
    }

    public UpdateLogScreen(String serverurl, Update up, boolean needupdate, List<String> localExtraMods) {
        super(TITLE);
        this.serverURL = serverurl;
        this.update = up == null ? new Update() : up;
        this.need_update = needupdate;
        this.localExtraMods = localExtraMods == null ? Collections.emptyList() : localExtraMods;
        if (this.update.mods_list == null) {
            LOGGER.debug("Update data has null mods list");
        }
    }

    @Override
    protected void init() {
        this.layout.addToHeader(new StringWidget(this.getTitle(), this.font));

        String markdownContent = "## " + (this.update.update_time != null ? this.update.update_time : "") + "\n\n"
                + (this.update.update_logs != null ? this.update.update_logs : "");
        List<Component> parsedLines = MarkdownParser.parse(markdownContent);

        GridLayout logs = this.layout.addToContents(new GridLayout()).spacing(8);
        logs.defaultCellSetting().alignHorizontallyCenter();
        GridLayout.RowHelper logs$rowhelper = logs.createRowHelper(1);

        int logWidth = Math.max(220, this.width - (this.localExtraMods.isEmpty() ? 40 : 170));
        MarkdownTextWidget markdownWidget = new MarkdownTextWidget(20, 50, logWidth, this.height - 120, parsedLines);
        logs$rowhelper.addChild(markdownWidget);

        logs$rowhelper.addChild(Button.builder(Component.translatable("gui.clientupdater.gotoweb"), (p_280784_) -> {
            this.minecraft.setScreen(new ConfirmLinkScreen((p_280783_) -> {
                if (p_280783_) {
                    Util.getPlatform().openUri(serverURL);
                }
                this.minecraft.setScreen(this);
            }, serverURL, true));
        }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());

        if (!this.localExtraMods.isEmpty()) {
            this.whitelistButton = Button
                    .builder(Component.translatable("gui.clientupdater.openwhitelist"),
                            (btn) -> this.minecraft.setScreen(new ModWhitelistScreen(this, this.localExtraMods)))
                    .bounds(whitelistButtonX(), whitelistButtonY(), 116, 20)
                    .build();
            this.addRenderableWidget(this.whitelistButton);
        }

        if (need_update) {
            this.layout.addToFooter(Button.builder(Component.translatable("gui.clientupdater.update"), (p_280801_) -> {
                this.minecraft.setScreen(new UpdateScreen(this.update, this.serverURL));
            }).bounds(this.width / 2 - 100, 140, 200, 20).build());
        } else {
            this.layout.addToFooter(Button.builder(Component.translatable("gui.clientupdater.complete"), (p_280801_) -> {
                this.minecraft.setScreen((Screen) null);
            }).bounds(this.width / 2 - 100, 140, 200, 20).build());
        }

        this.layout.arrangeElements();
        this.layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        
        // 更新白名单按钮位置（它不在 layout 中）
        if (this.whitelistButton != null) {
            this.whitelistButton.setX(whitelistButtonX());
            this.whitelistButton.setY(whitelistButtonY());
        }
    }

    @Override
    public void render(GuiGraphics p_281469_, int p_96053_, int p_96054_, float p_96055_) {
        this.renderBackground(p_281469_);
        if (!this.localExtraMods.isEmpty()) {
            int noteX = whitelistButtonX();
            int noteY = whitelistButtonY() - 42;
            List<net.minecraft.util.FormattedCharSequence> wrapped = this.font.split(
                    Component.translatable("gui.clientupdater.whitelist.note"), 116);
            for (int i = 0; i < wrapped.size(); i++) {
                p_281469_.drawString(this.font, wrapped.get(i), noteX, noteY + i * 10, 0xBDBDBD);
            }
        }
        super.render(p_281469_, p_96053_, p_96054_, p_96055_);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
