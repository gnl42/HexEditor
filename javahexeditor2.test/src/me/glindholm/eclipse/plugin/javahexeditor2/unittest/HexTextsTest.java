package me.glindholm.eclipse.plugin.javahexeditor2.unittest;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent;
import me.glindholm.eclipse.plugin.javahexeditor2.HexTexts;

public final class HexTextsTest {

    private BinaryContent content;
    private Display display;
    private Shell shell;
    private HexTexts hexTexts;

    @BeforeEach
    public void setUp() throws Exception {
        display = Display.getDefault();
        shell = new Shell(Display.getDefault(), SWT.MODELESS | SWT.SHELL_TRIM);
        hexTexts = new HexTexts(shell, SWT.DEFAULT);
        content = new BinaryContent();
        content.insert(ByteBuffer.allocate(200), 0L);
        hexTexts.setContentProvider(content);
    }

    @AfterEach
    public void tearDown() throws Exception {
        content.dispose();
        hexTexts.dispose();
        shell.dispose();
        display.close();
    }

    /*
     * Test method for 'me.glindholm.eclipse.plugin.javahexeditor2.HexTexts.mergeRanges(ArrayList, int)'
     */
    @Test
    public void testMergeRanges() {
        final List<Long> changes = new ArrayList<>();
        final List<Integer> highlights = new ArrayList<>();
        List<StyleRange> merged = hexTexts.mergeRanges(changes, highlights); // _
        // _
        // _
        // _
        assertTrue(merged == null || merged.isEmpty());

        changes.add((long) 0);
        changes.add((long) 1);
        merged = hexTexts.mergeRanges(changes, highlights); // C _ _ _
        assertEquals(1, merged.size());
        assertEquals(0, merged.get(0).start);
        assertEquals(1, merged.get(0).length);

        changes.set(0, (long) 1);
        changes.set(1, (long) 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ C C _
        assertEquals(1, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(2, merged.get(0).length);

        changes.clear();
        highlights.add(0);
        highlights.add(1);
        merged = hexTexts.mergeRanges(changes, highlights); // H _ _ _
        assertEquals(1, merged.size());
        assertEquals(0, merged.get(0).start);
        assertEquals(1, merged.get(0).length);

        highlights.set(0, 1);
        highlights.set(1, 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H H _
        assertEquals(1, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(2, merged.get(0).length);

        changes.add((long) 0);
        changes.add((long) 1);
        merged = hexTexts.mergeRanges(changes, highlights); // C H H _
        assertEquals(2, merged.size());
        assertEquals(0, merged.get(0).start);
        assertEquals(1, merged.get(0).length);
        assertEquals(1, merged.get(1).start);
        assertEquals(2, merged.get(1).length);

        changes.set(0, (long) 3);
        changes.set(1, (long) 1);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H H C _
        assertEquals(2, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(2, merged.get(0).length);
        assertEquals(3, merged.get(1).start);
        assertEquals(1, merged.get(1).length);

        changes.set(0, (long) 4);
        changes.set(1, (long) 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H H _ C C _
        assertEquals(2, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(2, merged.get(0).length);
        assertEquals(4, merged.get(1).start);
        assertEquals(2, merged.get(1).length);

        changes.set(0, (long) 1);
        changes.set(1, (long) 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ CH CH _
        assertEquals(1, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(2, merged.get(0).length);

        changes.set(0, (long) 1);
        changes.set(1, (long) 1);
        merged = hexTexts.mergeRanges(changes, highlights); // _ CH H _
        assertEquals(2, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(1, merged.get(0).length);
        assertEquals(2, merged.get(1).start);
        assertEquals(1, merged.get(1).length);

        changes.set(0, (long) 2);
        changes.set(1, (long) 1);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H CH _
        assertEquals(2, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(1, merged.get(0).length);
        assertEquals(2, merged.get(1).start);
        assertEquals(1, merged.get(1).length);

        changes.set(0, (long) 2);
        changes.set(1, (long) 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H CH C _
        assertEquals(3, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(1, merged.get(0).length);
        assertEquals(2, merged.get(1).start);
        assertEquals(1, merged.get(1).length);
        assertEquals(3, merged.get(2).start);
        assertEquals(1, merged.get(2).length);

        highlights.set(1, 4);
        merged = hexTexts.mergeRanges(changes, highlights); // _ H CH CH H _
        assertEquals(3, merged.size());
        assertEquals(1, merged.get(0).start);
        assertEquals(1, merged.get(0).length);
        assertEquals(2, merged.get(1).start);
        assertEquals(2, merged.get(1).length);
        assertEquals(4, merged.get(2).start);
        assertEquals(1, merged.get(2).length);

        highlights.set(0, 2);
        merged = hexTexts.mergeRanges(changes, highlights); // _ _ CH CH H H _
        assertEquals(2, merged.size());
        assertEquals(2, merged.get(0).start);
        assertEquals(2, merged.get(0).length);
        assertEquals(4, merged.get(1).start);
        assertEquals(2, merged.get(1).length);
    }
}
