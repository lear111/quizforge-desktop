package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.desktop.config.DesktopConfiguration;
import io.quizforge.infrastructure.filesystem.*;
import java.nio.file.Files;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.stage.Stage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Real files and real Step 4 services; never configures or calls a live AI provider. */
final class ShellFixture implements AutoCloseable {
    final AnnotationConfigApplicationContext context;
    final WorkspaceService workspaces;
    final WorkspaceFileService files;
    final Workspace alpha;
    final Workspace beta;
    final Path alphaRoot;
    final Path history;
    final AtomicInteger settingsOpened = new AtomicInteger();
    final AtomicInteger aiOpened = new AtomicInteger();
    final AtomicReference<String> copiedText = new AtomicReference<>();

    ShellFixture(Path temp) throws Exception {
        String previous = System.getProperty("quizforge.dataDir");
        System.setProperty("quizforge.dataDir", temp.resolve("data").toString());
        try { context = new AnnotationConfigApplicationContext(DesktopConfiguration.class); }
        finally {
            if (previous == null) System.clearProperty("quizforge.dataDir");
            else System.setProperty("quizforge.dataDir", previous);
        }
        workspaces = context.getBean(WorkspaceService.class);
        files = context.getBean(WorkspaceFileService.class);
        alpha = workspaces.createWorkspace("Java 学习库");
        beta = workspaces.createWorkspace("Spring 学习");
        var paths = new WorkspacePathResolver(context.getBean(QuizForgeDataDirectory.class));
        alphaRoot = paths.workspaceRoot(alpha.id());
        history = temp.resolve("recent.txt");
        write("我的笔记/学习计划.md", "# 本周学习计划\n\n每天给自己留一点时间，整理知识，再通过练习巩固。\n\n## 今天\n\n- 阅读 Java 集合\n- 完成一组练习\n");
        write("原始材料/Java 官方文档.pdf", "sample");
        write("Java/Java集合.md", document("ArrayList 保持插入顺序，并支持按索引访问。"));
        Files.createDirectories(alphaRoot.resolve("空目录"));
        write(".quizforge/hidden.md", "Private internal file");
        String revision = new StandardKnowledgeDocumentV1().parseIfStandard(document("ArrayList 保持插入顺序，并支持按索引访问。"))
                .orElseThrow().contentId();
        var source = new QuestionSourceDocument("doc_java", revision, "Java 集合");
        var ref = SourceRef.anchor("doc_java", revision, "section_list", 1, "Java 集合", "ArrayList");
        var one = Question.choice("q_one", "SINGLE_CHOICE", new TextContent("需要保持插入顺序并支持快速按索引访问时，应选择哪种集合？"), new TextContent("ArrayList 基于可扩容数组，按索引访问的时间复杂度为 O(1)。"), List.of(ref), new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("ArrayList")),
                        new ChoiceOption("opt_b", new TextContent("HashSet")), new ChoiceOption("opt_c", new TextContent("TreeMap")))), new ChoiceAnswerSpec(List.of("opt_a")));
        var two = Question.choice("q_two", "MULTIPLE_CHOICE", new TextContent("以下哪些描述适用于 ArrayList？"), new TextContent("它保持插入顺序并允许重复元素。"), List.of(ref), new ChoicePayload(List.of(new ChoiceOption("opt_d", new TextContent("保持插入顺序")),
                        new ChoiceOption("opt_e", new TextContent("允许重复元素")), new ChoiceOption("opt_f", new TextContent("自动去重")))), new ChoiceAnswerSpec(List.of("opt_d", "opt_e")));
        var bank = new QuestionBank("qb_java", "Java 集合练习", "2.0", List.of(), List.of(one, two), List.of());
        write("题库/Java集合.qbank", new QuestionBankV2Codec().write(bank));
        write("空草稿/新文档.md", document("").replace("doc_java", "doc_empty"));
        write("空草稿/新题库.qbank", """
{
  "schemaVersion": "2.0",
  "assetId": "qb_empty",
  "title": "新题库",
  "stimuli": [],
  "questions": [],
  "resources": []
}
""");
        write("错误文件/broken.qbank", "{ malformed");
        Files.writeString(paths.workspaceRoot(beta.id()).resolve("Spring.md"), "# Spring\n依赖注入笔记");
    }

    MainWorkspaceView shell(Stage stage) {
        var loader = new FilePresentationLoader(files, context.getBean(WorkspaceFileCatalog.class));
        var historyStore = new WorkspaceHistory(history);
        historyStore.visit(alpha);
        return new MainWorkspaceView(workspaces, files, historyStore, loader,
                context.getBean(QuestionBankReferenceResolver.class), stage, settingsOpened::incrementAndGet,
                (workspace, file) -> aiOpened.incrementAndGet(), context.getBean(QuestionBankFileEditService.class),
                context.getBean(MarkdownFileEditService.class),
                context.getBean(MarkdownDocumentRegistration.class), copiedText::set,
                context.getBean(AssetIndexRepository.class),
                context.getBean(io.quizforge.core.question.QuestionSourceLinkService.class),
                context.getBean(io.quizforge.core.port.PracticeRuntimeProvider.class));
    }

    static String document(String body) {
        return "---\nquizforge_format: study-document\nschema_version: \"1.0\"\nquizforge_id: doc_java\n"
                + "title: Java 集合\nlanguage: zh-CN\n---\n# Java 集合\n\n## 有序集合\n<!-- qf:id=chapter_list -->\n\n"
                + "<!-- qf:anchor=section_list -->\n### ArrayList\n<!-- qf:id=section_list -->\n\n" + body + "\n";
    }

    void write(String path, String text) throws Exception {
        Path file = alphaRoot.resolve(path);
        Files.createDirectories(file.getParent());
        QBankTestPackageBuilder.write(file, text);
    }

    @Override public void close() { context.close(); }
}
