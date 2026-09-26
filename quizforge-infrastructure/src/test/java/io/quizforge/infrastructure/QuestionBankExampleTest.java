package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.*;
import io.quizforge.infrastructure.persistence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestionBankExampleTest {
    @TempDir Path temp;

    @Test void bundledExampleCanBeCopiedIntoAnyWorkspaceDirectoryAndResolved() throws Exception {
        Path examples = Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("examples/step7-practice");
        if (!Files.isDirectory(examples)) examples = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().getParent().resolve("examples/step7-practice");
        Path documentFile = examples.resolve("Java集合示例.qdoc");
        Path bankFile = examples.resolve("Java集合练习.qbank");
        var qdocCodec = new QDocV1Codec();
        var bankCodec = new QuestionBankV1Codec();
        var document = qdocCodec.parse(Files.readString(documentFile));
        var bank = bankCodec.parse(Files.readString(bankFile));
        assertEquals(4, bank.questions().size());
        assertEquals(2, bank.questions().stream().filter(q -> q.type().equals("SINGLE_CHOICE")).count());
        assertEquals(2, bank.questions().stream().filter(q -> q.type().equals("MULTIPLE_CHOICE")).count());
        assertEquals(document.id(), bank.sourceDocuments().getFirst().assetId());
        assertEquals(qdocCodec.contentId(document), bank.sourceDocuments().getFirst().contentId());
        Set<String> sections = document.content().stream()
                .flatMap(chapter -> chapter.children().stream()).filter(DocumentNode.class::isInstance)
                .map(DocumentNode.class::cast).map(DocumentNode::id).collect(Collectors.toSet());
        for (var question : bank.questions()) for (var ref : question.sourceRefs()) {
            assertEquals(document.id(), ref.documentAssetId());
            assertEquals(qdocCodec.contentId(document), ref.documentContentId());
            assertTrue(sections.contains(ref.sectionId()));
        }

        var directory = new QuizForgeDataDirectory(temp.resolve("data"));
        var paths = new WorkspacePathResolver(directory);
        var workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                paths, Clock.systemUTC());
        var workspace = workspaces.createWorkspace("Example");
        Path custom = Files.createDirectories(paths.workspaceRoot(workspace.id()).resolve("学习/我的例题"));
        Files.copy(documentFile, custom.resolve(documentFile.getFileName()));
        Files.copy(bankFile, custom.resolve(bankFile.getFileName()));
        var scanner = new FileSystemWorkspaceAssetScanner(paths,
                new SqliteAssetIndexRepository(paths), Clock.systemUTC(), false);
        var assets = scanner.scan(workspace.id());
        assertEquals(2, assets.size());
        assertEquals(1, assets.stream().filter(a -> a.assetType() == AssetType.QUESTION_BANK).count());
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                new QuestionBankReferenceResolver(scanner).resolve(workspace.id(), bank).getFirst().status());
    }
}
