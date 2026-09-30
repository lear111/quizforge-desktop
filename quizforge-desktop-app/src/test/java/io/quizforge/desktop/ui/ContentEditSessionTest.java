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
            assertEquals(List.of(resource),saved.addedResources().stream().map(QBankImageImporter.ImportedImage::resource).toList());
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
        assertEquals(Set.of(imported.resource().id()),QuestionContentData.imageIds(model.bank().questions().get(1).prompt()));
    }
}
