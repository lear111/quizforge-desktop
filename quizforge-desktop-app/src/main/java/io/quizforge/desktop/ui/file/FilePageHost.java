package io.quizforge.desktop.ui.file;

import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.function.*;
import javafx.scene.Node;
import javafx.scene.Scene;

/** Page composition callbacks preserve the outer pane and its tab lifecycle. */
record FilePageHost(Consumer<Node> top,Consumer<Node> center,Supplier<Node> currentCenter,Supplier<Scene> scene,
        BiConsumer<WorkspaceId,String> open,BooleanSupplier dirty) { }
