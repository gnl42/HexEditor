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
package me.glindholm.eclipse.plugin.javahexeditor2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContentActionHistory.Entry;

/**
 * A binary content provider. Content backed by files has no effect on memory footprint. Content
 * backed by memory buffers is limited by amount of memory. Notifies ModifyListeners when it has
 * been modified. Keeps track of the positions where changes have been done. Files that back this
 * content must not be modified while the content is still in use.
 *
 * @author Jordi Bergenthal
 */
public final class BinaryContent {

    /**
     * Used to notify changes in content
     */
    public interface ModifyListener extends EventListener {
        /**
         * Notifies the listener that the content has just been changed
         */
        void modified();
    }

    public static final class RangeSelection {
        public final long start;
        public final long end;

        public RangeSelection(final long start, final long end) {
            if (start < 0) {
                throw new IllegalArgumentException("Parameter start must not be negative, specified value is " + start);
            }
            if (end < 0) {
                throw new IllegalArgumentException("Parameter end must not be negative, specified value is " + end);
            }
            if (end < start) {
                throw new IllegalArgumentException("Parameter end must not be less than start, specified value is " + end + " < " + start);
            }
            this.start = start;
            this.end = end;
        }

        public long getLength() {
            return end - start;
        }

        @Override
        public String toString() {
            return "start=" + start + ", end=" + end;
        }
    }

    /**
     * A subset of data contained in a ByteBuffer or a File
     */
    final static class Range implements Comparable<Range>, Cloneable {
        long position = -1L;
        long length = -1L;
        boolean dirty = true;
        long dataOffset;
        Object data; // ByteBuffer or RandomAccessFile
        File file; // used when data is a RandomAccessFile since we cannot get a
        // File from it

        Range(final long aPosition, final long aLength) {
            position = aPosition;
            length = aLength;
        }

        Range(final long aPosition, final ByteBuffer aBuffer, final boolean isDirty) {
            this(aPosition, aBuffer.remaining());
            data = aBuffer;
            dirty = isDirty;
        }

        Range(final long position, final File file, final boolean isDirty) throws IOException {
            this(position, file.length());
            if (length < 0L) {
                throw new RuntimeException("File length is negative, specified value is " + length + ".");
            }

            this.file = file;
            data = RandomAccessFileFactory.createRandomAccessFile(this.file, "r");
            dirty = isDirty;
        }

        long exclusiveEnd() {
            return position + length;
        }

        @Override
        public Range clone() {
            try {
                return (Range) super.clone();
            } catch (final CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int compareTo(final Range other) {
            if (position < other.position && exclusiveEnd() <= other.position) {
                return -1;
            }
            if (other.position < position && other.exclusiveEnd() <= position) {
                return 1;
            }

            return 0; // overlap
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof Range) {
                return compareTo((Range) obj) == 0; // to maintain contract with
                // Map use
            }
            return false;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return "Range {position:" + position + ", length:" + length + '}';
        }
    }

    public static final long mappedFileBufferLength = 2048 * 1024; // for mapped
    // file I/O

    BinaryContentActionHistory actions; // undo/redo actions history
    BinaryContentActionHistory actionsTemp;
    boolean dirty;
    boolean dirtySize;
    long exclusiveEnd = -1L;
    long lastUpperNibblePosition = -1L;
    ArrayList<ModifyListener> listeners;
    List<Integer> myChanges;
    boolean myChangesInserted = false;
    long myChangesPosition = -1L;
    TreeSet<Range> myRanges;
    Iterator<Range> tailTree;

    /**
     * Create new empty content.
     */
    public BinaryContent() {
        myRanges = new TreeSet<>();
    }

    /**
     * Create new content from a file
     *
     * @param aFile the backing content provider
     * @throws IOException when i/o problems occur. The content will be empty but valid
     */
    public BinaryContent(final File aFile) throws IOException {
        this();
        if (aFile == null || aFile.length() < 1L) {
            return;
        }

        myRanges.add(new Range(0L, aFile, false));
    }

    void actionsOn(final boolean on) {
        if (on) {
            if (actions == null) {
                actions = actionsTemp;
            }
        } else {
            actionsTemp = actions;
            actions = null;
        }
    }

