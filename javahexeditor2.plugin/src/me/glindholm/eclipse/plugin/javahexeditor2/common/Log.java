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
package me.glindholm.eclipse.plugin.javahexeditor2.common;

/**
 * Utility class to issue log messages.
 *
 * @author Peter Dell
 */
public final class Log {

    public interface Delegate {

        boolean isTraceActive();

        void log(String message, Throwable th);
    }

    private static final class DefaultDelegate implements Delegate {

        @Override
        public boolean isTraceActive() {
            return false;
        }

        @Override
        public void log(final String message, final Throwable th) {
            System.err.println(message);
            if (th != null) {
                th.printStackTrace(System.err);
            }

        }

    }

    private static Delegate delegate = new DefaultDelegate();

    /**
     * Creation is private.
     */
    private Log() {
    }

    public static void setDelegate(final Delegate delegate) {
        Log.delegate = delegate;
    }

    public static void logError(final String message, final Object[] parameters, final Throwable th) {
        if (message == null) {
            throw new IllegalArgumentException("Parameter 'message' must not be null.");
        }
        if (delegate != null) {
            final String m = createMessage("ERROR: ", message, parameters);
            delegate.log(m, th);
        }
    }

    public static void trace(final Object owner, final String message, final Object... parameters) {
        if (delegate != null && delegate.isTraceActive()) {
            final String m = createMessage(owner, message, parameters);
            delegate.log(m, null);
        }
    }

    private static String createMessage(final Object owner, String message, final Object... parameters) {
        if (message == null) {
            message = "";
        }
        String[] stringParameters = null;
        if (parameters != null) {
            stringParameters = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                stringParameters[i] = String.valueOf(parameters[i]);
            }
        }
        return owner + ":" + TextUtility.format(message, stringParameters);
    }

}
