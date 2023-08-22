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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Display;

import me.glindholm.eclipse.plugin.javahexeditor2.common.TextUtility;

/**
 * A clipboard for binary content. Data up to 4 Mbytes is made available as text as well
 *
 * @author Jordi Bergenthal
 */
final class BinaryContentClipboard {

    public static String CLIPBOARD_FOLDER_PATH;
    public static final String CLIPBOARD_FILE_NAME = "javahexeditorClipboard.tmp";
    public static final String CLIPBOARD_FILE_NAME_PASTED = "javahexeditorPasted{0}.tmp";

    private static class FileByteArrayTransfer extends ByteArrayTransfer {
        private static final String MYTYPENAME = "myFileByteArrayTypeName";
        private static final int MYTYPEID = registerType(MYTYPENAME);
        private static FileByteArrayTransfer instance = new FileByteArrayTransfer();

        private FileByteArrayTransfer() {
        }

        public static FileByteArrayTransfer getInstance() {
            return instance;
        }

        @Override
        public void javaToNative(final Object object, final TransferData transferData) {
            if (object == null || !(object instanceof File)) {
                return;
            }

            if (isSupportedType(transferData)) {
                final File myType = (File) object;
                try {
                    // write data to a byte array and then ask super to convert
                    // to pMedium
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    final DataOutputStream writeOut = new DataOutputStream(out);
                    byte[] buffer = myType.getAbsolutePath().getBytes();
                    writeOut.writeInt(buffer.length);
                    writeOut.write(buffer);
                    buffer = out.toByteArray();
                    writeOut.close();

                    super.javaToNative(buffer, transferData);

                } catch (final IOException e) {
                } // copy nothing then
            }
        }

        @Override
        public Object nativeToJava(final TransferData transferData) {
            if (!isSupportedType(transferData)) {
                return null;
            }

            final byte[] buffer = (byte[]) super.nativeToJava(transferData);
            if (buffer == null) {
                return null;
            }

            File myData = null;
            final DataInputStream readIn = new DataInputStream(new ByteArrayInputStream(buffer));
            try {
                final int size = readIn.readInt();
                if (size < 1) {
                    return null;
                }
                final byte[] name = new byte[size];
                if (readIn.read(name) < size) {
                    return null;
                }
                myData = new File(new String(name));
            } catch (final IOException ex) {
            } // paste nothing then

            return myData;
        }

        @Override
        protected String[] getTypeNames() {
            return new String[] { MYTYPENAME };
        }

        @Override
        protected int[] getTypeIds() {
            return new int[] { MYTYPEID };
        }
    }

    private static class MemoryByteArrayTransfer extends ByteArrayTransfer {
        private static final String MYTYPENAME = "myMemoryByteArrayTypeName";
        private static final int MYTYPEID = registerType(MYTYPENAME);

        private static MemoryByteArrayTransfer instance = new MemoryByteArrayTransfer();

        private MemoryByteArrayTransfer() {
        }

        public static MemoryByteArrayTransfer getInstance() {
            return instance;
        }

        @Override
        public void javaToNative(final Object object, final TransferData transferData) {
            if (object == null || !(object instanceof byte[])) {
                return;
            }

            if (isSupportedType(transferData)) {
                final byte[] buffer = (byte[]) object;
                super.javaToNative(buffer, transferData);
            }
        }

        @Override
        public Object nativeToJava(final TransferData transferData) {
            Object result = null;
            if (isSupportedType(transferData)) {
                result = super.nativeToJava(transferData);
            }

            return result;
        }

        @Override
        protected String[] getTypeNames() {
            return new String[] { MYTYPENAME };
        }

        @Override
        protected int[] getTypeIds() {
            return new int[] { MYTYPEID };
        }
    }

    private final File clipboardFile;

    // 4 Megs for byte[], 4 Megs for text
    private static final long maxClipboardDataInMemory = 4 * 1024 * 1024;
    private final Clipboard myClipboard;
    private final Map<File, Integer> myFilesReferencesCounter;

    static {
        final File tempFolder = new File(System.getProperty("java.io.tmpdir", "."));
        CLIPBOARD_FOLDER_PATH = tempFolder.getAbsolutePath();
    }