    /**
     * Add a listener to the list of listeners to be notified when there is a change in the content
     *
     * @param listener to be notified of the change
     */
    public void addModifyListener(final ModifyListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }

        listeners.add(listener);
    }

    /**
     * Tells whether a redo is possible
     *
     * @return true if something can be redone
     */
    public boolean canRedo() {
        return actions != null && actions.canRedo();
    }

    /**
     * Tells whether an undo is possible
     *
     * @return true if something can be undone
     */
    public boolean canUndo() {
        return actions != null && actions.canUndo();
    }

    void commitChanges() {
        if (myChanges == null) {
            return;
        }

        final ByteBuffer store = ByteBuffer.allocate(myChanges.size());
        for (final Iterator<Integer> iterator = myChanges.iterator(); iterator.hasNext();) {
            store.put(iterator.next().byteValue());
        }
        store.position(0);
        myChanges = null;
        if (myChangesInserted) {
            insertRange(new Range(myChangesPosition, store, true));
        } else {
            overwriteRange(new Range(myChangesPosition, store, true));
        }
        myChangesInserted = false;
        myChangesPosition = -1L;
    }

    /**
     * Deletes length bytes from the content at the given position
     *
     * @param position start deletion point
     * @param length   number of bytes to delete
     */
    public void delete(final long position, long length) {
        if (position < 0 || position >= length() || length < 1L) {
            return;
        }

        dirty = true;
        dirtySize = true;
        if (length > length() - position) {
            length = length() - position;
        }
        if (actions != null) {
            lastUpperNibblePosition = -1L;
            actions.eventPreModify(BinaryContentActionHistory.TYPE_DELETE, position, length == 1L);
        }
        if (myChanges != null && myChangesInserted && myChangesPosition <= position && myChangesPosition + myChanges.size() >= position + length) {
            final int deleteStart = (int) (position - myChangesPosition);
            final List<Integer> subList = myChanges.subList(deleteStart, deleteStart + (int) length);
            if (actions != null) {
                actions.addDeleted(position, subList, length == 1L);
                if (length > 1) {
                    actions.endAction();
                }
            }
            if (length < myChanges.size()) {
                subList.clear();
            } else { // length == myChanges.size()
                myChanges = null;
                // splitAndShift(position, 0); // mark them as dirty
            }
        } else {
            commitChanges();
            deleteAndShift(position, length);
        }
        notifyListeners();
    }

    void deleteAndShift(final long start, final long length) {
        deleteInternal(start, length);
        initSubtreeTraversing(start, 0L);
        shiftRemainingRanges(-length);
    }

    void deleteInternal(final long startPosition, final long length) {
        if (length < 1L) {
            return;
        }
        initSubtreeTraversing(startPosition, length);
        if (!tailTree.hasNext()) {
            return;
        }

        final ArrayList<Range> deleted = new ArrayList<>();
        final Range firstRange = tailTree.next();
        final Range secondRange = firstRange.clone(); // will be tail part of
        // firstRange
        Range lastRange = null;
        if (firstRange.position < startPosition) {
            firstRange.length = startPosition - firstRange.position;

            secondRange.length = secondRange.exclusiveEnd() - startPosition; // actions
            secondRange.dataOffset += startPosition - secondRange.position; // actions
            secondRange.position = startPosition; // actions
        } else { // firstRange.position == startPosition
            tailTree.remove();
        }
        final long endSoFar = secondRange.exclusiveEnd();
        boolean toBeAdded = false;
        if (endSoFar > exclusiveEnd) {
            lastRange = secondRange.clone();
            toBeAdded = true;
            secondRange.length = exclusiveEnd - secondRange.position; // actions
        }
        deleted.add(secondRange); // actions
        if (endSoFar < exclusiveEnd) {
            while (tailTree.hasNext() && lastRange == null) {
                lastRange = tailTree.next();
                if (lastRange.exclusiveEnd() <= exclusiveEnd) {
                    tailTree.remove();
                    deleted.add(lastRange); // actions
                    lastRange = null;
                }
            }
            if (lastRange != null && lastRange.position < exclusiveEnd) { // actions
                final Range beforeLastRange = lastRange.clone();
                beforeLastRange.length = exclusiveEnd - beforeLastRange.position;
                deleted.add(beforeLastRange);
            }
        }
        if (lastRange != null && lastRange.position < exclusiveEnd && lastRange.exclusiveEnd() > exclusiveEnd) {
            final long delta = exclusiveEnd - lastRange.position;
            lastRange.position += delta;
            lastRange.length -= delta;
            lastRange.dataOffset += delta;
            if (toBeAdded) {
                myRanges.add(lastRange);
            }
        }
        if (actions != null) {
            actions.addLostRanges(deleted);
        }
    }

