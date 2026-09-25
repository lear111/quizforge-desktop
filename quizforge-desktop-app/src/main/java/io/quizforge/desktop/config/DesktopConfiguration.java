package io.quizforge.desktop.config;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.port.AiProviderConfigRepository;
import io.quizforge.core.port.AiProviderResolver;
import io.quizforge.core.port.CredentialStore;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.port.StandardDocumentFileStorage;
import io.quizforge.core.port.StandardDocumentRepository;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.port.QuestionBankRepository;
import io.quizforge.core.question.QuestionGenerationService;
import io.quizforge.core.question.QuestionValidator;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.desktop.ui.DesktopView;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.DocumentStructureParser;
import io.quizforge.extension.question.QuestionGenerator;
import io.quizforge.extensions.ai.deepseek.DeepSeekAiProvider;
import io.quizforge.extensions.document.standardmd.StandardMarkdownV1Processor;
import io.quizforge.extensions.document.standardmd.StandardMarkdownV1Validator;
import io.quizforge.extensions.question.choice.DefaultChoiceQuestionGenerator;
import io.quizforge.infrastructure.filesystem.LocalMaterialFileStorage;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteAiProviderConfigRepository;
import io.quizforge.infrastructure.persistence.SqliteMaterialRepository;
import io.quizforge.infrastructure.persistence.SqliteStandardDocumentRepository;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import io.quizforge.infrastructure.persistence.SqliteQuestionBankRepository;
import io.quizforge.infrastructure.security.WindowsDpapiCredentialStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DesktopConfiguration {
    private static final long DEFAULT_MAX_MATERIAL_BYTES = 10L * 1024 * 1024;
    private static final int DEFAULT_MAX_DOCUMENT_INPUT_CHARS = 100_000;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public QuizForgeDataDirectory dataDirectory() {
        return QuizForgeDataDirectory.defaultDirectory();
    }

    @Bean
    public SqliteDatabase database(QuizForgeDataDirectory directory) {
        return new SqliteDatabase(directory);
    }

    @Bean
    public WorkspaceRepository workspaceRepository(SqliteDatabase database) {
        return new SqliteWorkspaceRepository(database);
    }

    @Bean
    public MaterialRepository materialRepository(SqliteDatabase database) {
        return new SqliteMaterialRepository(database);
    }

    @Bean
    public MaterialFileStorage materialFileStorage(QuizForgeDataDirectory directory) {
        return new LocalMaterialFileStorage(new WorkspacePathResolver(directory));
    }

    @Bean
    public WorkspaceDirectoryStorage workspaceDirectoryStorage(QuizForgeDataDirectory directory) {
        return new WorkspacePathResolver(directory);
    }

    @Bean
    public WorkspaceService workspaceService(WorkspaceRepository repository,
            WorkspaceDirectoryStorage directories, Clock clock) {
        return new WorkspaceService(repository, directories, clock);
    }

    @Bean
    public MaterialService materialService(WorkspaceService workspaces, MaterialRepository repository,
            MaterialFileStorage storage, Clock clock) {
        long maxBytes = Long.getLong("quizforge.material.maxBytes", DEFAULT_MAX_MATERIAL_BYTES);
        return new MaterialService(workspaces, repository, storage, clock, maxBytes);
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
    public AiSettingsService aiSettingsService(AiProviderConfigRepository repository,
            CredentialStore credentials, Clock clock) {
        return new AiSettingsService(repository, credentials, clock);
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
    public AiConnectionService aiConnectionService(AiProviderResolver resolver) {
        return new AiConnectionService(resolver);
    }

    @Bean
    public StandardDocumentRepository standardDocumentRepository(SqliteDatabase database) {
        return new SqliteStandardDocumentRepository(database);
    }

    @Bean
    public StandardDocumentFileStorage standardDocumentFileStorage(QuizForgeDataDirectory directory) {
        return new LocalStandardDocumentFileStorage(directory);
    }

    @Bean
    public DocumentProcessor documentProcessor() {
        return new StandardMarkdownV1Processor();
    }

    @Bean
    public StandardMarkdownV1Validator documentValidator() {
        return new StandardMarkdownV1Validator();
    }

    @Bean
    public QuestionGenerator questionGenerator(AiProviderResolver providers) {
        return new DefaultChoiceQuestionGenerator(providers::resolve);
    }

    @Bean
    public QuestionValidator questionValidator() {
        return new QuestionValidator();
    }

    @Bean
    public QuestionBankRepository questionBankRepository(SqliteDatabase database) {
        return new SqliteQuestionBankRepository(database);
    }

    @Bean
    public QuestionGenerationService questionGenerationService(WorkspaceService workspaces,
            DocumentNormalizationService documents, DocumentStructureParser structureParser,
            QuestionGenerator generator, QuestionValidator validator, QuestionBankRepository banks,
            Clock clock) {
        return new QuestionGenerationService(workspaces, documents, structureParser,
                generator, validator, banks, clock);
    }

    @Bean
    public DocumentNormalizationService documentNormalizationService(WorkspaceService workspaces,
            MaterialRepository materials, MaterialFileStorage materialFiles,
            StandardDocumentRepository documents, StandardDocumentFileStorage documentFiles,
            AiProviderResolver providers, DocumentProcessor processor, DocumentValidator validator,
            Clock clock) {
        int maxChars = Integer.getInteger("quizforge.document.maxInputChars",
                DEFAULT_MAX_DOCUMENT_INPUT_CHARS);
        return new DocumentNormalizationService(workspaces, materials, materialFiles, documents,
                documentFiles, providers, processor, validator, clock, maxChars);
    }

    @Bean
    public DesktopView desktopView(WorkspaceService workspaces, MaterialService materials,
            DocumentNormalizationService documents, AiSettingsService settings,
            AiConnectionService connections, QuestionGenerationService questions) {
        return new DesktopView(workspaces, materials, documents, settings, connections, questions);
    }
}
