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

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.service.prefs.BackingStoreException;

import me.glindholm.eclipse.plugin.javahexeditor2.HexEditorPlugin;
import me.glindholm.eclipse.plugin.javahexeditor2.Preferences;
import me.glindholm.eclipse.plugin.javahexeditor2.PreferencesManager;

/**
 * This class represents a preference page that is contributed to the Preferences dialog.
 * <p>
 * This page is used to modify preferences only. They are stored in the preference store that
 * belongs to the main plug-in class. That way, preferences can be accessed directly via the
 * preference store.
 */
public final class HexEditorPreferencesPage extends PreferencePage implements IWorkbenchPreferencePage {

    private PreferencesManager preferences;

    @Override
    protected Control createContents(final Composite parent) {
        final FontData fontData = HexEditorPreferences.getFontData();
        preferences = new PreferencesManager(fontData);

        return preferences.createPreferencesPart(parent);
    }

    @Override
    public void init(final IWorkbench workbench) {
    }

    @Override
    protected void performDefaults() {
        super.performDefaults();
        preferences.setFontData(null);
    }

    @Override
    public boolean performOk() {
        final IPreferenceStore store = HexEditorPlugin.getDefault().getPreferenceStore();
        final FontData fontData = preferences.getFontData();
        store.setValue(Preferences.FONT_NAME, fontData.getName());
        store.setValue(Preferences.FONT_STYLE, fontData.getStyle());
        store.setValue(Preferences.FONT_SIZE, fontData.getHeight());
        store.firePropertyChangeEvent(Preferences.FONT_DATA, null, fontData);

        try {
            InstanceScope.INSTANCE.getNode(HexEditorPlugin.ID).flush();
        } catch (final BackingStoreException ex) {

            throw new RuntimeException("Cannot store preferences for plugin '" + HexEditorPlugin.ID + "'", ex);
        }

        return true;
    }
}
