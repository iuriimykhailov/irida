package ca.corefacility.bioinformatics.irida.database.changesets;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import ca.corefacility.bioinformatics.irida.config.data.IridaApiJdbcDataSourceConfig.ApplicationContextAwareSpringLiquibase.ApplicationContextSpringResourceOpener;
import ca.corefacility.bioinformatics.irida.model.project.ReferenceFile;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequenceFile;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequenceFileSnapshot;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.repositories.analysis.AnalysisOutputFileRepository;
import ca.corefacility.bioinformatics.irida.repositories.referencefile.ReferenceFileRepository;
import ca.corefacility.bioinformatics.irida.repositories.sequencefile.SequenceFileRepository;
import ca.corefacility.bioinformatics.irida.repositories.sequencefile.SequenceFileSnapshotRepository;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawSqlStatement;

public class AbsoluteToRelativePaths implements CustomSqlChange {

	private static final Logger logger = LoggerFactory.getLogger(AbsoluteToRelativePaths.class);
	private Path sequenceFileDirectory;
	private Path referenceFileDirectory;
	private Path outputFileDirectory;
	private Path snapshotFileDirectory;

	private SequenceFileRepository sequenceFileRepository;
	private ReferenceFileRepository referenceFileRepository;
	private AnalysisOutputFileRepository outputFileRepository;
	private SequenceFileSnapshotRepository sequenceFileSnapshotRepository;

	@Override
	public String getConfirmationMessage() {
		return "Absolute paths transformed to relative paths.";
	}

	@Override
	public void setUp() throws SetupException {
		logger.info("Setting up absolute to relative paths changeset.");
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
		logger.info("The resource accessor is of type [" + resourceAccessor.getClass() + "]");
		final ApplicationContext applicationContext;
		if (resourceAccessor instanceof ApplicationContextSpringResourceOpener) {
			applicationContext = ((ApplicationContextSpringResourceOpener) resourceAccessor).getApplicationContext();
		} else {
			applicationContext = null;
		}

		if (applicationContext != null) {
			logger.info("We're running inside of a spring instance, getting the existing application context.");
			this.sequenceFileDirectory = applicationContext.getBean("sequenceFileBaseDirectory", Path.class);
			this.referenceFileDirectory = applicationContext.getBean("referenceFileBaseDirectory", Path.class);
			this.outputFileDirectory = applicationContext.getBean("outputFileBaseDirectory", Path.class);
			this.snapshotFileDirectory = applicationContext.getBean("snapshotFileBaseDirectory", Path.class);

			this.sequenceFileRepository = applicationContext.getBean(SequenceFileRepository.class);
			this.referenceFileRepository = applicationContext.getBean(ReferenceFileRepository.class);
			this.outputFileRepository = applicationContext.getBean(AnalysisOutputFileRepository.class);
			this.sequenceFileSnapshotRepository = applicationContext.getBean(SequenceFileSnapshotRepository.class);
		} else {
			logger.error("This changeset *must* be run from a servlet container as it requires access to Spring's application context.");
			throw new IllegalStateException("This changeset *must* be run from a servlet container as it requires access to Spring's application context.");
		}
	}

	@Override
	public ValidationErrors validate(Database database) {
		final ValidationErrors validationErrors = new ValidationErrors();

		final Iterable<SequenceFile> sequenceFiles = sequenceFileRepository.findAll();
		for (final SequenceFile sf : sequenceFiles) {
			if (!sf.getFile().startsWith(this.sequenceFileDirectory)) {
				validationErrors.addError("Sequence file with id [" + sf.getId() + "] with path ["
						+ sf.getFile().toString() + "] is not under path specified in /etc/irida/irida.conf ["
						+ this.sequenceFileDirectory.toString()
						+ "]; please confirm that you've specified the correct directory in /etc/irida/irida.conf.");
				break;
			}
		}

		final Iterable<ReferenceFile> referenceFiles = referenceFileRepository.findAll();
		for (final ReferenceFile rf : referenceFiles) {
			if (!rf.getFile().startsWith(this.referenceFileDirectory)) {
				validationErrors.addError("Reference file with id [" + rf.getId() + "] with path ["
						+ rf.getFile().toString() + "] is not under path specified in /etc/irida/irida.conf ["
						+ this.referenceFileDirectory.toString()
						+ "]; please confirm that you've specified the correct directory in /etc/irida/irida.conf.");
				break;
			}
		}

		final Iterable<AnalysisOutputFile> outputFiles = outputFileRepository.findAll();
		for (final AnalysisOutputFile of : outputFiles) {
			if (!of.getFile().startsWith(this.outputFileDirectory)) {
				validationErrors.addError("Output file with id [" + of.getId() + "] with path ["
						+ of.getFile().toString() + "] is not under path specified in /etc/irida/irida.conf ["
						+ this.outputFileDirectory.toString()
						+ "]; please confirm that you've specified the correct directory in /etc/irida/irida.conf.");
				break;
			}
		}

		final Iterable<SequenceFileSnapshot> snapshotFiles = sequenceFileSnapshotRepository.findAll();
		for (final SequenceFileSnapshot sf : snapshotFiles) {
			if (!sf.getFile().startsWith(this.snapshotFileDirectory)) {
				validationErrors.addError("Output file with id [" + sf.getId() + "] with path ["
						+ sf.getFile().toString() + "] is not under path specified in /etc/irida/irida.conf ["
						+ this.snapshotFileDirectory.toString()
						+ "]; please confirm that you've specified the correct directory in /etc/irida/irida.conf.");
				break;
			}
		}

		return validationErrors;
	}

	@Override
	public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
		// for each type of directory and file-class, go through and strip out
		// the prefix in the database.

		final String sequenceFileDirectoryPath = appendPathSeparator(this.sequenceFileDirectory.toString());
		final String referenceFileDirectoryPath = appendPathSeparator(this.referenceFileDirectory.toString());
		final String outputFileDirectoryPath = appendPathSeparator(this.outputFileDirectory.toString());
		final String snapshotFileDirectoryPath = appendPathSeparator(this.snapshotFileDirectory.toString());

		return new SqlStatement[] {
				new RawSqlStatement(String.format("update sequence_file set file_path = replace(file_path, '%s', '')",
						sequenceFileDirectoryPath)),
				new RawSqlStatement(String.format("update reference_file set filePath = replace(filePath, '%s', '')",
						referenceFileDirectoryPath)),
				new RawSqlStatement(
						String.format("update analysis_output_file set file_path = replace(file_path, '%s', '')",
								outputFileDirectoryPath)),
				new RawSqlStatement(
						String.format("update remote_sequence_file set file_path = replace(file_path, '%s', '')",
								snapshotFileDirectoryPath)) };
	}

	// make sure that we've got trailing path separators so that the paths in
	// the database are actually
	// relative, i.e., we're translating /sequencefiles/1/2/file.fastq to
	// 1/2/file.fastq, not /1/2/file.fastq
	private static String appendPathSeparator(final String path) {
		final String pathSeparator = FileSystems.getDefault().getSeparator();
		if (!path.endsWith(pathSeparator)) {
			return path + pathSeparator;
		} else {
			return path;
		}
	}
}