    /**
     * Init system resources for the clipboard
     *
     * @param display
     */
    public BinaryContentClipboard(final Display display) {
        clipboardFile = new File(new File(CLIPBOARD_FOLDER_PATH), CLIPBOARD_FILE_NAME);

        myClipboard = new Clipboard(display);
        myFilesReferencesCounter = new HashMap<>();
    }

    public static boolean deleteFileALaMs(final File file) {
        // horrible hack for even worse M$ OS
        final long time = System.currentTimeMillis();
        boolean success = false;
        while (!(success = file.delete()) && System.currentTimeMillis() - time < 1234L) {
            System.gc();
            try {
                Thread.sleep(333);
            } catch (final InterruptedException e) { /* Keep trying */
            }
        }
        if (success) {
            System.gc();
        }
        return success;
    }

    /**
     * Dispose system clipboard and file resources
     *
     * @throws IOException
     *
     * @see Clipboard#dispose()
     */
    public void dispose() throws IOException {
        final File lastPaste = (File) myClipboard.getContents(FileByteArrayTransfer.getInstance());
        myClipboard.dispose();

        if (!clipboardFile.equals(lastPaste)) { // null
            emptyClipboardFile();
        }

        for (final File aFile : myFilesReferencesCounter.keySet()) {
            final int count = myFilesReferencesCounter.get(aFile);
            final File lock = getLockFromFile(aFile);
            if (updateLock(lock, -count)) { // lock deleted
                if (!deleteFileALaMs(aFile)) {
                    aFile.deleteOnExit();
                }
            }
        }
    }

    private void emptyClipboardFile() {
        if (clipboardFile.canWrite() && clipboardFile.length() > 0L) {
            try {
                final RandomAccessFile file = RandomAccessFileFactory.createRandomAccessFile(clipboardFile, "rw");
                file.setLength(0L);
                file.close();
            } catch (final IOException e) {
            } // ok, leave it alone
        }
    }

    /**
     * Dispose system clipboard resources
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize() throws IOException {
        dispose();
    }

    /**
     * Paste the clipboard contents into a BinaryContent
     *
     * @param content
     * @param start
     * @param insert
     * @return
     */
    public long getContents(final BinaryContent content, final long start, final boolean insert) {
        long total = tryGettingFiles(content, start, insert);
        if (total >= 0L) {
            return total;
        }

        total = tryGettingMemoryByteArray(content, start, insert);
        if (total >= 0L) {
            return total;
        }

        total = tryGettingFileByteArray(content, start, insert);
        if (total >= 0L) {
            return total;
        }

        return 0L;
    }

    private File getLockFromFile(final File lastPaste) {
        final String name = lastPaste.getAbsolutePath();
        return new File(name.substring(0, name.length() - 3) + "lock");
    }