    private long[] deleteRanges(final List<Range> currentAction) {
        final long[] result = new long[2];
        result[0] = result[1] = currentAction.get(0).position;
        actionsOn(false);
        deleteAndShift(result[0], currentAction.get(currentAction.size() - 1).exclusiveEnd() - result[0]);
        actionsOn(true);

        return result;
    }

    /**
     * Closes all files before termination. After this call the object is no longer valid. Calling
     * dispose() is optional, but it will let use of files immediately in the operating system, instead
     * of having to wait until the object is garbage collected. Note: apparently due to a bug in the
     * Java virtual machine combined with some dumb OS, files won't be freed after this call. See
     * https://forum.java.sun.com/thread.jspa?forumID=4&threadID=158689
     */
    public void dispose() {
        if (myRanges == null) {
            return;
        }

        for (final Range value : myRanges) {
            if (value.data instanceof RandomAccessFile) {
                try {
                    ((RandomAccessFile) value.data).close();
                } catch (final IOException e) {
                    // ok, leave this file alone and close the rest
                }
            }
        }

        if (actions != null) {
            actions.finalize();
        }
        myRanges = null;
        listeners = null;
    }

    int fillWithChanges(final ByteBuffer dst, final long position) {
        final long relativePosition = position - myChangesPosition;
        final int changesSize = myChanges.size();
        if (relativePosition < 0L || relativePosition >= changesSize) {
            return 0;
        }

        int remaining = dst.remaining();
        int i = (int) relativePosition;
        for (; remaining > 0 && i < changesSize; ++i, --remaining) {
            dst.put(myChanges.get(i).byteValue());
        }

        return i - (int) relativePosition;
    }

    int fillWithPartOfRange(final ByteBuffer dst, final Range sourceRange, final long overlapBytes, final int maxCopyLength) throws IOException {
        final int dstInitialPosition = dst.position();
        if (sourceRange.data instanceof final ByteBuffer src) {
            src.limit((int) (sourceRange.dataOffset + sourceRange.length));
            src.position((int) (sourceRange.dataOffset + overlapBytes));
            if (src.remaining() > dst.remaining() || src.remaining() > maxCopyLength) {
                src.limit(src.position() + Math.min(dst.remaining(), maxCopyLength));
            }
            dst.put(src);
        } else if (sourceRange.data instanceof final RandomAccessFile src) {
            final long start = sourceRange.dataOffset + overlapBytes;
            final int length = (int) Math.min(sourceRange.length - overlapBytes, maxCopyLength);
            int limit = -1;
            if (dst.remaining() > length) {
                limit = dst.limit();
                dst.limit(dst.position() + length);
            }
            src.getChannel().read(dst, start);
            if (limit > 0) {
                dst.limit(limit);
            }
        }

        return dst.position() - dstInitialPosition;
    }

