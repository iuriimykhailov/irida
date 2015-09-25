package ca.corefacility.bioinformatics.irida.config.services;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.oxm.Unmarshaller;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextScheduledExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import ca.corefacility.bioinformatics.irida.config.analysis.AnalysisExecutionServiceConfig;
import ca.corefacility.bioinformatics.irida.config.analysis.ExecutionManagerConfig;
import ca.corefacility.bioinformatics.irida.config.repository.ForbidJpqlUpdateDeletePostProcessor;
import ca.corefacility.bioinformatics.irida.config.repository.IridaApiRepositoriesConfig;
import ca.corefacility.bioinformatics.irida.config.security.IridaApiSecurityConfig;
import ca.corefacility.bioinformatics.irida.config.workflow.IridaWorkflowsConfig;
import ca.corefacility.bioinformatics.irida.model.user.Role;
import ca.corefacility.bioinformatics.irida.model.user.User;
import ca.corefacility.bioinformatics.irida.processing.FileProcessingChain;
import ca.corefacility.bioinformatics.irida.processing.FileProcessor;
import ca.corefacility.bioinformatics.irida.processing.impl.DefaultFileProcessingChain;
import ca.corefacility.bioinformatics.irida.processing.impl.FastqcFileProcessor;
import ca.corefacility.bioinformatics.irida.processing.impl.GzipFileProcessor;
import ca.corefacility.bioinformatics.irida.repositories.analysis.submission.AnalysisSubmissionRepository;
import ca.corefacility.bioinformatics.irida.repositories.sequencefile.SequenceFileRepository;
import ca.corefacility.bioinformatics.irida.service.AnalysisSubmissionCleanupService;
import ca.corefacility.bioinformatics.irida.service.TaxonomyService;
import ca.corefacility.bioinformatics.irida.service.impl.InMemoryTaxonomyService;
import ca.corefacility.bioinformatics.irida.service.impl.analysis.submission.AnalysisSubmissionCleanupServiceImpl;
import ca.corefacility.bioinformatics.irida.service.user.UserService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Configuration for the IRIDA platform.
 * 
 * 
 */
@Configuration
@Import({ IridaApiSecurityConfig.class, IridaApiAspectsConfig.class, IridaApiRepositoriesConfig.class,
		ExecutionManagerConfig.class, AnalysisExecutionServiceConfig.class,
		IridaCachingConfig.class, IridaWorkflowsConfig.class, WebEmailConfig.class })
@ComponentScan(basePackages = "ca.corefacility.bioinformatics.irida.service")
public class IridaApiServicesConfig {
	private static final Logger logger = LoggerFactory.getLogger(IridaApiServicesConfig.class);
	
	private static final String DEFAULT_ENCODING = "UTF-8";
	private static final String[] RESOURCE_LOCATIONS = { "classpath:/i18n/messages", "classpath:/i18n/mobile" };
	
	@Autowired
	private Environment env;

	@Value("${taxonomy.location}")
	private ClassPathResource taxonomyFileLocation;

	@Value("${file.processing.decompress}")
	private Boolean decompressFiles;

	@Value("${file.processing.decompress.remove.compressed.file}")
	private Boolean removeCompressedFiles;
	
	@Bean
	public BeanPostProcessor forbidJpqlUpdateDeletePostProcessor() {
		return new ForbidJpqlUpdateDeletePostProcessor();
	}

	@Bean
	public MessageSource messageSource() {
		logger.info("Configuring ReloadableResourceBundleMessageSource.");

		ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
		source.setBasenames(RESOURCE_LOCATIONS);
		source.setFallbackToSystemLocale(false);
		source.setDefaultEncoding(DEFAULT_ENCODING);

		// Set template cache timeout if in production
		// Don't cache at all if in development
		if (!env.acceptsProfiles("prod")) {
			source.setCacheSeconds(0);
		}

		return source;
	}

	@Bean
	public FileProcessingChain fileProcessorChain(SequenceFileRepository sequenceFileRepository) {
		final FileProcessor gzipFileProcessor = new GzipFileProcessor(sequenceFileRepository, removeCompressedFiles);
		final FileProcessor fastQcFileProcessor = new FastqcFileProcessor(messageSource(), sequenceFileRepository);
		final List<FileProcessor> fileProcessors = Lists.newArrayList(gzipFileProcessor, fastQcFileProcessor);

		if (!decompressFiles) {
			logger.info("File decompression is disabled [file.processing.decompress=false]");
			fileProcessors.remove(gzipFileProcessor);
		}

		return new DefaultFileProcessingChain(sequenceFileRepository, fileProcessors);
	}

