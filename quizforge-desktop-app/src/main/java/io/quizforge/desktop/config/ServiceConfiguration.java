package io.quizforge.desktop.config;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.port.AiProviderConfigRepository;
import io.quizforge.core.port.AiProviderResolver;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.CredentialStore;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.port.WorkspaceFileOperations;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.service.WorkspaceFileService;
import io.quizforge.core.workspace.service.WorkspaceService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ServiceConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public WorkspaceService workspaceService(WorkspaceRepository repository,
            WorkspaceDirectoryStorage directories, Clock clock) {
        return new WorkspaceService(repository, directories, clock);
    }

    @Bean
    public AiSettingsService aiSettingsService(AiProviderConfigRepository repository,
            CredentialStore credentials, Clock clock) {
        return new AiSettingsService(repository, credentials, clock);
    }

    @Bean
    public AiConnectionService aiConnectionService(AiProviderResolver resolver) {
        return new AiConnectionService(resolver);
    }

    @Bean
    public MarkdownFileEditService markdownFileEditService(WorkspaceService workspaces,
            FileDocumentStorage files, WorkspaceAssetScanner scanner) {
        return new MarkdownFileEditService(workspaces, files, scanner);
    }

    @Bean
    public QuestionBankFileEditService questionBankFileEditService(WorkspaceService workspaces,
            QuestionBankFileStorage files, QuestionBankFileCodec codec,
            WorkspaceAssetScanner scanner, DocumentNodeLookup nodes) {
        return new QuestionBankFileEditService(workspaces, files, codec, scanner, nodes);
    }

    @Bean
    public io.quizforge.core.question.source.QuestionSourceLinkService questionSourceLinkService(
            AssetIndexRepository index, DocumentNodeLookup nodes) {
        return new io.quizforge.core.question.source.QuestionSourceLinkService(index, nodes);
    }

    @Bean
    public WorkspaceFileService workspaceFileService(WorkspaceService workspaces,
            WorkspaceAssetScanner scanner, WorkspaceFileCatalog catalog, QuestionBankFileCodec banks,
            WorkspaceFileOperations operations) {
        return new WorkspaceFileService(workspaces, scanner, catalog, banks, operations);
    }

    @Bean
    public QuestionBankReferenceResolver questionBankReferenceResolver(WorkspaceAssetScanner scanner,
            DocumentNodeLookup nodes) {
        return new QuestionBankReferenceResolver(scanner, nodes);
    }
}
