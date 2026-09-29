package io.quizforge.desktop.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/** Presentation helpers for the native sidebar menus and their native submenus. */
final class WorkspaceMenus {
    private WorkspaceMenus() { }

    static MenuItem action(String text, String id, String icon, Runnable action) {
        MenuItem item = decorate(new MenuItem(text), false);
        item.setId(id);
        item.setGraphic(UiTheme.workspaceMenuIcon(icon));
        item.setOnAction(event -> action.run());
        return item;
    }

    static MenuItem textAction(String text, String id, Runnable action) {
        MenuItem item = decorate(new MenuItem(text), true);
        item.setId(id);
        item.setOnAction(event -> action.run());
        return item;
    }

    static Menu submenu(String text, String id, String icon) {
        Menu menu = decorate(new Menu(text), false);
        menu.setId(id);
        menu.setGraphic(UiTheme.workspaceMenuIcon(icon));
        return menu;
    }

    static SeparatorMenuItem separator() {
        SeparatorMenuItem separator = new SeparatorMenuItem();
        separator.getStyleClass().add("workspace-menu-separator");
        return separator;
    }

    static void headerItem(MenuItem item) {
        item.parentPopupProperty().addListener((obs, before, popup) -> headerPopup(popup));
        headerPopup(item.getParentPopup());
    }

    private static void headerPopup(ContextMenu popup) {
        if (popup != null && !popup.getStyleClass().contains("workspace-header-menu"))
            popup.getStyleClass().add("workspace-header-menu");
    }

    private static <T extends MenuItem> T decorate(T item, boolean nested) {
        item.getStyleClass().add("workspace-menu-item");
        item.setMnemonicParsing(false);
        // JavaFX creates MenuButton and submenu popups lazily. Style the native popup
        // when attached, without replacing its skin, placement, focus or key handling.
        item.parentPopupProperty().addListener((obs, before, popup) -> {
            if (popup == null) return;
            if (!popup.getStyleClass().contains("workspace-context-menu"))
                popup.getStyleClass().add("workspace-context-menu");
            // A Menu first propagates its parent popup to its descendants. Only the
            // popup that directly owns this item is the actual submenu.
            if (nested && popup.getItems().contains(item)
                    && !popup.getStyleClass().contains("workspace-submenu"))
                popup.getStyleClass().add("workspace-submenu");
        });
        return item;
    }
}
