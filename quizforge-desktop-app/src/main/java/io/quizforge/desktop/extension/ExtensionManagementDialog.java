package io.quizforge.desktop.extension;

import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/** Local package installation; activation occurs on the next application startup. */
public final class ExtensionManagementDialog {
    private ExtensionManagementDialog() { }
    public static void show(Window owner) {
        var manager = ExtensionManager.getDefault();
        Dialog<Void> dialog = new Dialog<>();dialog.setTitle("题型扩展");dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox content = new VBox(14);content.setPrefWidth(480);
        content.getChildren().add(UiTheme.label("应用没有预装题型。导入 .qfext、确认权限并重启后使用；开发预览可直接打开源码目录。", "editor-caption"));
        VBox packages = new VBox(8);
        Label status = new Label();status.setWrapText(true);status.setId("extension-install-status");
        refreshPackages(packages,manager,owner,status);
        Button install = new Button("安装题型扩展…");install.setId("extension-install-package");
        install.setOnAction(event -> {
            FileChooser chooser = new FileChooser();chooser.setTitle("安装题型扩展");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QuizForge 题型扩展", "*.qfext"));
            var chosen = chooser.showOpenDialog(owner);if (chosen == null) return;
            install.setDisable(true);
            try {
                var inspection=manager.inspect(chosen.toPath());
                var approved=reviewPermissions(owner,inspection.manifest(),null,"安装并授权");
                if(approved==null){install.setDisable(false);return;}
                manager.install(chosen.toPath(),inspection.sha256(),approved).whenComplete((extension,failure) -> {
                    install.setDisable(false);
                    if (failure != null) { status.setText("安装失败：" + failure.getMessage());return; }
                    refreshPackages(packages,manager,owner,status);
                    status.setText("已安装。重启应用后，新题型将出现在题库编辑菜单中。");
                });
            } catch (java.io.IOException|RuntimeException failure) { install.setDisable(false);status.setText("安装失败：" + failure.getMessage()); }
        });
        Button develop = new Button("加载开发目录…"); develop.setId("extension-load-development-directory");
        develop.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("选择包含 manifest.json 的题型扩展开发目录");
            var directory = chooser.showDialog(owner); if (directory == null) return;
            develop.setDisable(true); status.setText("正在连接主浏览区实时预览…");
            manager.loadDevelopmentDirectory(directory.toPath()).whenComplete((nothing, failure) -> {
                develop.setDisable(false);
                if (failure != null) { status.setText("开发目录加载失败：" + failure.getMessage()); return; }
                dialog.close();
            });
        });
        Button previewButton = new Button("独立开发预览…");
        previewButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("选择题型扩展开发目录");
            var directory = chooser.showDialog(owner); if (directory == null) return;
            try {
                var preview = new ExtensionDevelopmentWindow(owner,directory.toPath());
                dialog.close();
                preview.stage().show();
            }
            catch (RuntimeException failure) { status.setText("开发预览打开失败：" + failure.getMessage()); }
        });
        Label liveStatus = new Label(); liveStatus.setWrapText(true); liveStatus.textProperty().bind(manager.developmentStatusProperty());
        Button stop = new Button("停止实时预览"); stop.setOnAction(event -> manager.stopDevelopment());
        content.getChildren().addAll(packages, install, develop, previewButton, stop, liveStatus, status);dialog.getDialogPane().setContent(content);
        if (!manager.failures().isEmpty()) content.getChildren().add(new Label("部分扩展未加载：\n" + String.join("\n", manager.failures())));
        UiTheme.apply(dialog);dialog.showAndWait();
    }
    private static void refreshPackages(VBox packages,ExtensionManager manager,Window owner,Label status) {
        packages.getChildren().clear();
        try {
            for(var extension:manager.diskInstalled()) {
                Label title=new Label(extension.manifest().name()+" · "+extension.manifest().version());
                Button permissions=new Button("权限…");permissions.setId("extension-permissions-"+extension.manifest().id()+"-"+extension.manifest().version());
                permissions.setOnAction(event->{
                    try {
                        var current=manager.grantedPermissions(extension);
                        var approved=reviewPermissions(owner,extension.manifest(),current,"保存权限");
                        if(approved==null)return;
                        manager.setPermissions(extension,approved);
                        status.setText("权限已更新，已打开的题卡立即生效，作答和草稿保持不变。");
                    } catch(java.io.IOException|RuntimeException failure){status.setText("权限更新失败："+failure.getMessage());}
                });
                packages.getChildren().add(new javafx.scene.layout.HBox(12,title,permissions));
            }
            if(packages.getChildren().isEmpty())packages.getChildren().add(new Label("暂无已安装的题型扩展"));
        } catch(java.io.IOException failure){status.setText("无法读取已安装扩展："+failure.getMessage());}
    }
    private static java.util.Map<String,java.util.Set<String>> reviewPermissions(Window owner,io.quizforge.infrastructure.extension.ExtensionManifest manifest,
            java.util.Map<String,java.util.Set<String>> current,String actionLabel) {
        Dialog<java.util.Map<String,java.util.Set<String>>> review=new Dialog<>();
        review.initOwner(owner);review.setTitle("确认题型扩展权限");
        ButtonType approve=new ButtonType(actionLabel,ButtonBar.ButtonData.OK_DONE);
        review.getDialogPane().getButtonTypes().addAll(approve,ButtonType.CANCEL);
        VBox body=new VBox(12);body.setPrefWidth(450);
        Label heading=new Label(manifest.name()+" · "+manifest.version()+"\n"+manifest.id());heading.setWrapText(true);body.getChildren().add(heading);
        Label explanation=new Label(current==null?"选择允许扩展使用的功能。未授权的操作将被拒绝；更新版本需要重新确认。":"勾选以恢复权限，取消勾选以撤销权限。保存后立即生效；已保存的题目、答案和草稿不会删除。");
        explanation.setWrapText(true);body.getChildren().add(explanation);
        var controls=new java.util.LinkedHashMap<String,java.util.Map<String,CheckBox>>();
        for(var type:manifest.types()) {
            body.getChildren().add(new Label(type.label()));
            var checks=new java.util.LinkedHashMap<String,CheckBox>();controls.put(type.id(),checks);
            type.permissions().stream().sorted().forEach(permission->{
                CheckBox check=new CheckBox(io.quizforge.infrastructure.extension.ExtensionPermissions.LABELS.get(permission));
                check.setSelected(current==null||current.getOrDefault(type.id(),java.util.Set.of()).contains(permission));
                check.setId("extension-permission-"+type.id()+"-"+permission);checks.put(permission,check);body.getChildren().add(check);
            });
            if(checks.isEmpty())body.getChildren().add(new Label("未请求操作权限，仅能读取和展示当前题目。"));
        }
        if(current!=null) {
            Button clear=new Button("撤销全部"),all=new Button("恢复全部声明权限");
            clear.setId("extension-permissions-clear");all.setId("extension-permissions-all");
            clear.setOnAction(event->controls.values().forEach(checks->checks.values().forEach(check->check.setSelected(false))));
            all.setOnAction(event->controls.values().forEach(checks->checks.values().forEach(check->check.setSelected(true))));
            body.getChildren().add(new javafx.scene.layout.HBox(10,clear,all));
        }
        ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(420);
        review.getDialogPane().setContent(scroll);
        review.setResultConverter(button->{
            if(button!=approve)return null;
            var result=new java.util.LinkedHashMap<String,java.util.Set<String>>();
            controls.forEach((type,checks)->result.put(type,checks.entrySet().stream().filter(entry->entry.getValue().isSelected()).map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet())));
            return result;
        });
        UiTheme.apply(review);return review.showAndWait().orElse(null);
    }
}
