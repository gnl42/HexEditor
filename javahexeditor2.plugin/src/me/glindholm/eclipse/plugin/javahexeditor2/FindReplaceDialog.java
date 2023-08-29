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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContentFinder.Match;
import me.glindholm.eclipse.plugin.javahexeditor2.common.ByteArrayUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.NumberUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.SWTUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.TextUtility;

/**
 * Find/Replace dialog with hex/text, forward/backward, and ignore case options. Remembers previous
 * state, in case it has been closed by the user and reopened again.
 *
 * @author Jordi Bergenthal
 *
 */
final class FindReplaceDialog extends Dialog {

    SelectionAdapter defaultSelectionAdapter = new SelectionAdapter() {
        @Override
        public void widgetSelected(final SelectionEvent e) {
            if (lastIgnoreCase != ignoreCaseCheckBox.getSelection() || lastForward != forwardRadioButton.getSelection()
                    || lastFindHexButtonSelected != findGroup.hexRadioButton.getSelection()
                    || lastReplaceHexButtonSelected != replaceGroup.hexRadioButton.getSelection()) {
                sendInfoMessage(Texts.EMPTY);
            }
            lastFocused.textCombo.setFocus();
        }
    };

    private List<FindReplaceHistory.Entry> findList;
    private List<FindReplaceHistory.Entry> replaceList;

    HexTexts myTarget;
    TextHexInputGroup lastFocused;

    boolean lastForward = true;
    boolean lastFindHexButtonSelected = true;
    boolean lastReplaceHexButtonSelected = true;
    boolean lastIgnoreCase = false;
    boolean searching = false;

    // Visual components
    Shell shell;
    TextHexInputGroup findGroup;
    TextHexInputGroup replaceGroup;
    private Group directionGroup;
    Button forwardRadioButton;
    Button backwardRadioButton;
    private Group optionsGroup;
    Button ignoreCaseCheckBox;

    private Composite feedbackComposite;
    Label feedbackLabel;
    Composite progressComposite;
    ProgressBar progressBar;
    private Button progressBarStopButton;

    private Button findButton;
    private Button replaceButton;
    private Button replaceAllButton;
    private Button closeButton;

    /**
     * Group with text/hex selector and text input
     */
    private final class TextHexInputGroup {
        List<FindReplaceHistory.Entry> items;

        // visual components
        Group group;
        private Composite composite;
        Button hexRadioButton;
        Button textRadioButton;
        Combo textCombo;

        public TextHexInputGroup(final List<FindReplaceHistory.Entry> oldItems) {
            if (oldItems == null) {
                throw new IllegalArgumentException("Parameter 'oldItems' must not be null.");
            }
            items = oldItems;
        }

