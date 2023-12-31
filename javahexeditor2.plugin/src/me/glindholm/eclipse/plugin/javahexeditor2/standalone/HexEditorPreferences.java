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
package me.glindholm.eclipse.plugin.javahexeditor2.standalone;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.swt.graphics.FontData;

import me.glindholm.eclipse.plugin.javahexeditor2.Preferences;
import me.glindholm.eclipse.plugin.javahexeditor2.PreferencesManager;
import me.glindholm.eclipse.plugin.javahexeditor2.common.SWTUtility;

final class HexEditorPreferences {
    public final String PROPERTIES_FILE = "javahexeditor.properties";

    private final HexEditor hexEditor;
    private FontData fontData;

    public HexEditorPreferences(final HexEditor hexEditor) {
        if (hexEditor == null) {
            throw new IllegalArgumentException("Parameter 'hexEditor' must not be null.");
        }
        this.hexEditor = hexEditor;
        fontData = Preferences.getDefaultFontData();
    }

    /**
     * Gets the currently active font data.
     *
     * @return The currently active font data, not <code>null</code>.
     */
    public FontData getFontData() {
        return fontData;
    }

    /**
     * Sets the currently active font data.
     *
     * @param fontData The currently active font data, not <code>null</code>.
     */
    public void setFontData(final FontData fontData) {
        if (fontData == null) {
            throw new IllegalArgumentException("Parameter 'fontData' must not be null.");
        }
        this.fontData = fontData;

    }

    public void load() {

        final Properties properties = new Properties();
        try {
            final FileInputStream file = new FileInputStream(PROPERTIES_FILE);
            properties.load(file);
            file.close();
        } catch (final IOException e) {
            return;
        }

        final String name = properties.getProperty(Preferences.FONT_NAME);
        if (name == null) {
            return;
        }

        final String styleString = properties.getProperty(Preferences.FONT_STYLE);
        if (styleString == null) {
            return;
        }
        final int style = PreferencesManager.fontStyleToInt(styleString);

        int size = 0;
        try {
            size = Integer.parseInt(properties.getProperty(Preferences.FONT_SIZE));
        } catch (final NumberFormatException e) {
            return;
        }

        fontData = new FontData(name, size, style);
    }

    public void store() {
        final File propertiesFile = new File(PROPERTIES_FILE);
        if (fontData == null) {
            if (propertiesFile.exists()) {
                propertiesFile.delete();
            }
            return;
        }

        final Properties properties = new Properties();
        properties.setProperty(Preferences.FONT_NAME, fontData.getName());
        properties.setProperty(Preferences.FONT_STYLE, PreferencesManager.fontStyleToString(fontData.getStyle()));
        properties.setProperty(Preferences.FONT_SIZE, Integer.toString(fontData.getHeight()));
        try {
            final FileOutputStream stream = new FileOutputStream(propertiesFile);
            properties.store(stream, null);
            stream.close();
        } catch (final IOException ex) {
            SWTUtility.showErrorMessage(hexEditor.shell, Texts.PREFERENCES_MESSAGE_CANNOT_NOT_WRITE_FILE, propertiesFile.getAbsolutePath(), ex.getMessage());

        }
    }
}
