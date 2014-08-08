package ca.corefacility.bioinformatics.irida.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import ca.corefacility.bioinformatics.irida.config.data.DataConfig;
import ca.corefacility.bioinformatics.irida.config.data.IridaApiJdbcDataSourceConfig;
import ca.corefacility.bioinformatics.irida.repositories.relational.auditing.UserRevListener;

/**
 * Configuration for repository/data storage classes.
 * 
 * @author Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>
 * 
 */
@Configuration
@EnableTransactionManagement(order = 1000)
@EnableJpaRepositories(basePackages = "ca.corefacility.bioinformatics.irida.repositories", repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class)
@Import({ IridaApiPropertyPlaceholderConfig.class, IridaApiJdbcDataSourceConfig.class })
public class IridaApiRepositoriesConfig {
	@Autowired
	private DataConfig dataConfig;

	private @Value("${sequence.file.base.directory}") String sequenceFileBaseDirectory;

	private @Value("${reference.file.base.directory}") String referenceFileBaseDirectory;

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
			JpaVendorAdapter jpaVendorAdapter) {
		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setJpaVendorAdapter(jpaVendorAdapter);
		factory.setJpaProperties(dataConfig.getJpaProperties());
		factory.setPackagesToScan("ca.corefacility.bioinformatics.irida.model",
				"ca.corefacility.bioinformatics.irida.repositories.relational.auditing");

		return factory;
	}

	@Bean
	public PlatformTransactionManager transactionManager() {
		return new JpaTransactionManager();
	}

	@Bean(name = "referenceFileBaseDirectory")
	public Path referenceFileBaseDirectory() throws IOException {
		Path baseDirectory = Paths.get(referenceFileBaseDirectory);
		if (!Files.exists(baseDirectory)) {
			throw new IllegalStateException("Cannot continue startup; base directory [" + baseDirectory
					+ "] does not exist!");
		}
		return baseDirectory;
	}

	@Bean(name = "sequenceFileBaseDirectory")
	public Path sequenceFileBaseDirectory() throws IOException {
		Path baseDirectory = Paths.get(sequenceFileBaseDirectory);
		if (!Files.exists(baseDirectory)) {
			throw new IllegalStateException("Cannot continue startup; base directory [" + baseDirectory
					+ "] does not exist!");
		}
		return baseDirectory;
	}

	@Bean(initMethod = "initialize")
	public RevisionListener revisionListener() {
		return new UserRevListener();
	}

	@Bean
	public AuditReader auditReader(EntityManagerFactory entityManagerFactory) {
		return AuditReaderFactory.get(entityManagerFactory.createEntityManager());
	}
}