	@Bean(name = "fileProcessingChainExecutor")
	@Profile({ "dev", "prod" })
	public TaskExecutor fileProcessingChainExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(16);
		taskExecutor.setMaxPoolSize(48);
		taskExecutor.setQueueCapacity(100);
		taskExecutor.setThreadPriority(Thread.MIN_PRIORITY);
		return taskExecutor;
	}

	@Bean(name = "fileProcessingChainExecutor")
	@Profile({ "it", "test" })
	public TaskExecutor fileProcessingChainExecutorIntegrationTest() {
		return new SyncTaskExecutor();
	}

	@Bean
	public Validator validator() {
		ResourceBundleMessageSource validatorMessageSource = new ResourceBundleMessageSource();
		validatorMessageSource.setBasename("ValidationMessages");
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.setValidationMessageSource(validatorMessageSource);
		return validator;
	}

	@Bean
	public TaxonomyService taxonomyService() throws URISyntaxException {
		Path path = Paths.get(taxonomyFileLocation.getPath());
		return new InMemoryTaxonomyService(path);
	}

	/**
	 * Builds a new {@link Executor} for analysis tasks.
	 * 
	 * @param userService
	 *            a reference to the user service.
	 * 
	 * @return A new {@link Executor} for analysis tasks.
	 */
	@Bean
	@DependsOn("springLiquibase")
	@Profile({"prod", "dev"})
	public Executor analysisTaskExecutor(UserService userService) {
		ScheduledExecutorService delegateExecutor = Executors.newScheduledThreadPool(4);
		SecurityContext schedulerContext = createAnalysisTaskSecurityContext(userService);
		return new DelegatingSecurityContextScheduledExecutorService(delegateExecutor, schedulerContext);
	}
	
	@Bean
	@DependsOn("springLiquibase")
	@Profile({ "prod" })
	public AnalysisSubmissionCleanupService analysisSubmissionCleanupService(
			AnalysisSubmissionRepository analysisSubmissionRepository, UserService userService) {
		AnalysisSubmissionCleanupService analysisSubmissionCleanupService = new AnalysisSubmissionCleanupServiceImpl(
				analysisSubmissionRepository);
		SecurityContext adminContext = createAnalysisTaskSecurityContext(userService);

		// Run method to clean up previous analysis submissions in inconsistent
		// states.
		SecurityContext oldContext = SecurityContextHolder.getContext();
		SecurityContextHolder.setContext(adminContext);
		analysisSubmissionCleanupService.switchInconsistentSubmissionsToError();
		SecurityContextHolder.setContext(oldContext);

		return analysisSubmissionCleanupService;
	}

	/**
	 * Creates a security context object for the analysis tasks.
	 * 
	 * @param userService
	 *            A {@link UserService}.
	 * 
	 * @return A {@link SecurityContext} for the analysis tasks.
	 */
	private SecurityContext createAnalysisTaskSecurityContext(UserService userService) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();

		Authentication anonymousToken = new AnonymousAuthenticationToken("nobody", "nobody",
				ImmutableList.of(Role.ROLE_ANONYMOUS));

		Authentication oldAuthentication = SecurityContextHolder.getContext().getAuthentication();
		SecurityContextHolder.getContext().setAuthentication(anonymousToken);
		User admin = userService.getUserByUsername("admin");
		SecurityContextHolder.getContext().setAuthentication(oldAuthentication);

		Authentication adminAuthentication = new PreAuthenticatedAuthenticationToken(admin, null,
				Lists.newArrayList(Role.ROLE_ADMIN));

		context.setAuthentication(adminAuthentication);

		return context;
	}

	/**
	 * @return An Executor for handling uploads to Galaxy.
	 */
	@Bean
	public Executor uploadExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(4);
		taskExecutor.setMaxPoolSize(8);
		taskExecutor.setQueueCapacity(16);
		taskExecutor.setThreadPriority(Thread.MIN_PRIORITY);
		return taskExecutor;
	}

	@Bean
	public Unmarshaller workflowDescriptionUnmarshaller() {
		Jaxb2Marshaller jaxb2marshaller = new Jaxb2Marshaller();
		jaxb2marshaller.setPackagesToScan(new String[] { "ca.corefacility.bioinformatics.irida.model.workflow" });
		return jaxb2marshaller;
	}
}
