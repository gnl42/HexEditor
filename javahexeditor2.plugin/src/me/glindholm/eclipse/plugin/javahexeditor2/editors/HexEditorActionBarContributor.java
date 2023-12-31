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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import me.glindholm.eclipse.plugin.javahexeditor2.Manager;
import me.glindholm.eclipse.plugin.javahexeditor2.Texts;

/**
 * HexEditor contributor. Contributes status bar and menu bar items
 *
 * @author Jordi Bergenthal
 */
public final class HexEditorActionBarContributor extends EditorActionBarContributor {

    private final class MyMenuContributionItem extends ContributionItem {
        MenuItem myMenuItem;

        MyMenuContributionItem(final String id) {
            super(id);
        }

        @Override
        public void fill(final Menu parent, final int index) {
            myMenuItem = new MenuItem(parent, SWT.PUSH, index);
            myMenuItem.setEnabled(false);

            if (MenuIds.SAVE_SELECTION_AS.equals(getId())) {
                myMenuItem.setText(Texts.EDITOR_SAVE_SELECTION_AS_MENU_ITEM_LABEL);
                myMenuItem.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(final SelectionEvent e) {
                        activeEditor.saveAsToFile(true);
                    }
                });
            } else if (MenuIds.TRIM.equals(getId())) {
                myMenuItem.setText(Texts.EDITOR_TRIM_MENU_ITEM_LABEL);
                myMenuItem.addSelectionListener(new SelectionAdapter() {

                    @Override
                    public void widgetSelected(final SelectionEvent e) {
                        final Manager manager = activeEditor.getManager();
                        if (manager.isValid()) {
                            activeEditor.getManager().doTrim();
                        }
                    }
                });
            } else if (MenuIds.SELECT_BLOCK.equals(getId())) {
                myMenuItem.setText(Texts.EDITOR_SELECT_BLOCK_MENU_ITEM_LABEL);
                // TODO This only works after the "Edit" menu was shown once
                myMenuItem.setAccelerator(SWT.CONTROL | 'E');
                myMenuItem.addSelectionListener(new SelectionAdapter() {

                    @Override
                    public void widgetSelected(final SelectionEvent e) {
                        final Manager manager = activeEditor.getManager();
                        if (manager.isValid()) {
                            manager.doSelectBlock();
                        }
                    }
                });
            }
        }
    }

    private final class MyMenuListener implements IMenuListener {
        public MyMenuListener() {
        }

        @Override
        public void menuAboutToShow(final IMenuManager menu) {
            final boolean textSelected = activeEditor.getManager().isTextSelected();
            final boolean lengthModifiable = textSelected && !activeEditor.getManager().isOverwriteMode();
            final boolean filled = activeEditor.getManager().isFilled();

            MenuItem menuItem = getMenuItem(IWorkbenchActionConstants.M_FILE, MenuIds.SAVE_SELECTION_AS);
            if (menuItem != null) {
                menuItem.setEnabled(textSelected);
            }
            menuItem = getMenuItem(IWorkbenchActionConstants.M_EDIT, MenuIds.TRIM);
            if (menuItem != null) {
                menuItem.setEnabled(lengthModifiable);
            }
            menuItem = getMenuItem(IWorkbenchActionConstants.M_EDIT, MenuIds.SELECT_BLOCK);
            if (menuItem != null) {
                menuItem.setEnabled(filled);
            }
        }

        private MenuItem getMenuItem(final String prefix, final String menuId) {
            final IActionBars bars = getActionBars();

            final IContributionItem contributionItem = bars.getMenuManager().findUsingPath(prefix + '/' + menuId);
            if (contributionItem != null && ((MyMenuContributionItem) contributionItem).myMenuItem != null
                    && !((MyMenuContributionItem) contributionItem).myMenuItem.isDisposed()) {
                return ((MyMenuContributionItem) contributionItem).myMenuItem;
            }
            return null;
        }
    }

    private final class MyStatusLineContributionItem extends ContributionItem {
        MyStatusLineContributionItem(final String id) {
            super(id);
        }

        @Override
        public void fill(final Composite parent) {
            if (activeEditor != null) {
                activeEditor.getManager().createStatusPart(parent, true);
            }
        }
    }

    private static final class MenuIds {
        public static final String SAVE_SELECTION_AS = "saveSelectionAs";
        public static final String TRIM = "trim";
        public static final String SELECT_BLOCK = "selectBlock";
        public static final String SAVE_AS = "saveAs";
        public static final String DELETE = "delete";
        public static final String SELECT_ALL = "selectAll";
        public static final String ADDITIONS = "additions";
    }

    private static final String STATUS_LINE_ITEM_ID = "AllHexEditorStatusItemsItem";

    HexEditor activeEditor;

    public HexEditorActionBarContributor() {

    }

    /**
     * @see EditorActionBarContributor#contributeToMenu(org.eclipse.jface.action.IMenuManager)
     */
    @Override
    public void contributeToMenu(final IMenuManager menuManager) {
        IMenuManager menu = menuManager.findMenuUsingPath(IWorkbenchActionConstants.M_FILE);
        final IMenuListener myMenuListener = new MyMenuListener();
        if (menu != null) {
            menu.insertAfter(MenuIds.SAVE_AS, new MyMenuContributionItem(MenuIds.SAVE_SELECTION_AS));
            menu.addMenuListener(myMenuListener);
        }

        menu = menuManager.findMenuUsingPath(IWorkbenchActionConstants.M_EDIT);
        if (menu != null) {
            menu.insertAfter(MenuIds.DELETE, new MyMenuContributionItem(MenuIds.TRIM));
            menu.addMenuListener(myMenuListener);
        }

        menu = menuManager.findMenuUsingPath(IWorkbenchActionConstants.M_EDIT);
        if (menu != null) {
            menu.insertAfter(MenuIds.SELECT_ALL, new MyMenuContributionItem(MenuIds.SELECT_BLOCK));
            menu.addMenuListener(myMenuListener);
        }

        menu = menuManager.findMenuUsingPath(IWorkbenchActionConstants.M_NAVIGATE);
        if (menu != null) {
            final Action goToAction = new Action() {

                @Override
                public boolean isEnabled() {
                    return activeEditor.getManager().isFilled();
                }

                @Override
                public void run() {
                    activeEditor.getManager().doGoTo();
                }
            };
            // declared in org.eclipse.ui.workbench.text plugin.xml
            goToAction.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_GOTO);
            goToAction.setText(Texts.EDITOR_GO_TO_MENU_ITEM_LABEL);
            // TODO This only works after the "Navigate" menu was shown once
            // Eclipse standard even has the correct accelerator.
            // goToAction.setAccelerator(SWT.CTRL + 'L');
            menu.appendToGroup(MenuIds.ADDITIONS, goToAction);
        }
    }

    /**
     * @see EditorActionBarContributor#contributeToStatusLine(org.eclipse.jface.action.IStatusLineManager)
     */
    @Override
    public void contributeToStatusLine(final IStatusLineManager statusLineManager) {
        statusLineManager.add(new MyStatusLineContributionItem(STATUS_LINE_ITEM_ID));
    }

    /**
     * @see IEditorActionBarContributor#setActiveEditor(org.eclipse.ui.IEditorPart)
     */
    @Override
    public void setActiveEditor(final IEditorPart targetEditor) {
        if (targetEditor instanceof HexEditor) {
            if (activeEditor != null) {
                final Manager manager = ((HexEditor) targetEditor).getManager();
                manager.reuseStatusLinelFrom(activeEditor.getManager());
            }
            activeEditor = (HexEditor) targetEditor;
            activeEditor.getManager().setFocus();
            activeEditor.updateActionsStatus();
        }
    }
}
