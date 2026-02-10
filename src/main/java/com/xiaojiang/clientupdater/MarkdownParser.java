package com.xiaojiang.clientupdater;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单的Markdown解析器，将Markdown文本转换为Minecraft Component
 */
public class MarkdownParser {
    
    // 正则表达式模式
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.+?)\\]\\((.+?)\\)");
    private static final Pattern HEADER_1_PATTERN = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern HEADER_2_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Pattern HEADER_3_PATTERN = Pattern.compile("^###\\s+(.+)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^[-*]\\s+(.+)$");
    
    /**
     * 解析Markdown文本并返回Component列表（每行一个）
     */
    public static List<Component> parse(String markdown) {
        List<Component> components = new ArrayList<>();
        
        if (markdown == null || markdown.isEmpty()) {
            return components;
        }
        
        String[] lines = markdown.split("\\r?\\n");
        
        for (String line : lines) {
            Component component = parseLine(line);
            if (component != null) {
                components.add(component);
            }
        }
        
        return components;
    }
    
    /**
     * 解析单行Markdown
     */
    private static Component parseLine(String line) {
        if (line.trim().isEmpty()) {
            return Component.literal("");
        }
        
        // 检查标题
        Matcher h1Matcher = HEADER_1_PATTERN.matcher(line);
        if (h1Matcher.matches()) {
            return Component.literal(h1Matcher.group(1))
                    .setStyle(Style.EMPTY.withBold(true).withColor(0x00AA00).withUnderlined(true));
        }
        
        Matcher h2Matcher = HEADER_2_PATTERN.matcher(line);
        if (h2Matcher.matches()) {
            return Component.literal(h2Matcher.group(1))
                    .setStyle(Style.EMPTY.withBold(true).withColor(0x55FF55));
        }
        
        Matcher h3Matcher = HEADER_3_PATTERN.matcher(line);
        if (h3Matcher.matches()) {
            return Component.literal(h3Matcher.group(1))
                    .setStyle(Style.EMPTY.withBold(true).withColor(0xAAFFAA));
        }
        
        // 检查列表项
        Matcher listMatcher = LIST_ITEM_PATTERN.matcher(line);
        if (listMatcher.matches()) {
            String content = listMatcher.group(1);
            MutableComponent bullet = Component.literal("• ").setStyle(Style.EMPTY.withColor(0xFFAA00));
            MutableComponent text = parseInline(content);
            return bullet.append(text);
        }
        
        // 普通行，解析内联样式
        return parseInline(line);
    }
    
    /**
     * 解析内联Markdown样式（粗体、斜体、代码、链接）
     */
    private static MutableComponent parseInline(String text) {
        MutableComponent result = Component.literal("");
        int lastEnd = 0;
        
        // 创建一个列表来存储所有匹配项及其位置
        List<MatchInfo> matches = new ArrayList<>();
        
        // 查找所有粗体
        Matcher boldMatcher = BOLD_PATTERN.matcher(text);
        while (boldMatcher.find()) {
            matches.add(new MatchInfo(boldMatcher.start(), boldMatcher.end(), 
                boldMatcher.group(1), MatchType.BOLD));
        }
        
        // 查找所有斜体（不包括已被粗体匹配的）
        Matcher italicMatcher = ITALIC_PATTERN.matcher(text);
        while (italicMatcher.find()) {
            // 检查是否已经在粗体中
            boolean inBold = false;
            for (MatchInfo match : matches) {
                if (match.type == MatchType.BOLD && 
                    italicMatcher.start() >= match.start && 
                    italicMatcher.end() <= match.end) {
                    inBold = true;
                    break;
                }
            }
            if (!inBold) {
                matches.add(new MatchInfo(italicMatcher.start(), italicMatcher.end(),
                    italicMatcher.group(1), MatchType.ITALIC));
            }
        }
        
        // 查找所有代码
        Matcher codeMatcher = CODE_PATTERN.matcher(text);
        while (codeMatcher.find()) {
            matches.add(new MatchInfo(codeMatcher.start(), codeMatcher.end(),
                codeMatcher.group(1), MatchType.CODE));
        }
        
        // 查找所有链接
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            matches.add(new MatchInfo(linkMatcher.start(), linkMatcher.end(),
                linkMatcher.group(1), MatchType.LINK, linkMatcher.group(2)));
        }
        
        // 按位置排序
        matches.sort((a, b) -> Integer.compare(a.start, b.start));
        
        // 合并重叠的匹配项
        List<MatchInfo> filteredMatches = new ArrayList<>();
        int lastMatchEnd = -1;
        for (MatchInfo match : matches) {
            if (match.start >= lastMatchEnd) {
                filteredMatches.add(match);
                lastMatchEnd = match.end;
            }
        }
        
        // 构建结果
        lastEnd = 0;
        for (MatchInfo match : filteredMatches) {
            // 添加匹配前的普通文本
            if (match.start > lastEnd) {
                result.append(Component.literal(text.substring(lastEnd, match.start)));
            }
            
            // 添加格式化的文本
            MutableComponent styledText = Component.literal(match.content);
            switch (match.type) {
                case BOLD:
                    styledText.setStyle(Style.EMPTY.withBold(true));
                    break;
                case ITALIC:
                    styledText.setStyle(Style.EMPTY.withItalic(true));
                    break;
                case CODE:
                    styledText.setStyle(Style.EMPTY.withColor(0xFF5555));
                    break;
                case LINK:
                    styledText.setStyle(Style.EMPTY.withColor(0x5555FF).withUnderlined(true));
                    // 可以添加点击事件来打开链接
                    styledText.setStyle(styledText.getStyle().withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, match.url)));
                    break;
            }
            result.append(styledText);
            
            lastEnd = match.end;
        }
        
        // 添加剩余文本
        if (lastEnd < text.length()) {
            result.append(Component.literal(text.substring(lastEnd)));
        }
        
        return result;
    }
    
    /**
     * 匹配信息内部类
     */
    private static class MatchInfo {
        final int start;
        final int end;
        final String content;
        final MatchType type;
        final String url;
        
        MatchInfo(int start, int end, String content, MatchType type) {
            this(start, end, content, type, null);
        }
        
        MatchInfo(int start, int end, String content, MatchType type, String url) {
            this.start = start;
            this.end = end;
            this.content = content;
            this.type = type;
            this.url = url;
        }
    }
    
    private enum MatchType {
        BOLD, ITALIC, CODE, LINK
    }
}
