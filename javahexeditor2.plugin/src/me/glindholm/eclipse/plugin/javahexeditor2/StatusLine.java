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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent.RangeSelection;
import me.glindholm.eclipse.plugin.javahexeditor2.common.NumberUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.SWTUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.TextUtility;

/**
 * Status line component of the editor. Displays the current position, value at position, the
 * insert/overwrite status and the file size.
 */
final class StatusLine extends Composite {

    private Label positionLabel;
    private Label valueLabel;
    private Label insertModeLabel;
    private Label sizeLabel;

    /**
     * Create a status line part
     *
     * @param parent            parent in the widget hierarchy
     * @param style             not used
     * @param withLeftSeparator so it can be put besides other status items (for plugin)
     */
    public StatusLine(final Composite parent, final int style, final boolean withLeftSeparator) {
        super(parent, style);
        initialize(withLeftSeparator);
    }

    private void initialize(final boolean withSeparator) {

        // From Eclipse 3.1's GridData javadoc:
        // NOTE: Do not reuse GridData objects.
        // Every control in a Composite that is
        // managed by a GridLayout must have a unique GridData instance
        final GridLayout statusLayout = new GridLayout();
        statusLayout.numColumns = withSeparator ? 8 : 7;
        statusLayout.marginHeight = 0;
        setLayout(statusLayout);

        if (withSeparator) {
            final Label separator1 = new Label(this, SWT.SEPARATOR);
            separator1.setLayoutData(createGridData());
        }

        final long MAX_FILE_SIZE = 0; // Use a reasonable value to not waste space
        positionLabel = new Label(this, SWT.SHADOW_NONE);
        positionLabel.setLayoutData(createGridData());
        updatePositionWidth(MAX_FILE_SIZE);

        final Label separator2 = new Label(this, SWT.SEPARATOR);
        separator2.setLayoutData(createGridData());

        valueLabel = new Label(this, SWT.SHADOW_NONE);
        int maxLength = getValueText(Byte.MAX_VALUE).length();
        valueLabel.setLayoutData(createGridData(maxLength));

        final Label separator3 = new Label(this, SWT.SEPARATOR);
        separator3.setLayoutData(createGridData());

        insertModeLabel = new Label(this, SWT.SHADOW_NONE);
        maxLength = Math.max(Texts.STATUS_LINE_MODE_INSERT.length(), Texts.STATUS_LINE_MODE_OVERWRITE.length());
        insertModeLabel.setLayoutData(createGridData(maxLength));

        final Label separator4 = new Label(this, SWT.SEPARATOR);
        separator4.setLayoutData(createGridData());

        sizeLabel = new Label(this, SWT.SHADOW_NONE);
        sizeLabel.setLayoutData(createGridData());
        updateSizeWidth(MAX_FILE_SIZE);

    }

    private int getWidthHint(final int maxLength) {
        final GC gc = new GC(this);
        final int widthHint = (int) (maxLength * SWTUtility.getAverageCharacterWidth(gc));
        gc.dispose();

        return widthHint;
    }

    private GridData createGridData() {
        final GridData gridData = new GridData();
        gridData.grabExcessVerticalSpace = true;
        gridData.widthHint = 1;
        return gridData;
    }

    private GridData createGridData(final int maxLength) {
        final int width = getWidthHint(maxLength);
        final GridData gridData = new GridData(width, SWT.DEFAULT);
        gridData.grabExcessVerticalSpace = true;

        return gridData;
    }

    /**
     * Update the insert/overwrite mode.
     *
     * @param insert <code>true</code> for insert mode, or <code>false</code> for overwrite
     */
    public void updateInsertMode(final boolean insert) {
        if (isDisposed() || insertModeLabel.isDisposed()) {
            return;
        }

        insertModeLabel.setText(insert ? Texts.STATUS_LINE_MODE_INSERT : Texts.STATUS_LINE_MODE_OVERWRITE);
    }

    /**
     * Clear the position status.
     */
    public void clearPosition() {
        if (isDisposed() || positionLabel.isDisposed()) {
            return;
        }
        positionLabel.setText(Texts.EMPTY);
    }

    public void updatePositionWidth(final long size) {
        if (isDisposed() || positionLabel.isDisposed()) {
            return;
        }
        final long sizeMinusOne = size > 1 ? size - 1 : size;
        final int maxLength = Math.max(getPositionText(size).length(), getSelectionText(new RangeSelection(sizeMinusOne, size)).length()) + 2;
        ((GridData) positionLabel.getLayoutData()).widthHint = getWidthHint(maxLength);
    }

    /**
     * Update the position status. Displays its decimal and hex value.
     *
     * @param position position to display
     */
    public void updatePosition(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Parameter 'position' must not be negative.");
        }
        if (isDisposed() || positionLabel.isDisposed()) {
            return;
        }
        positionLabel.setText(getPositionText(position));
    }

    private String getPositionText(final long position) {
        final String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_POSITION, NumberUtility.getDecimalAndHexString(position));
        return text;
    }

    /**
     * Update the selection status. Displays its decimal and hex values for start and end selection
     *
     * @param rangeSelection selection array to display: [0] = start, [1] = end
     */
    public void updateSelection(final RangeSelection rangeSelection) {
        if (rangeSelection == null) {
            throw new IllegalArgumentException("Parameter 'rangeSelection' must not be null.");
        }

        if (isDisposed() || positionLabel.isDisposed()) {
            return;
        }

        positionLabel.setText(getSelectionText(rangeSelection));
    }

    private String getSelectionText(final RangeSelection rangeSelection) {
        if (rangeSelection == null) {
            throw new IllegalArgumentException("Parameter 'rangeSelection' must not be null.");
        }
        final String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_SELECTION,
                NumberUtility.getDecimalAndHexRangeString(rangeSelection.start, rangeSelection.end));
        return text;
    }

    /**
     * Clear the value status.
     */
    public void clearValue() {
        if (isDisposed() || valueLabel.isDisposed()) {
            return;
        }
        valueLabel.setText(Texts.EMPTY);
    }

    /**
     * Update the value status. Displays its decimal, hex and binary value
     *
     * @param value value to display
     */
    public void updateValue(final byte value) {
        if (isDisposed() || valueLabel.isDisposed()) {
            return;
        }
        valueLabel.setText(getValueText(value));
    }

    private String getValueText(final byte value) {
        final int unsignedValue = value & 0xff;
        String binaryText = "0000000" + Integer.toBinaryString(unsignedValue);
        binaryText = binaryText.substring(binaryText.length() - 8);

        final String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_VALUE, NumberUtility.getDecimalString(unsignedValue),
                NumberUtility.getHexString(unsignedValue), binaryText);
        return text;
    }

    public void updateSizeWidth(final long size) {
        if (isDisposed() || sizeLabel.isDisposed()) {
            return;
        }
        final int maxLength = getSizeText(size).length() + 1;
        ((GridData) sizeLabel.getLayoutData()).widthHint = getWidthHint(maxLength);
    }

    /**
     * Clear the size status.
     */
    public void clearSize() {
        if (isDisposed() || valueLabel.isDisposed()) {
            return;
        }
        sizeLabel.setText(Texts.EMPTY);
    }

    /**
     * Update the size status. Displays its decimal and hex value.
     *
     * @param size size to display
     */
    public void updateSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Parameter 'size' must not be negative.");
        }
        if (isDisposed() || sizeLabel.isDisposed()) {
            return;
        }
        sizeLabel.setText(getSizeText(size));
    }

    private String getSizeText(final long size) {
        final String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_SIZE, NumberUtility.getDecimalAndHexString(size));
        return text;
    }

}
