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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent;

public final class BinaryContentTest {

    private BinaryContent content;
    private ByteBuffer data;

    @BeforeEach
    public void setUp() throws Exception {
        content = new BinaryContent(TestUtilities.getDataFile(TestUtilities.resourceData));
        data = ByteBuffer.allocate(8);
    }

    @AfterEach
    public void tearDown() throws Exception {
        content.dispose();
        data = null;
    }

    @Test
    public void testCopyManyFileMaps() {
        try {
            final long length = BinaryContent.mappedFileBufferLength;
            if (length * 2 > Integer.MAX_VALUE) {
                fail("Test not appropriate for current setup. Update test.");
            }

            final File longFile = TestUtilities.setUpLongData(length * 3);
            final BinaryContent longContent = new BinaryContent(longFile);
            final File file = File.createTempFile("JavaHexEditor", ".tmp");
            longContent.get(file);
            assertEquals(longFile.length(), file.length());
            longContent.dispose();
            file.deleteOnExit();
            TestUtilities.tearDownLongData();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDelete() {
        try {
            content.delete(0, 1);
            content.delete(0, 2);
            content.delete(12, 1);
            content.delete(11, 2);
            content.delete(249, 1);
            content.delete(247, 2);
            assertEquals(8, content.get(data, 0));
            assertEquals(3, data.get(0));

            data.position(0);
            assertEquals(8, content.get(data, 10));
            assertEquals(13, data.get(0));
            assertEquals(17, data.get(1));

            data.position(0);
            assertEquals(1, content.get(data, 246));
            assertEquals(252, data.get(0) & 0x0ff);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInsertByte() {
        try {
            content.insert((byte) 13, 256);
            assertEquals(4, content.get(data, 253));
            assertEquals((byte) 255, data.get(2));
            assertEquals(13, data.get(3));
            content.insert((byte) 14, 0);
            content.insert((byte) 15, 5);

            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(14, data.get(0));
            assertEquals(0, data.get(1));

            data.position(0);
            assertEquals(8, content.get(data, 4));
            assertEquals(3, data.get(0));
            assertEquals(15, data.get(1));
            assertEquals(4, data.get(2));

            data.position(0);
            assertEquals(6, content.get(data, 253));
            assertEquals((byte) 255, data.get(4));
            assertEquals(13, data.get(5));

            data.position(0);
            content.insert((byte) 14, 259);
            content.insert((byte) 15, 260);
            assertEquals(1, content.get(data, 260));
            assertEquals(15, data.get(0));

            final BinaryContent empty = new BinaryContent();
            empty.insert((byte) 33, 0L);
            data.position(0);
            assertEquals(1, empty.get(data, 0));
            assertEquals(33, data.get(0));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInsertAtBeginningOfFile() {
        try {
            content.delete(1L, 255L);
            content.insert((byte) 2, 0L);
            content.insert((byte) 1, 0L);
            content.insert((byte) 0, 0L);
            data.position(0);
            assertEquals(4, content.get(data, 0));
            assertEquals(0, data.get(0));
            assertEquals(1, data.get(1));
            assertEquals(2, data.get(2));
            assertEquals(0, data.get(3));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInsertThenDelete() {
        try {
            content.insert((byte) 64, 5);
            content.delete(5, 1);
            assertEquals(8, content.get(data, 0));
            assertEquals(4, data.get(4));
            assertEquals(5, data.get(5));
            assertEquals(6, data.get(6));

            content.insert((byte) 64, 5);
            content.delete(5, 1);
            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(4, data.get(4));
            assertEquals(5, data.get(5));
            assertEquals(6, data.get(6));

            content.insert((byte) 64, 5);
            content.delete(5, 2);
            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(4, data.get(4));
            assertEquals(6, data.get(5));
            assertEquals(7, data.get(6));

            content.insert((byte) 64, 5);
            content.delete(4, 2);
            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(3, data.get(3));
            assertEquals(6, data.get(4));
            assertEquals(7, data.get(5));

            content.insert((byte) 64, 5);
            content.delete(4, 3);
            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(3, data.get(3));
            assertEquals(8, data.get(4));
            assertEquals(9, data.get(5));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Test method for overwrite(byte)
     */
    @Test
    public void testOverwriteByte() {
        try {
            assertEquals(8, content.get(data, 5));
            assertEquals(5, data.get(0));

            content.overwrite((byte) 13, 255);
            content.overwrite((byte) 14, 0);
            content.overwrite((byte) 15, 5);
            data.position(0);
            assertEquals(8, content.get(data, 0));
            assertEquals(14, data.get(0));
            assertEquals(1, data.get(1));

            data.position(0);
            assertEquals(8, content.get(data, 4));
            assertEquals(4, data.get(0));
            assertEquals(15, data.get(1));
            assertEquals(6, data.get(2));

            data.position(0);
            assertEquals(6, content.get(data, 250));
            assertEquals((byte) 254, data.get(4));
            assertEquals(13, data.get(5));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testOverwriteThenInsert() {
        try {
            content.overwrite((byte) 33, 128);
            content.insert((byte) 43, 129);
            assertEquals(8, content.get(data, 127));
            assertEquals(127, data.get(0));
            assertEquals(33, data.get(1));
            assertEquals(43, data.get(2));
            assertEquals((byte) 129, data.get(3));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRangesModified() {
        try {
            final List<Long> rangesModified = new ArrayList<>();

            content.overwrite((byte) 13, 128);
            assertEquals(8, content.get(data, rangesModified, 125));
            assertEquals(2, rangesModified.size());
            assertEquals(128, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());

            content.insert((byte) 14, 127);
            data.position(0);
            assertEquals(8, content.get(data, rangesModified, 125));
            assertEquals(4, rangesModified.size());
            assertEquals(127, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());
            assertEquals(129, rangesModified.get(2).intValue());
            assertEquals(1, rangesModified.get(3).intValue());

            content.overwrite((byte) 15, 126);
            data.position(0);
            assertEquals(8, content.get(data, rangesModified, 125));
            assertEquals(6, rangesModified.size());
            assertEquals(126, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());
            assertEquals(127, rangesModified.get(2).intValue());
            assertEquals(1, rangesModified.get(3).intValue());
            assertEquals(129, rangesModified.get(4).intValue());
            assertEquals(1, rangesModified.get(5).intValue());

            final BinaryContent empty = new BinaryContent();
            empty.insert((byte) 33, 0L);
            data.position(0);
            assertEquals(1, empty.get(data, rangesModified, 0));
            assertEquals(2, rangesModified.size());
            assertEquals(0, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRangesModifiedAtEndOfFile() {
        try {
            final List<Long> rangesModified = new ArrayList<>();

            content.insert((byte) 13, 256);
            assertEquals(7, content.get(data, rangesModified, 250));
            assertEquals(2, rangesModified.size());
            assertEquals(256, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRangesModifiedUnconnexInserts() {
        try {
            final List<Long> rangesModified = new ArrayList<>();

            content.delete(2, 254);
            content.insert((byte) 13, 0);
            content.insert((byte) 14, 2);
            assertEquals(4, content.get(data, rangesModified, 0));
            assertEquals(4, rangesModified.size());
            assertEquals(0, rangesModified.get(0).intValue());
            assertEquals(1, rangesModified.get(1).intValue());
            assertEquals(2, rangesModified.get(2).intValue());
            assertEquals(1, rangesModified.get(3).intValue());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
