package com.akashic.kunftig.common.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable text component tree: literal text, a style and children that inherit the style.
 * This is the subset of Minecraft's JSON text format that matters for names; anything else
 * (hover, click, insertion, font) is accepted by the parser and ignored.
 */
public final class Component {

    public static final Component EMPTY = new Component("", Style.EMPTY, Collections.<Component>emptyList());

    private final String text;
    private final Style style;
    private final List<Component> children;

    private Component(String text, Style style, List<Component> children) {
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
        this.children = children;
    }

    public static Component text(String text) {
        return text(text, Style.EMPTY);
    }

    public static Component text(String text, Style style) {
        return new Component(text, style, Collections.<Component>emptyList());
    }

    public static Component of(String text, Style style, List<Component> children) {
        if (children.isEmpty()) {
            return text(text, style);
        }
        return new Component(text, style, Collections.unmodifiableList(new ArrayList<Component>(children)));
    }

    public String text() {
        return text;
    }

    public Style style() {
        return style;
    }

    public List<Component> children() {
        return children;
    }

    /** Concatenated literal text of the whole tree, without any formatting. */
    public String plainText() {
        StringBuilder out = new StringBuilder();
        appendPlain(out);
        return out.toString();
    }

    private void appendPlain(StringBuilder out) {
        out.append(text);
        for (Component child : children) {
            child.appendPlain(out);
        }
    }

    /** Visits every literal run with its fully resolved style, in reading order. */
    public void forEachRun(RunVisitor visitor) {
        visit(Style.EMPTY, visitor);
    }

    private void visit(Style parent, RunVisitor visitor) {
        Style resolved = style.inheritFrom(parent);
        if (!text.isEmpty()) {
            visitor.accept(text, resolved);
        }
        for (Component child : children) {
            child.visit(resolved, visitor);
        }
    }

    /** Receives literal runs from {@link #forEachRun(RunVisitor)}. */
    public interface RunVisitor {

        void accept(String text, Style resolvedStyle);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Component)) {
            return false;
        }
        Component that = (Component) other;
        return text.equals(that.text) && style.equals(that.style) && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, style, children);
    }

    @Override
    public String toString() {
        return "Component{text='" + text + "', style=" + style + ", children=" + children + '}';
    }
}
