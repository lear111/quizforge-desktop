package io.quizforge.desktop.config;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.port.AiProviderConfigRepository;
import io.quizforge.core.port.AiProviderResolver;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.CredentialStore;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.port.WorkspaceFileOperations;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.infrastructure.ai.DeepSeekAiProvider;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.markdown.FileDocumentNodeLookup;
import io.quizforge.infrastructure.filesystem.markdown.LocalMarkdownFileStorage;
import io.quizforge.infrastructure.filesystem.markdown.MarkdownDocumentRegistrationService;
import io.quizforge.infrastructure.filesystem.qbank.LocalQuestionBankFileStorage;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.workspace.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileCatalog;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileOperations;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAiProviderConfigRepository;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import io.quizforge.infrastructure.security.WindowsDpapiCredentialStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfiguration {

    @Bean
    public QuizForgeDataDirectory dataDirectory() {
        return QuizForgeDataDirectory.defaultDirectory();
    }

    @Bean
    public SqliteDatabase database(QuizForgeDataDirectory directory) {
        return new SqliteDatabase(directory);
    }

    @Bean
    public io.quizforge.core.port.PracticeRuntimeProvider practiceRuntimeProvider(WorkspacePathResolver paths,
            QuestionBankFileCodec codec, Clock clock) {
        return new io.quizforge.infrastructure.persistence.practice.SqliteWorkspacePracticeRuntimeProvider(
                paths, codec, clock);
    }

    @Bean
    public WorkspaceRepository workspaceRepository(SqliteDatabase database) {
        return new SqliteWorkspaceRepository(database);
    }

    @Bean
    public WorkspacePathResolver workspacePathResolver(QuizForgeDataDirectory directory,
            WorkspaceRepository repository) {
        return new WorkspacePathResolver(directory, repository);
    }

    @Bean
    public AssetIndexRepository assetIndexRepository(WorkspacePathResolver paths) {
        return new SqliteAssetIndexRepository(paths);
    }

    @Bean
    public WorkspaceAssetScanner workspaceAssetScanner(WorkspacePathResolver paths,
            AssetIndexRepository index, Clock clock) {
        return new FileSystemWorkspaceAssetScanner(paths, index, clock);
    }

    @Bean
    public AiProviderConfigRepository aiProviderConfigRepository(SqliteDatabase database) {
        return new SqliteAiProviderConfigRepository(database);
    }

    @Bean
    public CredentialStore credentialStore(QuizForgeDataDirectory directory) {
        return new WindowsDpapiCredentialStore(directory);
    }

    @Bean
    public AiProviderResolver aiProviderResolver(AiSettingsService settings,
            CredentialStore credentials) {
        return () -> {
            var config = settings.configuration().orElseThrow(() ->
                    new QuizForgeException(ErrorCode.AI_PROVIDER_NOT_CONFIGURED,
                            "Configure an AI Provider first."));
            String key = credentials.get(config.credentialRef()).orElseThrow(() ->
                    new QuizForgeException(ErrorCode.AI_PROVIDER_CREDENTIAL_MISSING,
                            "Configure an API key first."));
            if (!DeepSeekAiProvider.ID.equals(config.providerType())) {
                throw new QuizForgeException(ErrorCode.AI_PROVIDER_NOT_CONFIGURED,
                        "Unsupported AI Provider.");
            }
            return new DeepSeekAiProvider(config.baseUrl(), config.model(), key);
        };
    }

    @Bean
    public LocalMarkdownFileStorage markdownFileStorage(WorkspacePathResolver paths) {
        return new LocalMarkdownFileStorage(paths);
    }

    @Bean
    public QuestionBankFileCodec questionBankFileCodec() { return new QuestionBankV2Codec(); }

    @Bean
    public QuestionBankFileStorage questionBankFileStorage(WorkspacePathResolver paths) {
        return new LocalQuestionBankFileStorage(paths);
    }

    @Bean
    public WorkspaceFileCatalog workspaceFileCatalog(WorkspacePathResolver paths,
            QuestionBankFileCodec banks) {
        return new LocalWorkspaceFileCatalog(paths, banks);
    }

    @Bean
    public MarkdownDocumentRegistration markdownDocumentRegistration(WorkspacePathResolver paths,
            WorkspaceAssetScanner scanner) {
        return new MarkdownDocumentRegistrationService(paths, scanner);
    }

    @Bean
    public WorkspaceFileOperations workspaceFileOperations(WorkspacePathResolver paths) {
        return new LocalWorkspaceFileOperations(paths);
    }

    @Bean
    public DocumentNodeLookup documentNodeLookup(WorkspaceFileCatalog catalog) {
        return new FileDocumentNodeLookup(catalog);
    }
}
