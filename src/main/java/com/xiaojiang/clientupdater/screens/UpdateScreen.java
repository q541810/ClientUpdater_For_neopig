package com.xiaojiang.clientupdater.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.xiaojiang.clientupdater.Updater;

//import org.slf4j.Logger;

//import com.mojang.logging.LogUtils;
import com.xiaojiang.clientupdater.Update;

@OnlyIn(Dist.CLIENT)
public class UpdateScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.clientupdater.updating");
    private Update update = new Update();
    private String serverURL;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private static Button bt;
    private Updater updater = new Updater();
    // private static final Logger LOGGER = LogUtils.getLogger();

    public UpdateScreen(Update up, String url) {
        super(TITLE);
        this.update = up;
        this.serverURL = url;
        this.updater.update = this.update;
        this.updater.server_url = this.serverURL;
        this.updater.start();
    }

    protected void init() {
        this.layout.addToHeader(new StringWidget(this.getTitle(), this.font));
        // 添加按钮
        bt = this.layout.addToFooter(Button.builder(Component.translatable("gui.clientupdater.restart"), (v) -> {
            this.minecraft.stop();
        }).bounds(this.width / 2 - 100, 140, 200, 20).build());
        this.layout.arrangeElements();
        this.layout.visitWidgets(this::addRenderableWidget);
        bt.visible = updater.isComplete;
    }

    public void render(GuiGraphics p_281469_, int p_96053_, int p_96054_, float p_96055_) {
        this.renderBackground(p_281469_);
        
        // 绘制标题 - 已在 init 中通过 layout 添加，此处移除避免重复绘制
        // p_281469_.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);

        if (!updater.isComplete) {
            // 绘制警告信息 (红色)
            p_281469_.drawCenteredString(this.font, "警告：不要在更新时关闭游戏或断网！会导致下载的模组损坏！", this.width / 2, this.height / 2 - 40, 0xFF5555);

            // 绘制进度信息 (使用 currentStep 显示更详细的状态)
            String statusText = updater.currentStep.isEmpty() ? ("正在下载: " + updater.currentFileName) : updater.currentStep;
            p_281469_.drawCenteredString(this.font, statusText, this.width / 2, this.height / 2 - 20, 0xFFFFFF);

            // 绘制进度条背景 (深灰色)
            int barWidth = 200;
            int barHeight = 20;
            int barX = (this.width - barWidth) / 2;
            int barY = this.height / 2;
            p_281469_.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

            // 绘制进度条前景 (绿色)
            int progressWidth = (int) (barWidth * (updater.currentProgress / 100.0f));
            p_281469_.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF55FF55);

            // 绘制百分比文字
            String percentText = updater.currentProgress + "%";
            p_281469_.drawCenteredString(this.font, percentText, this.width / 2, barY + 6, 0xFFFFFF);
        } else {
            // 更新完成提示
            p_281469_.drawCenteredString(this.font, "更新完成！请重启游戏", this.width / 2, this.height / 2 - 10, 0x55FF55);
        }

        bt.visible = updater.isComplete;
        super.render(p_281469_, p_96053_, p_96054_, p_96055_);
    }

    public void tick() {
        bt.visible = updater.isComplete;
        super.tick();
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }
}