    void fillWithRange(final ByteBuffer dst, final Range sourceRange, long overlapBytes, final long position, final List<Long> rangesModified)
            throws IOException {
        long positionSoFar = position;
        if (position < myChangesPosition) {
            final int added = fillWithPartOfRange(dst, sourceRange, overlapBytes, (int) Math.min(myChangesPosition - position, Integer.MAX_VALUE));
            positionSoFar += added;
            overlapBytes += added;
        }
        int changesAdded = 0;
        final long changesPosition = positionSoFar;
        if (myChanges != null && positionSoFar >= myChangesPosition && positionSoFar < myChangesPosition + myChanges.size()
                && overlapBytes < sourceRange.length) {
            changesAdded = fillWithChanges(dst, positionSoFar);
            if (myChangesInserted) {
                positionSoFar += changesAdded;
            } else {
                overlapBytes += changesAdded;
            }
        }

        positionSoFar += fillWithPartOfRange(dst, sourceRange, overlapBytes, Integer.MAX_VALUE);

        if (rangesModified != null) {
            if (sourceRange.dirty) {
                rangesModified.add(position);
                rangesModified.add(positionSoFar - position);
            } else if (changesAdded > 0) {// && !myChangesInserted) {
                rangesModified.add(changesPosition);
                rangesModified.add((long) changesAdded);
                // } else if (myChanges != null && changesPosition >=
                // myChangesPosition && myChangesInserted &&
                // positionSoFar - changesPosition > 0) {
                // rangesModified.add(Long.valueOf(changesPosition));
                // rangesModified.add(Long.valueOf(positionSoFar -
                // changesPosition));
            }
        }
    }

    /**
     * Closes all files for termination
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize() {
        dispose();
    }

    /**
     * Reads a sequence of bytes from this content into the given buffer, starting at the given position
     *
     * @param dst      where to write the read result to
     * @param position starting read point
     * @return number of bytes read
     * @throws IOException
     */
    public int get(final ByteBuffer dst, final long position) throws IOException {
        return get(dst, null, position);
    }

    /**
     * Reads a sequence of bytes from this content into the given buffer, starting at the given position
     *
     * @param dst            where to write the read result to
     * @param rangesModified ordered Long's sequence (range start, range length, ...). Return variable
     *                       that specifies current ranges of modified content. Useful for highlighting
     *                       changes in content.
     * @param position       starting read point
     * @return number of bytes read
     * @throws IOException
     */
    public int get(final ByteBuffer dst, final List<Long> rangesModified, final long position) throws IOException {
        if (rangesModified != null) {
            rangesModified.clear();
        }
        long positionShift = 0;
        final int dstInitialRemaining = dst.remaining();
        if (myChanges != null && myChangesInserted && position > myChangesPosition) {
            positionShift = (int) Math.min(myChanges.size(), position - myChangesPosition);
        }

        long positionSoFar = position - positionShift;
        initSubtreeTraversing(positionSoFar, dst.remaining());

        Range partialRange = null;
        while (tailTree.hasNext() && (partialRange = tailTree.next()).position < exclusiveEnd) {
            fillWithRange(dst, partialRange, positionSoFar - partialRange.position, positionSoFar + positionShift, rangesModified); // throws IOException
            positionSoFar = partialRange.exclusiveEnd();
            if (myChanges != null && myChangesInserted && positionSoFar + positionShift > myChangesPosition) {
                positionShift = myChanges.size();
            }
        }
        if (dst.remaining() > 0 && myChanges != null && positionSoFar + positionShift < myChangesPosition + myChanges.size()) {
            final int size = fillWithChanges(dst, positionSoFar + positionShift);
            if (rangesModified != null) {
                rangesModified.add(positionSoFar + positionShift);
                rangesModified.add((long) size);
            }
        }

        return dstInitialRemaining - dst.remaining();
    }

    /**
     * Reads the sequence of all bytes from this content into the given file
     *
     * @param destinationFile where to write the read result to
     * @return number of bytes read
     * @throws IOException
     */
    public long get(final File destinationFile) throws IOException {
        return get(destinationFile, 0L, length());
    }

