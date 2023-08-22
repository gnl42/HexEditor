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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Utility class to handle SWT widgets.
 *
 * @author Peter Dell
 */
public final class SWTUtility {

    /**
     * Blocks the caller until the task is finished. Does not block the user interface thread.
     *
     * @param task independent of the user interface thread (no widgets used)
     */
    public static void blockUntilFinished(final Runnable task) {
        final Thread thread = new Thread(task);
        thread.start();
        final Display display = Display.getCurrent();
        final boolean[] pollerEnabled = { false };
        while (thread.isAlive() && !display.isDisposed()) {
            if (!display.readAndDispatch()) {
                // awake periodically so it returns when task has finished
                if (!pollerEnabled[0]) {
                    pollerEnabled[0] = true;
                    display.timerExec(300, () -> pollerEnabled[0] = false);
                }
                display.sleep();
            }
        }
    }

    /**
     * Helper method to make a center a shell or dialog in the center of another shell.
     *
     * * @param movingShell shell to be relocated, not <code>null</code>
     * 
     * @param fixedShell shell to be used as reference, not <code>null</code>
     * 
     */
    public static void placeInCenterOf(final Shell movingShell, final Shell fixedShell) {
        if (movingShell == null) {
            throw new IllegalArgumentException("Parameter 'movingShell' must not be null.");
        }
        if (fixedShell == null) {
            throw new IllegalArgumentException("Parameter 'fixedShell' must not be null.");
        }

        movingShell.pack();

        final Rectangle fixedShellSize = fixedShell.getBounds();
        final Rectangle dialogSize = movingShell.getBounds();

        int locationX, locationY;
        locationX = (fixedShellSize.width - dialogSize.width) / 2 + fixedShellSize.x;
        locationY = (fixedShellSize.height - dialogSize.height) / 2 + fixedShellSize.y;

        movingShell.setLocation(new Point(locationX, locationY));
    }

    public static int showMessage(final Shell shell, final int style, final String title, final String message, final String... parameters) {
        final MessageBox messageBox = new MessageBox(shell, style);
        messageBox.setText(title);
        messageBox.setMessage(TextUtility.format(message, parameters));
        return messageBox.open();
    }

    public static int showErrorMessage(final Shell shell, final String title, final String message, final String... parameters) {
        return showMessage(shell, SWT.ERROR | SWT.OK, title, message, parameters);

    }

    /**
     * Compatibility between old and new SWT versions.
     * 
     * @param gc The graphics context, not <code>null</code>
     * @return The average character width, a positive integer.
     */
    public static double getAverageCharacterWidth(final GC gc) {
        final String GET_AVERAGE_CHARACTER_WIDTH = "getAverageCharacterWidth";
        final String GET_AVERAGE_CHAR_WIDTH = "getAverageCharWidth";

        if (gc == null) {
            throw new IllegalArgumentException();
        }
        final FontMetrics fm = gc.getFontMetrics();
        Method method = getMethod(FontMetrics.class, GET_AVERAGE_CHARACTER_WIDTH);
        if (method != null) {

            Double result = null;
            try {
                result = (Double) method.invoke(fm);
            } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return result;
        }

        method = getMethod(FontMetrics.class, GET_AVERAGE_CHAR_WIDTH);
        if (method != null) {
            Integer result = null;
            try {
                result = (Integer) method.invoke(fm);
            } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return result.doubleValue();
        }
        throw new RuntimeException("None of the required methods '" + GET_AVERAGE_CHARACTER_WIDTH + "' or '" + GET_AVERAGE_CHAR_WIDTH + "' found");
    }

    /**
     * Compatibility between old and new SWT versions.
     * 
     * @param styledText The styled text, not <code>null</code>
     * @param point      The point, not <code>null</code>
     * @return The offset at location point.
     */
    public static int getOffsetAtPoint(final StyledText styledText, final Point point) {
        final String GET_OFFSET_AT_POINT = "getOffsetAtPoint";
        final String GET_OFFSET_AT_LOCATION = "getOffsetAtLocation";

        if (styledText == null) {
            throw new IllegalArgumentException();
        }
        Method method = getMethod(StyledText.class, GET_OFFSET_AT_POINT, Point.class);
        if (method == null) {
            method = getMethod(StyledText.class, GET_OFFSET_AT_LOCATION, Point.class);
        }
        if (method == null) {
            throw new RuntimeException("None of the required methods '" + GET_OFFSET_AT_POINT + "' or '" + GET_OFFSET_AT_LOCATION + "' found");
        }
        Integer result = null;
        try {
            result = (Integer) method.invoke(styledText, point);
        } catch (final IllegalAccessException | IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch (final InvocationTargetException ex) {
            if (ex.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex.getCause();
            }
            throw new RuntimeException(ex);
        }
        return result;
    }

    private static Method getMethod(final Class<?> clazz, final String methodName, final Class<?>... parameterTypes) {
        Method method = null;
        try {
            method = clazz.getMethod(methodName, parameterTypes);
        } catch (final NoSuchMethodException ex1) {
            method = null;
        }
        return method;
    }
}
