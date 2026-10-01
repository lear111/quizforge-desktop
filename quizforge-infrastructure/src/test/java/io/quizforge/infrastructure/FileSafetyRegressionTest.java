package io.quizforge.infrastructure;

import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.markdown.LocalMarkdownFileStorage;
import io.quizforge.infrastructure.filesystem.qbank.LocalQuestionBankFileStorage;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileCatalog;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileOperations;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FileSafetyRegressionTest {
    @TempDir Path temp;
    private final Instant now=Instant.parse("2026-10-01T00:00:00Z");
    private WorkspacePathResolver paths(){return new WorkspacePathResolver(new QuizForgeDataDirectory(temp.resolve("data")));}

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void questionBankPublicationAndRollbackPreserveExternalVersions(boolean guarded) throws Exception {
        var paths=paths();var workspace=new Workspace(WorkspaceId.newId(),"review",now,now);paths.create(workspace);
        Path file=paths.workspaceRoot(workspace.id()).resolve("question-banks/review.qbank");
        var writer=new QBankPackageWriter();
        var original=io.quizforge.infrastructure.testing.EssayTestBanks.bank();
        var editor=new io.quizforge.core.question.service.QuestionBankEditorModel(original);editor.setTitle("Edited");
        var outside=new io.quizforge.core.question.service.QuestionBankEditorModel(original);outside.setTitle("External");
        Path externalFile=temp.resolve("external.qbank");writer.write(externalFile,outside.bank());
        byte[] external=Files.readAllBytes(externalFile);writer.write(file,original);
        var storage=new LocalQuestionBankFileStorage(paths);
        try(var pending=guarded ? storage.stageReplace(workspace.id(),"question-banks/review.qbank",editor.bank(),
                io.quizforge.core.port.QuestionResourceInput.NONE,original)
                : storage.stageReplace(workspace.id(),"question-banks/review.qbank",editor.bank())) {
            Files.write(file,external);assertThrows(IllegalStateException.class,pending::publish);
        }
        assertArrayEquals(external,Files.readAllBytes(file));
        writer.write(file,original);
        byte[] edited;
        try(var pending=storage.stageReplace(workspace.id(),"question-banks/review.qbank",editor.bank())) {
            pending.publish();edited=Files.readAllBytes(file);Files.write(file,external);
            assertThrows(IllegalStateException.class,pending::rollback);
        }
        assertArrayEquals(external,Files.readAllBytes(file));
        try(var files=Files.list(file.getParent())) {
            assertTrue(files.filter(path->path.toString().endsWith(".tmp")).anyMatch(path->{
                try{return java.util.Arrays.equals(edited,Files.readAllBytes(path));}
                catch(java.io.IOException failure){throw new AssertionError(failure);}
            }));
        }
    }

    @Test void junctionCannotReadDeleteOrRedirectCredentialsOutsideOwnedRoot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("windows"));
        var paths=paths();var workspace=new Workspace(WorkspaceId.newId(),"review",now,now);paths.create(workspace);
        Path root=paths.workspaceRoot(workspace.id()), outside=Files.createDirectory(temp.resolve("outside"));
        Path external=outside.resolve("retained.md");Files.writeString(external,"external");
        Path link=root.resolve("linked");
        var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toString(),outside.toString()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();assertEquals(0,process.waitFor());
        var catalog=new LocalWorkspaceFileCatalog(paths,new QuestionBankV2Codec());
        assertThrows(RuntimeException.class,()->catalog.readText(workspace.id(),"linked/retained.md"));
        assertThrows(RuntimeException.class,()->new LocalWorkspaceFileOperations(paths).delete(workspace.id(),"linked/retained.md"));
        assertEquals("external",Files.readString(external));
        var data=new QuizForgeDataDirectory(temp.resolve("credentials"));
        var secrets=new io.quizforge.infrastructure.security.WindowsDpapiCredentialStore(data);
        secrets.save("review-reference","owned-test-key");
        Files.move(data.root().resolve("secrets"),data.root().resolve("original-secrets"));
        var credentialLink=new ProcessBuilder("cmd.exe","/c","mklink","/J",data.root().resolve("secrets").toString(),outside.toString()).redirectErrorStream(true).start();
        credentialLink.getInputStream().readAllBytes();assertEquals(0,credentialLink.waitFor());
        assertThrows(RuntimeException.class,()->secrets.get("review-reference"));
        assertThrows(RuntimeException.class,()->secrets.save("review-reference","replacement-test-key"));
        assertThrows(RuntimeException.class,()->secrets.delete("review-reference"));
        assertFalse(Files.exists(outside.resolve("review-reference.dpapi")));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void externalMarkdownBeforePublishIsRetainedWithRecoverableEdit(boolean guarded) throws Exception {
        var paths=paths();var workspace=new Workspace(WorkspaceId.newId(),"review",now,now);paths.create(workspace);
        Path root=paths.workspaceRoot(workspace.id()), file=root.resolve("documents/test.md");
        Files.writeString(file,"original");
        var storage=new LocalMarkdownFileStorage(paths);
        try(var staged=guarded ? storage.stageReplace(workspace.id(),"documents/test.md","edited","original")
                : storage.stageReplace(workspace.id(),"documents/test.md","edited")) {
            Files.writeString(file,"external");
            assertThrows(IllegalStateException.class,staged::publish);
            assertEquals("external",Files.readString(file));
        }
        try(var siblings=Files.list(file.getParent())) {
            assertTrue(siblings.filter(path->path.toString().endsWith(".tmp"))
                    .anyMatch(path->{try{return Files.readString(path).equals("edited");}catch(Exception failure){throw new AssertionError(failure);}}));
        }
    }

    @Test void rollbackRetainsExternalMarkdownAndBothRecoveryVersions() throws Exception {
        var paths=paths();var workspace=new Workspace(WorkspaceId.newId(),"review",now,now);paths.create(workspace);
        Path root=paths.workspaceRoot(workspace.id()), file=root.resolve("documents/test.md");Files.writeString(file,"original");
        try(var staged=new LocalMarkdownFileStorage(paths).stageReplace(workspace.id(),"documents/test.md","edited")) {
            staged.publish();Files.writeString(file,"external");
            assertThrows(IllegalStateException.class,staged::rollback);
            assertEquals("external",Files.readString(file));
        }
        try(var siblings=Files.list(file.getParent())) {
            var recovered=siblings.filter(path->path.toString().endsWith(".tmp")).map(path->{
                try{return Files.readString(path);}catch(Exception failure){throw new AssertionError(failure);}
            }).toList();
            assertTrue(recovered.contains("original"));assertTrue(recovered.contains("edited"));
        }
    }

    @Test void ordinaryFailureRestoresOriginalWithoutLeavingRecoveryFiles() throws Exception {
        var paths=paths();var workspace=new Workspace(WorkspaceId.newId(),"review",now,now);paths.create(workspace);
        Path file=paths.workspaceRoot(workspace.id()).resolve("documents/test.md");Files.writeString(file,"original");
        try(var staged=new LocalMarkdownFileStorage(paths).stageReplace(workspace.id(),"documents/test.md","edited")) {
            staged.publish();staged.rollback();
        }
        assertEquals("original",Files.readString(file));
        try(var siblings=Files.list(file.getParent())){assertEquals(1,siblings.count());}
    }
}