    /**
     * Reads a sequence of bytes from this content into the given file
     *
     * @param destinationFile where to write the read result to
     * @param start           first byte in sequence
     * @param length          number of bytes to read
     * @return number of bytes read
     * @throws IOException
     */
    public long get(final File destinationFile, final long start, final long length) throws IOException {
        if (start < 0L || length < 0L || start + length > length()) {
            return 0L;
        }

        if (actions != null) {
            actions.endAction();
        }
        commitChanges();
        final RandomAccessFile dst = RandomAccessFileFactory.createRandomAccessFile(destinationFile, "rws");
        IOException preCloseException = null;
        try {
            dst.setLength(length);
            final FileChannel channel = dst.getChannel();

            ByteBuffer buffer = null;
            for (long position = 0L; position < length; position += mappedFileBufferLength) {
                final int partLength = (int) Math.min(mappedFileBufferLength, length - position);
                boolean bufferFromMap = true;
                try {
                    buffer = channel.map(FileChannel.MapMode.READ_WRITE, position, partLength);
                } catch (final IOException e) {
                    // gcj 4.3.0 channel maps work differently than sun's:
                    // gcj won't accept two calls to channel.map with a
                    // different position, sun does
                    // gcj will happily accept maps of size bigger than
                    // available memory, sun won't
                    // to access past the 2Gb barrier there is no choice but use
                    // plain ByteBuffers in gcj
                    bufferFromMap = false;
                    if (buffer == null) {
                        buffer = ByteBuffer.allocateDirect((int) mappedFileBufferLength);
                    }
                    buffer.position(0);
                    buffer.limit(partLength);
                }
                get(buffer, start + position);
                if (bufferFromMap) {
                    ((MappedByteBuffer) buffer).force();
                    buffer = null;
                } else {
                    buffer.position(0);
                    buffer.limit(partLength);
                    channel.write(buffer, position);
                }
            }
            channel.force(true);
            channel.close();
        } catch (final IOException e) {
            preCloseException = e;
        }
        try {
            dst.close();
        } catch (final IOException e) {
            if (preCloseException == null) {
                throw e;
            }
            throw preCloseException; // throw previous exception instead
        }
        if (preCloseException != null) {
            throw preCloseException;
        }

        return length;
    }

    /*
     * Does not check myChanges
     */
    private int getFromRanges(final long position) throws IOException {
        int result = 0;
        final Range range = getRangeAt(position);
        if (range != null) {
            final Object value = range.data;
            if (value instanceof final ByteBuffer data) {
                data.limit(data.capacity());
                data.position((int) range.dataOffset);
                result = data.get((int) (position - range.position)) & 0x0ff;
            } else if (value instanceof final RandomAccessFile randomFile) {
                randomFile.seek(position);
                result = randomFile.read();
            }
        }

        return result;
    }

    /**
     * Get the list of files that back this object.
     *
     * @return list of File's. It is not a live list (changes are not propagated)
     */
    public List<File> getOpenFiles() {
        final HashSet<File> result = new HashSet<>();
        if (myRanges == null || myRanges.size() == 0) {
            return new ArrayList<>(result);
        }

        for (final Range value : myRanges) {
            if (value.data instanceof RandomAccessFile && value.file != null) {
                result.add(value.file);
            }
        }

        return new ArrayList<>(result);
    }

    Range getRangeAt(final long position) {
        final SortedSet<Range> subSet = myRanges.tailSet(new Range(position, 1L));
        if (subSet.isEmpty()) {
            return null;
        }

        return subSet.first();
    }

    SortedSet<Range> initSubtreeTraversing(final long position, final long length) {
        final SortedSet<Range> result = myRanges.tailSet(new Range(position, 1L));
        tailTree = result.iterator();
        exclusiveEnd = position + length;
        if (exclusiveEnd > length()) {
            exclusiveEnd = length();
        }

        return result;
    }

    /**
     * Inserts a byte into this content at the given position
     *
     * @param source   byte
     * @param position insert point
     * @throws IOException
     */
    public void insert(final byte source, final long position) throws IOException {
        if (position > length()) {
            return;
        }

        dirty = true;
        dirtySize = true;
        lastUpperNibblePosition = position;
        if (actions != null) {
            actions.eventPreModify(BinaryContentActionHistory.TYPE_INSERT, position, true);
        }
        updateChanges(position, true);
        myChanges.set((int) (position - myChangesPosition), source & 0x0ff);
        notifyListeners();
    }

    /**
     * Inserts a sequence of bytes from the given buffer into this content, starting at the given
     * position and shifting the existing ones.
     *
     * @param source   bytes. The buffer is not copied internally, changes after this call will result
     *                 in undefined behaviour.
     * @param position starting insert point
     */
    public void insert(final ByteBuffer source, final long position) {
        if (source.remaining() < 1 || position > length()) {
            return;
        }

        dirty = true;
        dirtySize = true;
        lastUpperNibblePosition = -1L;
        if (actions != null) {
            actions.eventPreModify(BinaryContentActionHistory.TYPE_INSERT, position, false);
        }
        commitChanges();
        final Range newRange = new Range(position, source, true);
        insertRange(newRange);
        if (actions != null) {
            actions.addInserted(newRange.clone());
        }
        notifyListeners();
    }

