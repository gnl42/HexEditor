/*
 * javahexeditor, a java hex editor
 * Copyright (C) 2006, 2009 Jordi Bergenthal, pestatije(-at_)users.sourceforge.net
 * Copyright (C) 2018 - 2021 Peter Dell, peterdell(-at_)users.sourceforge.net
 * The official javahexeditor site is https://sourceforge.net/projects/javahexeditor
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package me.glindholm.eclipse.plugin.javahexeditor2.unittest;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent;
import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContentFinder;
import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContentFinder.Match;

public final class FinderTest {

    private static final int bufferSize = BinaryContentFinder.MAP_SIZE * 3 / 2;
    private BinaryContent content;
    private BinaryContentFinder finder;
    private BinaryContent longContent;

    @BeforeEach
    public void setUp() throws Exception {
        content = new BinaryContent(new File(getClass().getResource(AllTests.resourceData).getPath()));
        final File longFile = AllTests.setUpLongData(bufferSize + 1); // MapSize + 1
        longContent = new BinaryContent(longFile);
    }

    @AfterEach
    public void tearDown() throws Exception {
        content.dispose();
        longContent.dispose();
        AllTests.tearDownLongData();
    }

    private Match getNextMatch(final BinaryContentFinder finder) {
        if (finder == null) {
            throw new IllegalArgumentException("Parameter 'finder' must not be null.");
        }
        final Match match = finder.getNextMatch();
        if (match.getException() != null) {
            throw new RuntimeException(match.getException());
        }
        return match;
    }

    @Test
    public void testFindBackwards() {

        finder = new BinaryContentFinder(new byte[] { (byte) 0x0a }, content);
        assertEquals(0x0a, getNextMatch(finder).getStartPosition());
        assertFalse(getNextMatch(finder).isFound());
        finder.setDirectionForward(false);
        finder.setNewStart(0x0c);
        assertEquals(0x0a, getNextMatch(finder).getStartPosition());

        finder = new BinaryContentFinder(new byte[] { (byte) 0x0a }, content);
        finder.setNewStart(40);
        finder.setDirectionForward(false);
        assertEquals(0x0a, getNextMatch(finder).getStartPosition());
        assertFalse(getNextMatch(finder).isFound());

    }

    @Test
    public void testFindModifyFind() {

        try {
            finder = new BinaryContentFinder(new byte[] { (byte) 0x0a }, content);
            assertEquals(0x0a, getNextMatch(finder).getStartPosition());
            content.insert((byte) 1, 1L);
            assertEquals(0x0b, getNextMatch(finder).getStartPosition());
            assertFalse(getNextMatch(finder).isFound());
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    /*
     * Test method for 'me.glindholm.eclipse.plugin.javahexeditor2.Find.getNextMatch()'
     */
    @Test
    public void testGetNextMatch() {

        finder = new BinaryContentFinder(new byte[] { 0, 2 }, content);
        assertFalse(getNextMatch(finder).isFound());

        finder = new BinaryContentFinder(new byte[] { 0 }, content);
        assertEquals(0, getNextMatch(finder).getStartPosition());

        finder = new BinaryContentFinder(new byte[] { 1 }, content);
        assertEquals(1, getNextMatch(finder).getStartPosition());

        finder = new BinaryContentFinder(new byte[] { (byte) 254 }, content);
        assertEquals(254, getNextMatch(finder).getStartPosition());

        finder = new BinaryContentFinder(new byte[] { (byte) 255 }, content);
        assertEquals(255, getNextMatch(finder).getStartPosition());

    }

    @Test
    public void testIgnoreCase() {

        BinaryContent content3;
        try {
            content3 = new BinaryContent(new File(getClass().getResource(AllTests.resourceUnicode).getPath()));
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
        finder = new BinaryContentFinder("her", content3);
        finder.setCaseSensitive(false);
        assertEquals(2, getNextMatch(finder).getStartPosition());
        assertEquals(3, getNextMatch(finder).getStartPosition());
        assertEquals(33, getNextMatch(finder).getStartPosition());
        assertEquals(34, getNextMatch(finder).getStartPosition());
        assertFalse(getNextMatch(finder).isFound());

        finder.setNewStart(48L);
        finder.setDirectionForward(false);
        final Match match = getNextMatch(finder);
        assertEquals(34, match.getStartPosition());
        assertEquals(6, match.getLength());
        assertEquals(33, getNextMatch(finder).getStartPosition());
        assertEquals(3, getNextMatch(finder).getStartPosition());
        assertEquals(2, getNextMatch(finder).getStartPosition());
        assertFalse(getNextMatch(finder).isFound());
        content3.dispose();
    }

    @Test
    public void testLongFind() {

        finder = new BinaryContentFinder(new byte[] { 0, 0 }, longContent);
        assertEquals(0, getNextMatch(finder).getStartPosition());
        finder.setNewStart(bufferSize - 3);
        assertEquals(bufferSize - 3, getNextMatch(finder).getStartPosition());
        assertEquals(bufferSize - 2, getNextMatch(finder).getStartPosition());
        assertEquals(bufferSize - 1, getNextMatch(finder).getStartPosition());
        assertFalse(getNextMatch(finder).isFound());
    }

    @Test
    public void testSetNewStart() {

        finder = new BinaryContentFinder(new byte[] { (byte) 254 }, content);
        finder.setNewStart(0);
        assertEquals(254, getNextMatch(finder).getStartPosition());
        finder.setNewStart(0);
        assertEquals(254, getNextMatch(finder).getStartPosition());
        finder.setNewStart(254);
        assertEquals(254, getNextMatch(finder).getStartPosition());
        finder.setNewStart(255);
        assertFalse(getNextMatch(finder).isFound());

    }
}
