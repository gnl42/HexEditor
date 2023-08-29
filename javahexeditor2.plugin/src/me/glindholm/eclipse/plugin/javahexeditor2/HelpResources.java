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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public final class HelpResources {

    /**
     * Gets the URI to open for displaying the help document.
     *
     * @param online <code>true</code> to get the URI for the project web site, <code>false</code> to
     *               get an URI in the local file system. All relevant resources will be copied to the
     *               file system in this case.
     * @return The URI, not <code>null</code>.
     */
    public static URI getHelpResourceURI(final boolean online) {
        URI uri;
        if (online) {
            try {
                uri = new URI("https://javahexeditor.sourceforge.io");
            } catch (final URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            {

                uri = writeResource("/index.html", null).toURI();
                writeResource("/images/javahexeditor-48x48.png", null);
                writeResource("/images/linux-files-preferences.png", null);
                writeResource("/images/screenshot-01.png", null);
                writeResource("/images/screenshot-02.png", null);
            }
        }
        return uri;

    }

    /**
     * Copies a file from the class path to the temporary folder.
     *
     * @param resourcePath The resource path in the class path, not empty, not <code>null</code>.
     * @param filePath     The file path or <code>null</code> if the resource path shall be used.
     * @return
     */
    private static File writeResource(final String resourcePath, String filePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("Parameter 'resourcePath' must not be null.");
        }
        if (filePath == null) {
            filePath = resourcePath;
        }
        final InputStream inStream = HelpResources.class.getResourceAsStream(resourcePath);
        if (inStream == null) {
            throw new RuntimeException("Help file '" + resourcePath + "' missing in classpath.");
        }
        final File localFolder = new File(System.getProperty("java.io.tmpdir"), "javahexeditor");
        final File localFile = new File(localFolder, filePath);
        localFile.getParentFile().mkdirs();
        try {
            final FileOutputStream outStream = new FileOutputStream(localFile);
            final byte[] buffer = new byte[512];
            int read = 0;
            try {
                while ((read = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, read);
                }
            } finally {
                outStream.close();
            }
        } catch (final IOException ignore) {
            // Open browser anyway
        }
        try {
            inStream.close();
        } catch (final IOException ignore) {
            // Open browser anyway
        }
        localFile.deleteOnExit();
        return localFile;
    }
}