    /**
     * Inserts a sequence of bytes from the given file into this content, starting at the given position
     * and shifting the existing ones.
     *
     * @param aFile    The file is not copied internally, changes after this call will result in
     *                 undefined behaviour.
     * @param position starting insert point
     * @throws IOException when i/o problems occur. The content stays unchanged and valid
     */
    public void insert(final File aFile, final long position) throws IOException {
        final long fileLength = aFile.length();
        if (fileLength < 1L || position > length()) {
            return;
        }

        final Range newRange = new Range(position, aFile, true);
        dirty = true;
        dirtySize = true;
        lastUpperNibblePosition = -1L;
        if (actions != null) {
            actions.eventPreModify(BinaryContentActionHistory.TYPE_INSERT, position, false);
        }
        commitChanges();
        insertRange(newRange);
        if (actions != null) {
            actions.addInserted(newRange.clone());
        }
        notifyListeners();
    }

    private void insertRange(final Range newRange) {
        splitAndShift(newRange.position, newRange.length);
        myRanges.add(newRange);
    }

    private long[] insertRanges(final List<Range> ranges) {
        final Range firstRange = ranges.get(0);
        final Range lastRange = ranges.get(ranges.size() - 1);
        splitAndShift(firstRange.position, lastRange.exclusiveEnd() - firstRange.position);
        final ArrayList<Range> cloned = new ArrayList<>(ranges.size());
        for (final Range range : ranges) {
            cloned.add(range.clone());
        }
        myRanges.addAll(cloned);

        return new long[] { firstRange.position, lastRange.exclusiveEnd() };
    }

    /**
     * Tells whether changes have been done to the original content
     *
     * @return true: the content has been modified
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Tells whether changes have been done to the original content's size
     *
     * @return true: the content has been modified in size
     */
    public boolean isDirtySize() {
        return dirtySize;
    }

    /**
     * Number of bytes in content
     *
     * @return length of content in byte units
     */
    public long length() {
        long result = 0L;

        if (myRanges.size() > 0) {
            result = myRanges.last().exclusiveEnd();
        }

        if (myChanges != null && myChangesInserted) {
            result += myChanges.size();
        }

        return result;
    }

    void notifyListeners() {
        if (listeners == null) {
            return;
        }

        for (final ModifyListener listener : listeners) {
            listener.modified();
        }
    }

    /**
     * Writes a byte into this content at the given position
     *
     * @param source   byte
     * @param position overwrite point
     * @throws IOException
     */
    public void overwrite(final byte source, final long position) throws IOException {
        overwrite(source, 0, 8, position);
    }

    /**
     * Writes a byte into this content at the given position with bit offset and length bits Examples:
     * previous content 0000 0000, source 1111 1111, offset 0, length 8 -> resulting content 1111 1111
     * previous content 0000 0000, source 1111 1111, offset 1, length 2 -> resulting content 0110 0000
     * previous content 0000 0000, source stuv wxyz, offset 2, length 5 -> resulting content 00vw xyz0
     * <P>
     * When action history is on, considers the special case of user generated input(in hex) from a
     * keyboard, in which nibbles are input in different calls to this method: the lower nibble input is
     * not considered in action history so undoing/redoing takes effect on the whole byte.
     * <P>
     *
     * @param source   byte, interesting bits are to the right
     * @param offset   bit offset (0 <= offset < 8)
     * @param length   number of bits to copy
     * @param position overwrite point
     * @throws IOException
     */
    public void overwrite(final byte source, final int offset, int length, final long position) throws IOException {
        if (offset < 0 || offset > 7 || length < 0 || position >= length()) {
            return;
        }

        dirty = true;
        if (actions != null) {
            if (lastUpperNibblePosition == position && offset == 4 && length == 4) {
                actionsOn(false);
            } else {
                actions.eventPreModify(BinaryContentActionHistory.TYPE_OVERWRITE, position, true);
            }
        }
        if (length + offset > 8) {
            length = 8 - offset;
        }
        final Range range = updateChanges(position, false);
        final int previous = myChanges.get((int) (position - myChangesPosition));
        final int mask = 0x0ff >>> offset & 0x0ff << 8 - offset - length;
        final int newValue = previous & ~mask | source << 8 - offset - length & mask;
        myChanges.set((int) (position - myChangesPosition), newValue);
        if (actions != null) {
            if (range == null) {
                actions.addLostByte(position, previous);
            } else {
                final Range clone = range.clone();
                clone.position = position;
                clone.length = 1L;
                clone.dataOffset = range.dataOffset + position - range.position;
                // clone.dirty = true;
                actions.addLostRange(clone);
            }
        }
        actionsOn(true);
        lastUpperNibblePosition = actions != null && offset == 0 && length == 4 ? position : -1L;
        notifyListeners();
    }