        public void initialise() {
            group = new Group(shell, SWT.NONE);
            final GridLayout gridLayout = new GridLayout();
            gridLayout.numColumns = 2;
            group.setLayout(gridLayout);
            group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            createRadioButtonComposite();

            textCombo = new Combo(group, SWT.BORDER);

            // Calculate the size of the input field and set it as width hint.
            final int columns = 35;
            final GC gc = new GC(textCombo);
            final int width = (int) (columns * SWTUtility.getAverageCharacterWidth(gc));
            gc.dispose();

            final GridData gridData_textCombo = new GridData();
            gridData_textCombo.widthHint = width;
            textCombo.setLayoutData(gridData_textCombo);
            textCombo.addVerifyListener(e -> {
                if (e.keyCode == 0) {
                    return; // a list selection
                }
            });
            textCombo.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    final int index = textCombo.getSelectionIndex();
                    if (index < 0) {
                        return;
                    }
                    refreshHexOrText(items.get(index).isHex());
                }
            });
            textCombo.addModifyListener(e -> {
                sendInfoMessage(Texts.EMPTY);
                if (TextHexInputGroup.this == findGroup) {
                    dataToUI();
                }
            });
        }

        /**
         * This method initializes composite
         */
        private void createRadioButtonComposite() {
            composite = new Composite(group, SWT.NONE);
            composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
            final GridLayout gridLayout_composite = new GridLayout();
            gridLayout_composite.marginHeight = 0;
            gridLayout_composite.marginWidth = 0;
            composite.setLayout(gridLayout_composite);

            hexRadioButton = new Button(composite, SWT.RADIO);
            hexRadioButton.setText(Texts.FIND_REPLACE_DIALOG_HEX_RADIO_LABEL);
            hexRadioButton.addSelectionListener(defaultSelectionAdapter);
            hexRadioButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    if (ByteArrayUtility.parseString(textCombo.getText()) == null) {
                        textCombo.setText(Texts.EMPTY);
                    }
                }
            });
            textRadioButton = new Button(composite, SWT.RADIO);
            textRadioButton.setText(Texts.FIND_REPLACE_DIALOG_TEXT_RADIO_LABEL);
            textRadioButton.addSelectionListener(defaultSelectionAdapter);
        }

        public void refreshCombo() {
            if (items == null) {
                return;
            }

            if (textCombo.getItemCount() > 0) {
                textCombo.remove(0, textCombo.getItemCount() - 1);
            }
            for (final Iterator<FindReplaceHistory.Entry> iterator = items.iterator(); iterator.hasNext();) {
                final String itemString = iterator.next().getStringValue();
                textCombo.add(itemString);
            }
            if (!items.isEmpty()) {
                textCombo.setText(items.get(0).getStringValue());
            }
            selectText();
        }

        public void refreshHexOrText(final boolean hex) {
            hexRadioButton.setSelection(hex);
            textRadioButton.setSelection(!hex);
        }

        public void rememberText() {
            final String lastText = textCombo.getText();
            if (Texts.EMPTY.equals(lastText) || items == null) {
                return;
            }

            for (final Iterator<FindReplaceHistory.Entry> iterator = items.iterator(); iterator.hasNext();) {
                final String itemString = iterator.next().getStringValue();
                if (lastText.equals(itemString)) {
                    iterator.remove();
                }
            }
            items.add(0, new FindReplaceHistory.Entry(lastText, hexRadioButton.getSelection()));
            refreshCombo();
        }

        public void selectText() {
            textCombo.setSelection(new Point(0, textCombo.getText().length()));
        }

        public void setEnabled(final boolean enabled) {
            group.setEnabled(enabled);
            hexRadioButton.setEnabled(enabled);
            textRadioButton.setEnabled(enabled);
            textCombo.setEnabled(enabled);
        }
    }

    /**
     * Create find/replace dialog always on top of shell
     *
     * @param shell where it is displayed
     */
    public FindReplaceDialog(final Shell shell) {
        super(shell);
    }

    private void activateProgressBar() {
        // Set the progress bar to visible after 0,5 seconds.
        Display.getCurrent().timerExec(500, () -> {
            if (searching && !progressComposite.isDisposed()) {
                setProgressCompositeVisible(true);
            }
        });
        long max = myTarget.myContent.length();
        long min = myTarget.getCaretPos();
        if (backwardRadioButton.getSelection()) {
            max = min;
            min = 0L;
        }
        int factor = 0;
        while (max > Integer.MAX_VALUE) {
            max = max >>> 1;
            min = min >>> 1;
            ++factor;
        }
        progressBar.setMaximum((int) max);
        progressBar.setMinimum((int) min);
        progressBar.setSelection(0);
        final int finalFactor = factor;
        Display.getCurrent().timerExec(1000, new Runnable() {
            @Override
            public void run() {
                if (!searching || progressBar.isDisposed()) {
                    return;
                }

                int selection = 0;
                if (myTarget.myFinder != null) {
                    selection = (int) (myTarget.myFinder.getSearchPosition() >>> finalFactor);
                    if (backwardRadioButton.getSelection()) {
                        selection = progressBar.getMaximum() - selection;
                    }
                }
                progressBar.setSelection(selection);
                Display.getCurrent().timerExec(1000, this);
            }
        });
    }

    /**
     * Open and display the dialog.
     *
     * @param target             The target with data to search, not <code>null</code>.
     * @param findReplaceHistory The modifiable find-replace history, not <code>null</code>.
     **/

    public void open(final HexTexts target, final FindReplaceHistory findReplaceHistory) {
        if (target == null) {
            throw new IllegalArgumentException("Parameter 'target' must not be null.");
        }
        if (findReplaceHistory == null) {
            throw new IllegalArgumentException("Parameter 'findReplaceHistory' must not be null.");
        }

        myTarget = target;

        findList = findReplaceHistory.getFindList();
        replaceList = findReplaceHistory.getReplaceList();

        if (shell == null || shell.isDisposed()) {
            createShell();
        }
        SWTUtility.placeInCenterOf(shell, target.getShell());
        findGroup.refreshCombo();
        final long selectionLength = myTarget.getSelection().getLength();
        if (selectionLength > 0L && selectionLength <= BinaryContentFinder.MAX_SEQUENCE_SIZE) {
            findGroup.refreshHexOrText(true);
            ignoreCaseCheckBox.setEnabled(false);
            final StringBuilder selectedText = new StringBuilder();
            final byte[] selection = new byte[(int) selectionLength];
            try {
                myTarget.myContent.get(ByteBuffer.wrap(selection), myTarget.getSelection().start);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
            for (int i = 0; i < selectionLength; i++) {
                selectedText.append(HexTexts.byteToHex[selection[i] & 0x0ff]);
                if (i < selectionLength - 1) {
                    selectedText.append(' ');
                }
            }
            findGroup.textCombo.setText(selectedText.toString());
            findGroup.selectText();
        } else {
            findGroup.refreshHexOrText(lastFindHexButtonSelected);
            ignoreCaseCheckBox.setEnabled(!lastFindHexButtonSelected);
        }

        replaceGroup.refreshHexOrText(lastReplaceHexButtonSelected);
        replaceGroup.refreshCombo();

        ignoreCaseCheckBox.setSelection(lastIgnoreCase);
        if (lastForward) {
            forwardRadioButton.setSelection(true);
        } else {
            backwardRadioButton.setSelection(true);
        }

        sendInfoMessage(Texts.FIND_REPLACE_DIALOG_MESSAGE_SPECIFY_VALUE_TO_FIND);

        lastFocused = findGroup;
        lastFocused.textCombo.setFocus();
        dataToUI();
        shell.open();
    }

    /**
     * This method initializes composite3
     */
    private void createOptionsGroup() {
        optionsGroup = new Group(shell, SWT.NONE);
        optionsGroup.setLayout(new GridLayout());
        optionsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        optionsGroup.setText(Texts.FIND_REPLACE_DIALOG_OPTIONS_GROUP_LABEL);

        ignoreCaseCheckBox = new Button(optionsGroup, SWT.CHECK);
        ignoreCaseCheckBox.setText(Texts.FIND_REPLACE_DIALOG_IGNORE_CASE_CHECKBOX_LABEL);
        ignoreCaseCheckBox.addSelectionListener(defaultSelectionAdapter);
    }

    /**
     * This method initializes group1
     */
    private void createDirectionGroup() {
        directionGroup = new Group(shell, SWT.NONE);
        directionGroup.setText(Texts.FIND_REPLACE_DIALOG_DIRECTION_GROUP_LABEL);
        directionGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        directionGroup.setLayout(new GridLayout());

        forwardRadioButton = new Button(directionGroup, SWT.RADIO);
        forwardRadioButton.setText(Texts.FIND_REPLACE_DIALOG_DIRECTION_FORWARD_RADIO_LABEL);
        forwardRadioButton.addSelectionListener(defaultSelectionAdapter);

        backwardRadioButton = new Button(directionGroup, SWT.RADIO);
        backwardRadioButton.setText(Texts.FIND_REPLACE_DIALOG_DIRECTION_BACKWARD_RADIO_LABEL);
        backwardRadioButton.addSelectionListener(defaultSelectionAdapter);
    }

    private void createFeedbackComposite() {
        feedbackComposite = new Composite(shell, SWT.NONE);
        final GridLayout gridLayout_feedbackComposite = new GridLayout();
        gridLayout_feedbackComposite.verticalSpacing = 0;
        gridLayout_feedbackComposite.horizontalSpacing = 0;
        feedbackComposite.setLayout(gridLayout_feedbackComposite);
        feedbackComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        feedbackLabel = new Label(feedbackComposite, SWT.NONE);
        feedbackLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    }

    private void sendInfoMessage(final String message) {
        feedbackLabel.setForeground(null);
        feedbackLabel.setText(message);
    }

    private void sendErrorMessage(final String message) {
        final Color color_red = new Color(Display.getCurrent(), 255, 0, 0);
        feedbackLabel.setForeground(color_red);
        feedbackLabel.setText(message);
    }

    private void createProgressComposite() {
        progressComposite = new Composite(shell, SWT.NONE);
        progressComposite.setLayout(new GridLayout(2, false));
        progressComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        progressBar = new ProgressBar(progressComposite, SWT.NONE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        progressBarStopButton = new Button(progressComposite, SWT.NONE);
        progressBarStopButton.setText(Texts.FIND_REPLACE_DIALOG_STOP_SEARCHING_BUTTON_LABEL);

        setProgressCompositeVisible(false);
        progressBarStopButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                myTarget.stopSearching();
            }
        });

    }

    private void setProgressCompositeVisible(final boolean visible) {
        progressComposite.setVisible(visible);
        ((GridData) progressComposite.getLayoutData()).exclude = !visible;
        progressComposite.getParent().pack();
    }

    private void createButtonBarComposite() {

        final Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(5, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        // This Label does not show anything.
        // It is just used to push the buttons to the right.
        final Label spacerLabel = new Label(buttonBar, SWT.NONE);
        spacerLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        findButton = new Button(buttonBar, SWT.NONE);
        findButton.setText(Texts.FIND_REPLACE_DIALOG_FIND_BUTTON_LABEL);
        findButton.addSelectionListener(defaultSelectionAdapter);
        findButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                doFind();
            }
        });

        replaceButton = new Button(buttonBar, SWT.NONE);
        replaceButton.setText(Texts.FIND_REPLACE_DIALOG_REPLACE_BUTTON_LABEL);
        replaceButton.addSelectionListener(defaultSelectionAdapter);
        replaceButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                doReplace();
            }
        });
        replaceAllButton = new Button(buttonBar, SWT.NONE);
        replaceAllButton.setText(Texts.FIND_REPLACE_DIALOG_REPLACE_ALL_BUTTON_LABEL);
        replaceAllButton.addSelectionListener(defaultSelectionAdapter);
        replaceAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                doReplaceAll();
            }
        });

        closeButton = new Button(buttonBar, SWT.NONE);
        closeButton.setText(Texts.BUTTON_CLOSE_LABEL);
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                shell.close();
            }
        });
    }

    /**
     * This method initializes the shell
     */
    private void createShell() {
        shell = new Shell(getParent(), SWT.MODELESS | SWT.DIALOG_TRIM);
        shell.setText(Texts.FIND_REPLACE_DIALOG_TITLE);
        shell.setLayout(new GridLayout());
        shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellActivated(final ShellEvent e) {
                dataToUI();
            }
        });

        // Create the search group
        if (findGroup == null) {
            findGroup = new TextHexInputGroup(findList);
        }
        findGroup.initialise();
        findGroup.group.setText(Texts.FIND_REPLACE_DIALOG_FIND_GROUP_LABEL);
        final SelectionAdapter hexTextSelectionAdapter = new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                ignoreCaseCheckBox.setEnabled(e.widget == findGroup.textRadioButton);
            }
        };
        findGroup.textRadioButton.addSelectionListener(hexTextSelectionAdapter);
        findGroup.hexRadioButton.addSelectionListener(hexTextSelectionAdapter);

        // create the replace with group
        if (replaceGroup == null) {
            replaceGroup = new TextHexInputGroup(replaceList);
        }
        replaceGroup.initialise();
        replaceGroup.group.setText(Texts.FIND_REPLACE_DIALOG_REPLACE_GROUP_LABEL);

        createDirectionGroup();
        createOptionsGroup();
        createFeedbackComposite();
        createProgressComposite();
        createButtonBarComposite();

        shell.setDefaultButton(findButton);

        shell.addListener(SWT.Close, event -> myTarget.stopSearching());
    }

    void doFind() {
        prepareToRun();
        progressBarStopButton.setText(Texts.FIND_REPLACE_DIALOG_STOP_SEARCHING_BUTTON_LABEL);
        final String findLiteral = findGroup.textCombo.getText();

        if (findLiteral.length() > 0) {
            try {
                final Match match = myTarget.findAndSelect(findLiteral, findGroup.hexRadioButton.getSelection(), forwardRadioButton.getSelection(),
                        ignoreCaseCheckBox.getSelection());
                if (match.isFound()) {
                    sendInfoMessage(TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_FOUND, findLiteral,
                            NumberUtility.getDecimalAndHexString(match.getStartPosition())));
                } else if (match.getException() == null) {
                    sendErrorMessage(TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_NOT_FOUND, findLiteral));
                } else {
                    sendErrorMessage(TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_ERROR_WHILE_SEARCHING, findLiteral,
                            match.getException().getLocalizedMessage()));
                }
            } catch (final NumberFormatException ex) {
                sendErrorMessage(ex.getMessage());
            }
        } else {
            sendErrorMessage(Texts.FIND_REPLACE_DIALOG_MESSAGE_SPECIFY_VALUE_TO_FIND);
        }
        endOfRun();
    }

    void doReplace() {
        replace();
        doFind();
    }

    void doReplaceAll() {
        prepareToRun();
        progressBarStopButton.setText(Texts.FIND_REPLACE_DIALOG_STOP_SEARCHING_BUTTON_LABEL);
        final String findLiteral = findGroup.textCombo.getText();
        final String replaceLiteral = replaceGroup.textCombo.getText();

        if (findLiteral.length() > 0) {
            try {

                final long[] result = myTarget.replaceAll(findLiteral, findGroup.hexRadioButton.getSelection(), forwardRadioButton.getSelection(),
                        ignoreCaseCheckBox.getSelection(), replaceLiteral, replaceGroup.hexRadioButton.getSelection());
                final long replacements = result[0];
                final long startPosition = result[1];

                if (replacements == 1) {
                    sendInfoMessage(TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_ONE_REPLACEMENT, findLiteral, replaceLiteral,
                            NumberUtility.getDecimalAndHexString(startPosition)));
                } else {
                    sendInfoMessage(TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_MANY_REPLACEMENTS, NumberUtility.getDecimalString(replacements),
                            findLiteral, replaceLiteral));
                }
            } catch (final NumberFormatException ex) {
                sendErrorMessage(ex.getMessage());
            } catch (final IOException ex) {
                sendErrorMessage(
                        TextUtility.format(Texts.FIND_REPLACE_DIALOG_MESSAGE_ERROR_WHILE_REPLACING, findLiteral, replaceLiteral, ex.getLocalizedMessage()));
            }
        } else {
            sendErrorMessage(Texts.FIND_REPLACE_DIALOG_MESSAGE_SPECIFY_VALUE_TO_FIND);
        }
        endOfRun();
    }

    void dataToUI() {
        findGroup.setEnabled(!searching);
        replaceGroup.setEnabled(!searching);

        directionGroup.setEnabled(!searching);
        forwardRadioButton.setEnabled(!searching);
        backwardRadioButton.setEnabled(!searching);

        ignoreCaseCheckBox.setEnabled(!searching);

        findButton.setEnabled(!searching);
        replaceButton.setEnabled(!searching);
        replaceAllButton.setEnabled(!searching);

        closeButton.setEnabled(!searching);
        if (searching) {
            return;
        }

        final boolean somethingToFind = findGroup.textCombo.getText().length() > 0;
        long selectionLength = 0L;
        if (myTarget != null) {
            selectionLength = myTarget.getSelection().getLength();
        }
        findButton.setEnabled(somethingToFind);
        replaceButton.setEnabled(selectionLength > 0L && somethingToFind);
        replaceAllButton.setEnabled(somethingToFind);
    }

    private void endOfRun() {
        searching = false;
        if (progressComposite.isDisposed()) {
            return;
        }

        setProgressCompositeVisible(false);
        dataToUI();
    }

    private void prepareToRun() {
        searching = true;
        lastFindHexButtonSelected = findGroup.hexRadioButton.getSelection();
        lastReplaceHexButtonSelected = replaceGroup.hexRadioButton.getSelection();
        replaceGroup.rememberText();
        findGroup.rememberText();
        lastForward = forwardRadioButton.getSelection();
        lastIgnoreCase = ignoreCaseCheckBox.getSelection();
        feedbackLabel.setText(Texts.FIND_REPLACE_DIALOG_MESSAGE_SEARCHING);
        dataToUI();
        activateProgressBar();
    }

    private void replace() {

        try {
            myTarget.replace(replaceGroup.textCombo.getText(), replaceGroup.hexRadioButton.getSelection());
        } catch (final NumberFormatException ex) {
            feedbackLabel.setText(ex.getMessage());
        }
    }
}
