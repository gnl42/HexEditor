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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;

import javax.swing.JOptionPane;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import me.glindholm.eclipse.plugin.javahexeditor2.HelpResources;
import me.glindholm.eclipse.plugin.javahexeditor2.Manager;
import me.glindholm.eclipse.plugin.javahexeditor2.PreferencesManager;
import me.glindholm.eclipse.plugin.javahexeditor2.common.SWTUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.common.TextUtility;
import me.glindholm.eclipse.plugin.javahexeditor2.standalone.HexEditorMenu.Actions;

/**
 * Stand-alone wrapper for the Hex Editor. The stand-alone version is compiled with and for Java
 * 1.6.
 *
 * @author Jordi Bergenthal, Peter Dell
 */
public final class HexEditor {

    private static final String ICON_PATH = "images/javahexeditor-48x48.png";

    Shell shell;
    HexEditorMenu menu;
    Manager manager;

    private HexEditorPreferences preferences;
    private PreferencesManager preferencesManager;

    /**
     * Point of entry to the stand-alone version
     *
     * @param args optional first String: name of a file to edit
     */
    public static void main(final String[] args) throws Throwable {
        final HexEditor instance = new HexEditor();
        try {
            instance.run(args);
        } catch (final Throwable th) {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);
            printWriter.println(th.getMessage());
            th.printStackTrace(printWriter);
            JOptionPane.showMessageDialog(null, stringWriter.toString(), "Unexpected Fatal Error", JOptionPane.ERROR_MESSAGE);
            throw th;
        }
    }

    /**
     * Creation is private.
     */
    private HexEditor() {
    }

    /**
     * Point of entry to the stand-alone version
     *
     * @param args The command line arguments, not <code>null</code>.
     */
    private void run(final String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("Parameter 'args' must not be null.");
        }
        File file = null;
        if (args.length > 0 && args[0] != null) {
            file = new File(args[0]);
            if (!file.isFile() || !file.canRead()) {
                file = null;
            }
        }
        final Display display = Display.getDefault();
        manager = new Manager((contentFile, monitor) -> {
            if (contentFile.exists()) {
                contentFile.setLastModified(System.currentTimeMillis());
            }
        });
        preferences = new HexEditorPreferences(this);
        preferences.load();

        createShell();
        shell.pack();
        shell.open();
        if (file != null) {
            doOpen(file, false, null);
        } else {
            doOpen(null, true, null);
        }

        while (!shell.isDisposed()) {
            try {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            } catch (final OutOfMemoryError ex) {
                SWTUtility.showErrorMessage(shell, Texts.OUT_OF_MEMORY_ERROR_TITLE, Texts.OUT_OF_MEMORY_ERROR_MESSAGE);
            } catch (final RuntimeException ex) {
                final StringWriter writer = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(writer);
                ex.printStackTrace(printWriter);
                SWTUtility.showErrorMessage(shell, Texts.FATAL_ERROR_TITLE, Texts.FATAL_ERROR_MESSAGE, writer.toString());
                display.dispose();
                throw ex;
            }
        }
        display.dispose();
    }

    /**
     * This method initializes sShell
     */
    private void createShell() {
        shell = new Shell(Display.getDefault(), SWT.MODELESS | SWT.SHELL_TRIM);

        final InputStream stream = HexEditor.class.getClassLoader().getResourceAsStream(ICON_PATH);
        if (stream == null) {
            throw new RuntimeException("Icon '" + ICON_PATH + "' not found in class path.");
        }
        try {
            final Image hexIcon = new Image(shell.getDisplay(), stream);
            shell.setImage(hexIcon);
            shell.addDisposeListener(e -> hexIcon.dispose());
        } catch (final SWTException ex) {
        } finally {
            try {
                stream.close();
            } catch (final IOException ex) {
            }
        }

        shell.setText(Manager.APPLICATION_NAME);
        shell.setLayout(new FillLayout());
        menu = new HexEditorMenu(this);
        menu.fileSubMenu.addMenuListener(new MenuAdapter() {
            @Override
            public void menuShown(final MenuEvent e) {
                dataToUI();
            }
        });
        menu.editSubMenu.addMenuListener(new MenuAdapter() {
            @Override
            public void menuShown(final MenuEvent e) {
                dataToUI();

            }
        });
        shell.setMenuBar(menu.menuBar);
        createComposite();
        shell.addListener(SWT.Close, e -> {
            if (!doClose()) {
                e.doit = false;
            }
        });
        manager.addListener(event -> dataToUI());
    }

    /**
     * This method initializes composite
     */
    private void createComposite() {
        final GridLayout gridLayout = new GridLayout(1, true);
        gridLayout.marginHeight = 0;
        gridLayout.verticalSpacing = 3;
        gridLayout.marginBottom = 2;
        final Composite hexTextsParent = new Composite(shell, SWT.NONE);
        hexTextsParent.setLayout(gridLayout);

        manager.createEditorPart(hexTextsParent);

        final GridData gridData = new GridData();
        final GC gc = new GC(hexTextsParent);
        gridData.heightHint = gc.getFontMetrics().getHeight();
        gc.dispose();
        final Composite statusLine = manager.createStatusPart(hexTextsParent, false);
        statusLine.setLayoutData(gridData);

        final DropTarget target = new DropTarget(hexTextsParent, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
        target.setTransfer(FileTransfer.getInstance(), TextTransfer.getInstance());
        target.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(final DropTargetEvent e) {
                if (e.detail == DND.DROP_NONE) {
                    e.detail = DND.DROP_COPY;
                }
            }

            @Override
            public void drop(final DropTargetEvent event) {
                if (event.data == null || ((String[]) event.data).length < 1) {
                    event.detail = DND.DROP_NONE;

                    return;
                }
                final File file = new File(((String[]) event.data)[0]);
                if (!file.exists() || file.isDirectory() || !file.canRead()) {
                    event.detail = DND.DROP_NONE;
                    SWTUtility.showErrorMessage(shell, Texts.OPEN_ERROR_TITLE, Texts.OPEN_ERROR_MESSAGE_CANNOT_OPEN_DROPPED_FILE, file.getAbsolutePath());
                } else {
                    doOpen(file, false, null);
                }
            }
        });
    }

    void dataToUI() {
        final boolean selected = manager.isTextSelected();
        final boolean lengthModifiable = selected && !manager.isOverwriteMode();

        // Title bar
        final StringBuilder title = new StringBuilder();
        final File contentFile = manager.getContentFile();
        if (contentFile != null) {
            if (manager.isDirty()) {
                title.append('*');
            }
            title.append(contentFile.getAbsolutePath()).append(" - ");
        }
        title.append(Manager.APPLICATION_NAME);
        shell.setText(title.toString());

        // File menu
        menu.saveMenuItem.setEnabled(manager.isDirty());
        menu.saveAsMenuItem.setEnabled(manager.isValid());
        menu.saveSelectionAsMenuItem.setEnabled(selected);

        // Edit menu
        menu.pushCopy.setEnabled(selected);
        menu.pushCut.setEnabled(lengthModifiable);
        menu.pushPaste.setEnabled(manager.isEditable());

        menu.pushDelete.setEnabled(lengthModifiable);
        menu.pushTrim.setEnabled(lengthModifiable);
        menu.pushUndo.setEnabled(manager.canUndo());
        menu.pushRedo.setEnabled(manager.canRedo());

        menu.pushSelectAll.setEnabled(manager.isFilled());
        menu.pushSelectBlock.setEnabled(manager.isFilled());

        menu.pushGoTo.setEnabled(manager.isFilled());
        menu.pushFind.setEnabled(manager.isFilled());
    }

    void performAction(final int actionId) {
        switch (actionId) {
        case Actions.NEW:
            doOpen(null, true, null);
            break;
        case Actions.OPEN:
            doOpen(null, false, null);
            break;
        case Actions.SAVE:
            doSave();
            break;
        case Actions.SAVE_AS:
            doSaveAs();
            break;
        case Actions.SAVE_SELECTION_AS:
            doSaveSelectionAs();
            break;
        case Actions.EXIT:
            shell.close();
            shell.dispose();
            break;

        case Actions.UNDO:
            manager.doUndo();
            break;
        case Actions.REDO:
            manager.doRedo();
            break;

        case Actions.CUT:
            manager.doCut();
            break;
        case Actions.COPY:
            manager.doCopy();
            break;
        case Actions.PASTE:
            manager.doPaste();
            break;

        case Actions.DELETE:
            manager.doDelete();
            break;
        case Actions.TRIM:
            manager.doTrim();
            break;

        case Actions.SELECT_ALL:
            manager.doSelectAll();
            break;
        case Actions.SELECT_BLOCK:
            manager.doSelectBlock();
            break;

        case Actions.GO_TO:
            manager.doGoTo();
            break;
        case Actions.FIND:
            manager.doFind();
            break;

        case Actions.PREFERENCES:
            doPreferences();
            break;

        case Actions.HELP_CONTENTS:
            doOpenHelp(false);
            break;
        case Actions.WEB_SITE:
            doOpenHelp(true);
            break;
        case Actions.ABOUT:

            SWTUtility.showMessage(shell, SWT.ICON_INFORMATION | SWT.OK, Texts.ABOUT_DIALOG_TITLE,
                    TextUtility.format(Texts.ABOUT_DIALOG_TEXT, manager.getBuildOS(), manager.getBuildVersion()));
            break;
        default:
            break;
        }
    }

    void doOpen(final File file, final boolean newFile, final String charset) {
        if (!doClose()) {
            return;
        }
        try {
            manager.doOpen(file, newFile, charset);
        } catch (final CoreException ex) {
            SWTUtility.showErrorMessage(shell, Texts.OPEN_ERROR_TITLE, Texts.OPEN_ERROR_MESSAGE, ex.getMessage());
        }

    }

    private boolean doSave() {
        final File file = manager.getContentFile();
        if (file == null) {
            return doSaveAs();
        }

        try {
            manager.saveAsFile(file, null);
        } catch (final IOException ex) {
            SWTUtility.showErrorMessage(shell, Texts.SAVE_ERROR_TITLE, Texts.SAVE_ERROR_MESSAGE, ex.getMessage());
            return false;
        }

        return true;
    }

    boolean doClose() {
        // Anything open and any changes to save?
        if (manager.getContent() == null || !manager.isDirty()) {
            return true;
        }

        final MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO | SWT.CANCEL);
        box.setText(Texts.SAVE_ON_CLOSE_TITLE);
        box.setMessage(Texts.SAVE_ON_CLOSE_MESSAGE);

        final int result = box.open();
        if (result == SWT.CANCEL) {
            return false;
        }
        if (result == SWT.YES) {
            return doSave();
        }

        return true;
    }

    private boolean doSaveAs() {
        final File file = manager.showSaveAsDialog(shell, false);
        if (file == null) {
            return false;
        }

        try {
            manager.saveAsFile(file, null);
        } catch (final IOException ex) {
            SWTUtility.showErrorMessage(shell, Texts.SAVE_ERROR_TITLE, Texts.SAVE_ERROR_MESSAGE, ex.getMessage());
            return false;
        }

        return true;
    }

    private void doSaveSelectionAs() {
        final File file = manager.showSaveAsDialog(shell, true);
        if (file == null) {
            return;
        }

        try {
            manager.doSaveSelectionAs(file);
        } catch (final IOException ex) {
            SWTUtility.showErrorMessage(shell, Texts.SAVE_ERROR_TITLE, Texts.SAVE_ERROR_MESSAGE, ex.getMessage());
        }

    }

    private void doPreferences() {
        if (preferencesManager == null) {
            preferencesManager = new PreferencesManager(preferences.getFontData());
        }
        if (preferencesManager.openDialog(shell) == SWT.OK) {
            preferences.setFontData(preferencesManager.getFontData());
            preferences.store();
            manager.setTextFont(preferencesManager.getFontData());
        }
    }

    private void doOpenHelp(final boolean online) {
        final URI uri = HelpResources.getHelpResourceURI(online);
        try {
            Desktop.getDesktop().browse(uri);
        } catch (final IOException ex) {
            SWTUtility.showErrorMessage(shell, shell.getText(), Texts.OPEN_HELP_FILE_ERROR_MESSAGE, uri.toString(), ex.getMessage());
        }

    }

}