    /**
     * Writes a sequence of bytes from the given buffer into this content, starting at the given
     * position and overwriting the existing ones.
     *
     * @param source   bytes. The buffer is not copied internally, changes after this call will result
     *                 in undefined behaviour.
     * @param position starting overwrite point
     */
    public void overwrite(final ByteBuffer source, final long position) {
        if (source.remaining() > 0 && position < length()) {
            overwriteInternal(new Range(position, source, true));
        }
    }

    /**
     * Writes a sequence of bytes from the given file into this content, starting at the given position
     * and overwriting the existing ones. Changes to the file after this call will result in undefined
     * behaviour.
     *
     * @param aFile    with source bytes.
     * @param position starting overwrite point
     * @throws IOException when i/o problems occur. The content stays unchanged and valid
     */
    public void overwrite(final File aFile, final long position) throws IOException {
        if (aFile.length() > 0L && position < length()) {
            overwriteInternal(new Range(position, aFile, true));
        }
    }

    void overwriteInternal(final Range newRange) {
        dirty = true;
        lastUpperNibblePosition = -1L;
        if (actions != null) {
            actions.eventPreModify(BinaryContentActionHistory.TYPE_OVERWRITE, newRange.position, false);
        }
        commitChanges();
        overwriteRange(newRange);
        if (actions != null) {
            actions.addRangeToCurrentAction(newRange.clone());
        }
        notifyListeners();
    }

    private void overwriteRange(final Range aRange) {
        deleteInternal(aRange.position, aRange.length);
        myRanges.add(aRange);
    }

    private long[] overwriteRanges(final List<Range> ranges) {
        final Range firstRange = ranges.get(0);
        final Range lastRange = ranges.get(ranges.size() - 1);
        splitAndShift(firstRange.position, 0);
        splitAndShift(lastRange.exclusiveEnd(), 0);
        initSubtreeTraversing(firstRange.position, 0L);
        if (tailTree.hasNext()) {
            Range goingRange = tailTree.next();
            while (goingRange != null && goingRange.exclusiveEnd() <= lastRange.exclusiveEnd()) {
                tailTree.remove();
                goingRange = null;
                if (tailTree.hasNext()) {
                    goingRange = tailTree.next();
                }
            }
        }
        final ArrayList<Range> cloned = new ArrayList<>(ranges.size());
        for (final Range range : ranges) {
            cloned.add(range.clone());
        }
        myRanges.addAll(cloned);

        return new long[] { firstRange.position, lastRange.exclusiveEnd() };
    }

    /**
     * Redoes last action on BinaryContent. Action history should be on: setActionHistory()
     *
     * @return 2 elements long array, first one the start point (inclusive) of finished undo operation,
     *         second one the end point (exclusive). <code>null</code> if redo is not performed
     */
    public long[] redo() {
        if (actions == null) {
            return null;
        }

        final Entry entry = actions.redoAction();
        if (entry == null) {
            return null;
        }

        long[] result = null;
        final List<Range> ranges = entry.getRanges();
        if (entry.getActionType() == BinaryContentActionHistory.TYPE_DELETE) {
            result = deleteRanges(ranges);
        } else if (entry.getActionType() == BinaryContentActionHistory.TYPE_INSERT) {
            result = insertRanges(ranges);
        } else if (entry.getActionType() == BinaryContentActionHistory.TYPE_OVERWRITE) {
            // 0 to size - 1: overwritten ranges, last one: overwriter range
            final int size = ranges.size();
            result = overwriteRanges(ranges.subList(size - 1, size));
        }
        notifyListeners();

        return result;
    }