    /**
     * Tells whether there is valid data in the clipboard
     *
     * @return true: data is available
     */
    public boolean hasContents() {
        final TransferData[] available = myClipboard.getAvailableTypes();
        for (final TransferData element : available) {
            if (MemoryByteArrayTransfer.getInstance().isSupportedType(element) || TextTransfer.getInstance().isSupportedType(element)
                    || FileByteArrayTransfer.getInstance().isSupportedType(element) || FileTransfer.getInstance().isSupportedType(element)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Set the clipboard contents with a BinaryContent
     *
     * @param content
     * @param start
     * @param length
     */
    public void setContents(final BinaryContent content, final long start, final long length) {
        if (length < 1L) {
            return;
        }

        Object[] data = null;
        Transfer[] transfers = null;
        try {
            if (length <= maxClipboardDataInMemory) {
                final byte[] byteArrayData = new byte[(int) length];
                content.get(ByteBuffer.wrap(byteArrayData), start);
                final String textData = new String(byteArrayData);
                transfers = new Transfer[] { MemoryByteArrayTransfer.getInstance(), TextTransfer.getInstance() };
                data = new Object[] { byteArrayData, textData };
            } else {
                content.get(clipboardFile, start, length);
                transfers = new Transfer[] { FileByteArrayTransfer.getInstance() };
                data = new Object[] { clipboardFile };
            }
        } catch (final IOException e) {
            myClipboard.setContents(new Object[] { new byte[1] }, new Transfer[] { MemoryByteArrayTransfer.getInstance() });
            myClipboard.clearContents();
            emptyClipboardFile();
            return; // copy nothing then
        }
        myClipboard.setContents(data, transfers);
    }

    /*
     * The file is being reference counted. It will be deleted as soon as no javahexeditor process is
     * referencing it anymore.
     */
    private long tryGettingFileByteArray(final BinaryContent content, final long start, final boolean insert) {
        File lastPaste = (File) myClipboard.getContents(FileByteArrayTransfer.getInstance());
        if (lastPaste == null) {
            return -1L;
        }
        long total = lastPaste.length();
        if (!insert && total > content.length() - start) {
            return 0L;
        }

        File lock = null;
        if (clipboardFile.equals(lastPaste)) {
            for (int i = 0; i < 9999; ++i) {
                final String fileName = TextUtility.format(CLIPBOARD_FILE_NAME_PASTED, String.valueOf(i));
                final File clipboardDir = new File(CLIPBOARD_FOLDER_PATH);
                lastPaste = new File(clipboardDir, fileName);
                lock = new File(clipboardDir, fileName + ".lock");
                if (!lock.exists()) {
                    if (!lastPaste.exists() || lastPaste.delete()) {
                        break;
                    }
                }
            }
            if (lastPaste.exists() || lock != null && lock.exists()) {
                return 0L;
            }
            clipboardFile.renameTo(lastPaste);
            myClipboard.setContents(new Object[] { lastPaste }, new Transfer[] { FileByteArrayTransfer.getInstance() });
        } else {
            lock = getLockFromFile(lastPaste);
        }
        try {
            if (insert) {
                content.insert(lastPaste, start);
            } else {
                content.overwrite(lastPaste, start);
            }
        } catch (final IOException e) {
            total = 0L;
        }
        if (total > 0L) {
            try {
                updateLock(lock, 1);
            } catch (final IOException e) {
                myFilesReferencesCounter.remove(lastPaste);
                return total;
            }
            final Integer value = myFilesReferencesCounter.put(lastPaste, 1);
            if (value != null) {
                myFilesReferencesCounter.put(lastPaste, value.intValue() + 1);
            }
        }

        return total;
    }

    private long tryGettingFiles(final BinaryContent content, long start, final boolean insert) {
        final String[] files = (String[]) myClipboard.getContents(FileTransfer.getInstance());
        if (files == null) {
            return -1L;
        }

        long total = 0L;
        if (!insert) {
            for (final String file2 : files) {
                final File file = new File(file2);
                total += file.length();
                if (total > content.length() - start) {
                    return 0L; // would overflow
                }
            }
        }
        total = 0L;
        for (int i = files.length - 1; i >= 0; --i) { // for some reason they
            // are given in reverse
            // order
            File file = new File(files[i]);
            try {
                file = file.getCanonicalFile();
            } catch (final IOException e) {
            } // use non-canonical one then
            boolean success = true;
            try {
                if (insert) {
                    content.insert(file, start);
                } else {
                    content.overwrite(file, start);
                }
            } catch (final IOException e) {
                success = false;
            }
            if (success) {
                start += file.length();
                total += file.length();
            }
        }

        return total;
    }

    private long tryGettingMemoryByteArray(final BinaryContent content, final long start, final boolean insert) {
        byte[] byteArray = (byte[]) myClipboard.getContents(MemoryByteArrayTransfer.getInstance());
        if (byteArray == null) {
            final String text = (String) myClipboard.getContents(TextTransfer.getInstance());
            if (text != null) {
                byteArray = text.getBytes();
            }
        }
        if (byteArray == null) {
            return -1L;
        }

        long total = byteArray.length;
        final ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        if (insert) {
            content.insert(buffer, start);
        } else if (total <= content.length() - start) {
            content.overwrite(buffer, start);
        } else {
            total = 0L;
        }

        return total;
    }

    private boolean updateLock(final File lock, int references) throws IOException {
        final RandomAccessFile file = RandomAccessFileFactory.createRandomAccessFile(lock, "rw");
        if (file.length() >= 4) {
            references += file.readInt();
        }
        if (references > 0) {
            file.seek(0);
            file.writeInt(references);
        }
        file.close();
        if (references < 1) {
            lock.delete();

            return true;
        }

        return false;
    }
}
