package io.quizforge.desktop.config;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.service.WorkspaceFileService;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.desktop.ui.shell.DesktopView;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import({InfrastructureConfiguration.class,ServiceConfiguration.class})
public class DesktopConfiguration {

    @Bean
    public DesktopView desktopView(WorkspaceService workspaces, WorkspaceFileService files,
            WorkspaceFileCatalog catalog, QuestionBankReferenceResolver references,
            AiSettingsService settings, AiConnectionService connections, QuizForgeDataDirectory directory,
            QuestionBankFileEditService bankEdits,
              MarkdownFileEditService markdownEdits, MarkdownDocumentRegistration registration,
              AssetIndexRepository assetIndex,
              io.quizforge.core.question.source.QuestionSourceLinkService sourceLinks,
              io.quizforge.core.port.PracticeRuntimeProvider practice) {
        return new DesktopView(workspaces, files, catalog, references,
                settings, connections, directory.root().resolve("desktop-recent-workspaces.txt"),
                  bankEdits, markdownEdits, registration, assetIndex, sourceLinks, practice);
    }
}
