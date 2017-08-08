/*
 * javahexeditor, a java hex editor
 * Copyright (C) 2006, 2009 Jordi Bergenthal, pestatije(-at_)users.sourceforge.net
 * The official javahexeditor site is sourceforge.net/projects/javahexeditor
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
package net.sourceforge.javahexeditor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import net.sourceforge.javahexeditor.BinaryContent.RangeSelection;
import net.sourceforge.javahexeditor.common.NumberUtility;
import net.sourceforge.javahexeditor.common.TextUtility;

/**
 * Status line component of the editor. Displays the current position and the
 * insert/overwrite status.
 */
final class StatusLine extends Composite {

	private Label positionLabel;
	private Label valueLabel;
	private Label insertModeLabel;

	/**
	 * Create a status line part
	 *
	 * @param parent
	 *            parent in the widget hierarchy
	 * @param style
	 *            not used
	 * @param withLeftSeparator
	 *            so it can be put besides other status items (for plugin)
	 */
	public StatusLine(Composite parent, int style, boolean withLeftSeparator) {
		super(parent, style);
		initialize(withLeftSeparator);
	}

	private void initialize(boolean withSeparator) {

		// From Eclipse 3.1's GridData javadoc:
		// NOTE: Do not reuse GridData objects.
		// Every control in a Composite that is
		// managed by a GridLayout must have a unique GridData instance
		GridLayout statusLayout = new GridLayout();
		statusLayout.numColumns = withSeparator ? 6 : 5;
		statusLayout.marginHeight = 0;
		setLayout(statusLayout);

		if (withSeparator) {
			Label separator1 = new Label(this, SWT.SEPARATOR);
			separator1.setLayoutData(createGridData());
		}
	
		long MAX_FILE_SIZE = 1024 * 1024 * 1024; // Use a reasonable value to not waste space
		positionLabel = new Label(this, SWT.SHADOW_NONE);
		int maxLength = Math.max(getPositionText(Long.MAX_VALUE).length(),
				getSelectionText(new RangeSelection(MAX_FILE_SIZE - 1, MAX_FILE_SIZE)).length());
		positionLabel.setLayoutData(createGridData(maxLength));

		Label separator2 = new Label(this, SWT.SEPARATOR);
		separator2.setLayoutData(createGridData());

		valueLabel = new Label(this, SWT.SHADOW_NONE);
		maxLength = getValueText(Byte.MAX_VALUE).length();
		GridData gridData2 = createGridData(maxLength);
		valueLabel.setLayoutData(gridData2);

		Label separator3 = new Label(this, SWT.SEPARATOR);
		separator3.setLayoutData(createGridData());

		insertModeLabel = new Label(this, SWT.SHADOW_NONE);
		maxLength = Math.max(Texts.STATUS_LINE_MODE_INSERT.length(), Texts.STATUS_LINE_MODE_OVERWRITE.length());
		insertModeLabel.setLayoutData(createGridData(maxLength));
	}

	private GridData createGridData() {
		GridData gridData = new GridData();
		gridData.grabExcessVerticalSpace = true;
		gridData.widthHint = 1;
		return gridData;
	}

	private GridData createGridData(int maxLength) {
		GC gc = new GC(this);
		FontMetrics fontMetrics = gc.getFontMetrics();

		GridData gridData = new GridData((maxLength) * fontMetrics.getAverageCharWidth(), SWT.DEFAULT);
		gridData.grabExcessVerticalSpace = true;
		gc.dispose();

		return gridData;
	}

	/**
	 * Update the insert/overwrite mode.
	 *
	 * @param insert
	 *            <code>true</code> for insert mode, or <code>false</code> for
	 *            overwrite
	 */
	public void updateInsertMode(boolean insert) {
		if (isDisposed() || insertModeLabel.isDisposed()) {
			return;
		}

		insertModeLabel.setText(insert ? Texts.STATUS_LINE_MODE_INSERT : Texts.STATUS_LINE_MODE_OVERWRITE);
	}

	/**
	 * Clear the position status.
	 */
	public void clearPosition() {
		positionLabel.setText(Texts.EMPTY);
	}

	/**
	 * Update the position status. Displays its decimal and hex value.
	 *
	 * @param position
	 *            position to display
	 */
	public void updatePosition(long position) {
		if (position < 0) {
			throw new IllegalArgumentException("Parameter 'position' must not be negative.");
		}
		if (isDisposed() || positionLabel.isDisposed()) {
			return;
		}
		positionLabel.setText(getPositionText(position));
	}

	private String getPositionText(long position) {
		String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_POSITION,
				NumberUtility.getDecimalAndHexString(position));
		return text;
	}

	/**
	 * Update the selection status. Displays its decimal and hex values for start
	 * and end selection
	 *
	 * @param rangeSelection
	 *            selection array to display: [0] = start, [1] = end
	 */
	public void updateSelection(RangeSelection rangeSelection) {
		if (rangeSelection == null) {
			throw new IllegalArgumentException("Parameter 'rangeSelection' must not be null.");
		}

		if (isDisposed() || positionLabel.isDisposed()) {
			return;
		}

		positionLabel.setText(getSelectionText(rangeSelection));
	}

	private String getSelectionText(RangeSelection rangeSelection) {
		if (rangeSelection == null) {
			throw new IllegalArgumentException("Parameter 'rangeSelection' must not be null.");
		}
		String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_SELECTION,
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
	 * @param value
	 *            value to display
	 */
	public void updateValue(byte value) {
		if (isDisposed() || valueLabel.isDisposed()) {
			return;
		}
		valueLabel.setText(getValueText(value));
	}

	private String getValueText(byte value) {
		int unsignedValue = value & 0xff;
		String binaryText = "0000000" + Integer.toBinaryString(unsignedValue);
		binaryText = binaryText.substring(binaryText.length() - 8);

		String text = TextUtility.format(Texts.STATUS_LINE_MESSAGE_VALUE, NumberUtility.getDecimalString(unsignedValue),
				NumberUtility.getHexString(unsignedValue), binaryText);
		return text;
	}

}
