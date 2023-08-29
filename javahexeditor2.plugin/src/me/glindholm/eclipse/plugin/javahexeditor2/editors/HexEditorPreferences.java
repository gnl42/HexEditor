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

package me.glindholm.eclipse.plugin.javahexeditor2.editors;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.graphics.FontData;

import me.glindholm.eclipse.plugin.javahexeditor2.HexEditorPlugin;
import me.glindholm.eclipse.plugin.javahexeditor2.Preferences;

public final class HexEditorPreferences {

    /**
     * Gets font data information common to all plugin editors. Data comes from the preferences store.
     *
     * @return Font data to be used by plugin editors, not <code>null</code>.
     */
    public static FontData getFontData() {
        final IPreferenceStore store = HexEditorPlugin.getDefault().getPreferenceStore();
        final String name = store.getString(Preferences.FONT_NAME);
        final int style = store.getInt(Preferences.FONT_STYLE);
        final int size = store.getInt(Preferences.FONT_SIZE);
        FontData fontData = null;
        if (name != null && !name.isEmpty() && size > 0) {
            fontData = new FontData(name, size, style);
        } else {
            fontData = Preferences.getDefaultFontData();
        }

        return fontData;
    }
}
