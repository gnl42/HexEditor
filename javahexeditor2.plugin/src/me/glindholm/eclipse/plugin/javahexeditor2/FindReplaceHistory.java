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

import java.util.ArrayList;
import java.util.List;

/**
 * Find/Replace input history.
 *
 * @author Peter Dell
 */
public final class FindReplaceHistory {

    /**
     * An entry has a string value and a flag indicating whether the text is to be interpreted as text
     * or as hex value(s).
     */
    public static final class Entry {
        private final String stringValue;
        private final boolean hex;

        public Entry(final String stringValue, final boolean selected) {
            if (stringValue == null) {
                throw new IllegalArgumentException("Parameter 'stringValue' must not be null.");
            }

            this.stringValue = stringValue;
            hex = selected;
        }

        public String getStringValue() {
            return stringValue;
        }

        public boolean isHex() {
            return hex;
        }
    }

    private final List<Entry> findList;
    private final List<Entry> replaceList;

    public FindReplaceHistory() {
        findList = new ArrayList<>();
        replaceList = new ArrayList<>();
    }

    /**
     * Gets the list of previous find operations
     *
     * @return The modifiable list of previous find operations, may be empty, not <code>null</code>.
     */
    public List<Entry> getFindList() {

        return findList;
    }

    /**
     * Gets the list of previous replace operations
     *
     * @return The modifiable list of previous replace operations, may be empty, not <code>null</code>.
     */
    public List<Entry> getReplaceList() {

        return replaceList;
    }

}