    /**
     * Remove a listener to the list of listeners to be notified when there is a change in the content
     *
     * @param listener not to be notified of the change
     */
    public void removeModifyListener(final ModifyListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Sets action history on. After this call the content will remember past actions to undo and redo
     */
    public void setActionsHistory() {
        if (actions == null) {
            commitChanges();
            actions = new BinaryContentActionHistory(this);
        }
    }

    void shiftRemainingRanges(final long increment) {
        if (increment == 0L) {
            return;
        }

        while (tailTree.hasNext()) {
            final Range currentRange = tailTree.next();
            currentRange.position += increment;
            // currentRange.dirty = true;
        }
    }

    void splitAndShift(final long position, final long increment) {
        initSubtreeTraversing(position, 0);
        if (!tailTree.hasNext()) {
            return;
        }

        final Range firstRange = tailTree.next();
        Range secondRange = null;
        if (firstRange.position < position) {
            secondRange = firstRange.clone(); // will be tail part of
            // firstRange
            final long delta = position - firstRange.position;
            firstRange.length = delta;

            secondRange.length -= delta;
            secondRange.dataOffset += delta;
            secondRange.position = secondRange.position + delta + increment;
            // secondRange.dirty |= increment != 0;
        } else {
            firstRange.position += increment;
            // firstRange.dirty |= increment != 0;
        }
        shiftRemainingRanges(increment);
        if (secondRange != null) {
            myRanges.add(secondRange);
        }
    }

    /**
     * Lists the ranges that back this content
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder("BinaryContent: length=").append(length()).append("}\n");
        for (final Range range : myRanges) {
            result.append(range.toString()).append('\n');
        }
        return result.toString();
    }

    /**
     * Undoes last action on BinaryContent. Action history should be on: setActionHistory()
     *
     * @return 2 elements long array, first one the start point (inclusive) of finished undo operation,
     *         second one the end point (exclusive). <code>null</code> if undo is not performed
     */
    public long[] undo() {
        if (actions == null) {
            return null;
        }

        final Entry entry = actions.undoAction();
        if (entry == null) {
            return null;
        }

        commitChanges();
        long[] result = null;
        final List<Range> ranges = entry.getRanges();
        if (entry.getActionType() == BinaryContentActionHistory.TYPE_DELETE) {
            result = insertRanges(ranges);
        } else if (entry.getActionType() == BinaryContentActionHistory.TYPE_INSERT) {
            result = deleteRanges(ranges);
        } else if (entry.getActionType() == BinaryContentActionHistory.TYPE_OVERWRITE) {
            // 0 to size - 1: overwritten ranges, last one: overwriter range
            result = overwriteRanges(ranges.subList(0, ranges.size() - 1));
        }
        notifyListeners();

        return result;
    }

    private Range updateChanges(final long position, final boolean insert) throws IOException {
        Range result = null;
        if (myChanges != null) {
            long lowerLimit = myChangesPosition;
            final long upperLimit = myChangesPosition + myChanges.size();
            if (!insert && position >= lowerLimit && position < upperLimit) {
                return result; // reuse without expanding
            }

            if (!insert) {
                --lowerLimit;
            }
            if (insert == myChangesInserted && position >= lowerLimit && position <= upperLimit) { // reuse
                if (insert) {
                    myChanges.add((int) (position - myChangesPosition), 0);
                } else {
                    result = getRangeAt(position);
                    if (myChangesPosition > position) {
                        myChangesPosition = position;
                        myChanges.add(0, getFromRanges(position));
                    } else if (myChangesPosition + myChanges.size() <= position) {
                        myChanges.add(getFromRanges(position));
                    }
                }
                return result;

            }
            commitChanges();

        }
        myChanges = new ArrayList<>();
        myChanges.add(getFromRanges(position));
        myChangesInserted = insert;
        myChangesPosition = position;
        if (!insert) {
            result = getRangeAt(position);
        }

        return result;
    }
}
