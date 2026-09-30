package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import io.quizforge.infrastructure.testing.EssayTestBanks;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ContentEditSessionTest {
    @TempDir Path temp;

    @Test void stagedPngAndJpegBecomeAddedResourcesOnlyOnSave() throws Exception {
        for(String format:List.of("png","jpg")) {
            Path file=temp.resolve("picture."+format);Files.write(file,EssayTestBanks.image(format));
            var session=new ContentEditSession(new TextContent("Before"),List.of(),resource->null);
            var resource=session.stage(file);
            var edited=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Before"))),
                    new BlockImageNode(resource.id(),null,null,50,TextAlignment.CENTER))));
            assertTrue(session.resources().contains(resource));
            try(var stream=session.open(resource)){assertNotNull(stream);assertTrue(stream.read()>=0);}
            var saved=session.save(edited);
            assertTrue(saved.saved());assertEquals(edited,saved.content());
            assertEquals(List.of(resource),saved.addedResources().stream().map(StagedContentResource::resource).toList());
            assertEquals(format.equals("png")?"image/png":"image/jpeg",resource.mediaType());
        }
    }
    @Test void cancelDiscardsStagedResourceAndKeepsOriginal() throws Exception {
        Path file=temp.resolve("picture.png");Files.write(file,EssayTestBanks.image("png"));
        var original=new TextContent("Original");
        var session=new ContentEditSession(original,List.of(),resource->null);
        session.stage(file);
        var cancelled=session.cancel();assertFalse(cancelled.saved());assertNull(cancelled.content());
        assertTrue(session.resources().isEmpty());
    }
    @Test void removedReferenceIsReportedButSharedResourceSurvivesModelGc() throws Exception {
        Path file=temp.resolve("picture.png");Files.write(file,EssayTestBanks.image("png"));
        var imported=new QBankImageImporter().read(file);
        var original=new RichContent(new RichDocument(List.of(new BlockImageNode(imported.resource().id(),null,null))));
        var bank=EssayTestBanks.bank();
        var model=new QuestionBankEditorModel(bank);model.addResource(imported.resource());
        model.setPrompt(0,original);model.duplicateQuestion(0);
        var session=new ContentEditSession(original,model.bank().resources(),resource->imported.open());
        var saved=session.save(new TextContent("No picture"));
        assertEquals(Set.of(imported.resource().id()),saved.removedResourceReferences());
        model.setPrompt(0,saved.content());
        assertEquals(1,model.bank().resources().size());
        assertEquals(Set.of(imported.resource().id()),QuestionContentData.resourceIds(model.bank().questions().get(1).prompt()));
    }
    @Test void nativeDocumentBytesSurvivePackageMoveAndEditingDoesNotRewriteSharedDocuments() throws Exception {
        String picture="data:image/png;base64,"+Base64.getEncoder().encodeToString(EssayTestBanks.image("png"));
        String document=RichContentEditorAdapter.json(Map.of("version","1.0.4","options",Map.of("defaultFont","Georgia"),
                "data",Map.of("main",List.of(Map.of("value","First\n\nSecond","font","Georgia","size",24),
                        Map.of("type","image","value",picture,"width",137,"height",91)))));
        var session=new ContentEditSession(new TextContent("First"),List.of(),resource->null);
        var content=session.stageDocument(document,"First\n\nSecond");
        var saved=session.save(content);assertEquals(1,saved.addedResources().size());
        var pending=saved.addedResources().getFirst();assertEquals(ResourceKind.DOCUMENT,pending.resource().kind());
        assertEquals(document,session.document(content));
        var model=new QuestionBankEditorModel(EssayTestBanks.bank());model.addResource(pending.resource());model.setPrompt(0,content);
        var writer=new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter();
        var original=temp.resolve("bank.qbank");writer.write(original,model.bank(),resource->pending.open());
        var moved=temp.resolve("elsewhere/moved.qbank");Files.createDirectories(moved.getParent());Files.move(original,moved);
        try(var loaded=new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().open(moved)) {
            var restored=(DocumentContent)loaded.bank().questions().getFirst().prompt();
            var reopened=new ContentEditSession(restored,loaded.bank().resources(),loaded::open);
            assertEquals(document,reopened.document(restored));
            var unchanged=reopened.stageDocument(document,restored.text());
            assertEquals(restored,unchanged);assertTrue(reopened.save(unchanged).addedResources().isEmpty());
            assertFalse(reopened.cancel().saved());
            var shared=new QuestionBankEditorModel(loaded.bank());shared.duplicateQuestion(0);
            shared.setPrompt(0,new TextContent("Replacement"));assertEquals(1,shared.bank().resources().size());
            shared.setPrompt(1,new TextContent("Replacement"));assertTrue(shared.bank().resources().isEmpty());
        }
    }
}
