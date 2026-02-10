package com.xiaojiang.clientupdater.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown文本显示组件，支持滚动
 */
@OnlyIn(Dist.CLIENT)
public class MarkdownTextWidget extends AbstractWidget {
    
    private final List<Component> lines;
    private final List<FormattedCharSequence> wrappedLines;
    private double scrollAmount;
    private boolean scrolling;
    private int contentHeight;
    private final int lineHeight;
    private int lastWrapWidth;
    
    public MarkdownTextWidget(int x, int y, int width, int height, List<Component> lines) {
        super(x, y, width, height, Component.empty());
        this.lines = lines != null ? lines : new ArrayList<>();
        this.wrappedLines = new ArrayList<>();
        this.lineHeight = 12; // 每行高度
        this.scrollAmount = 0;
        this.lastWrapWidth = -1;
        rebuildWrappedLines();
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        rebuildWrappedLines();
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        clampScroll();
    }

    private void rebuildWrappedLines() {
        int wrapWidth = Math.max(24, this.width - 8);
        if (wrapWidth == this.lastWrapWidth && !this.wrappedLines.isEmpty()) {
            return;
        }
        this.lastWrapWidth = wrapWidth;
        this.wrappedLines.clear();

        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (Component line : this.lines) {
            if (line == null || line.getString().isEmpty()) {
                this.wrappedLines.add(FormattedCharSequence.EMPTY);
                continue;
            }
            this.wrappedLines.addAll(font.split(line, wrapWidth));
        }

        this.contentHeight = this.wrappedLines.size() * this.lineHeight + 8;
        clampScroll();
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.height);
    }

    private void clampScroll() {
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0, maxScroll());
    }
    
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        rebuildWrappedLines();

        // 绘制背景（可选）
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x80000000);
        
        // 启用裁剪
        graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        
        // 计算起始行
        int startLine = (int) (this.scrollAmount / this.lineHeight);
        int endLine = Math.min(startLine + (this.height / this.lineHeight) + 1, this.wrappedLines.size());
        
        // 绘制可见行
        int yOffset = this.getY() + 4 - (int) (this.scrollAmount % this.lineHeight);
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = startLine; i < endLine; i++) {
            if (i >= 0 && i < this.wrappedLines.size()) {
                FormattedCharSequence line = this.wrappedLines.get(i);
                graphics.drawString(font, line, this.getX() + 4, yOffset, 0xFFFFFF);
                yOffset += this.lineHeight;
            }
        }
        
        // 禁用裁剪
        graphics.disableScissor();
        
        // 绘制滚动条
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int scrollBarHeight = Math.max(16, (int) (this.height * (this.height / (double) this.contentHeight)));
            int scrollBarY = this.getY() + (int) ((this.height - scrollBarHeight) * (this.scrollAmount / maxScroll));
            
            // 滚动条背景
            graphics.fill(this.getX() + this.width - 4, this.getY(), 
                this.getX() + this.width, this.getY() + this.height, 0x40FFFFFF);
            // 滚动条滑块
            graphics.fill(this.getX() + this.width - 4, scrollBarY,
                this.getX() + this.width, scrollBarY + scrollBarHeight, 0xFFFFFFFF);
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - delta * this.lineHeight * 2, 0, maxScroll);
            return true;
        }
        return false;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            // 只有当点击在滚动条区域时才启动拖动
            if (maxScroll() > 0 && mouseX >= this.getX() + this.width - 4) {
                this.scrolling = true;
                return true;
            }
            // 否则让点击事件传递（支持链接点击）
            return false;
        }
        return false;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean wasScrolling = this.scrolling;
            this.scrolling = false;
            return wasScrolling;
        }
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int maxScroll = maxScroll();
        if (this.scrolling && maxScroll > 0) {
            double scrollRatio = dragY / this.height;
            this.scrollAmount = Mth.clamp(this.scrollAmount + scrollRatio * maxScroll, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // 无障碍功能 narration
    }
    
    public void setScrollAmount(double amount) {
        this.scrollAmount = Mth.clamp(amount, 0, maxScroll());
    }
    
    public double getScrollAmount() {
        return scrollAmount;
    }
}
