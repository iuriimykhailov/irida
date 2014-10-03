package ca.corefacility.bioinformatics.irida.service.analysis.execution.galaxy.phylogenonmics.impl.unit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.ExecutionManagerException;
import ca.corefacility.bioinformatics.irida.exceptions.WorkflowException;
import ca.corefacility.bioinformatics.irida.exceptions.galaxy.WorkflowChecksumInvalidException;
import ca.corefacility.bioinformatics.irida.model.enums.AnalysisState;
import ca.corefacility.bioinformatics.irida.model.workflow.WorkflowState;
import ca.corefacility.bioinformatics.irida.model.workflow.WorkflowStatus;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisPhylogenomicsPipeline;
import ca.corefacility.bioinformatics.irida.model.workflow.galaxy.PreparedWorkflowGalaxy;
import ca.corefacility.bioinformatics.irida.model.workflow.galaxy.WorkflowInputsGalaxy;
import ca.corefacility.bioinformatics.irida.model.workflow.galaxy.phylogenomics.RemoteWorkflowPhylogenomics;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.galaxy.phylogenomics.AnalysisSubmissionPhylogenomics;
import ca.corefacility.bioinformatics.irida.pipeline.upload.galaxy.GalaxyHistoriesService;
import ca.corefacility.bioinformatics.irida.pipeline.upload.galaxy.GalaxyWorkflowService;
import ca.corefacility.bioinformatics.irida.service.AnalysisService;
import ca.corefacility.bioinformatics.irida.service.AnalysisSubmissionService;
import ca.corefacility.bioinformatics.irida.service.analysis.execution.galaxy.phylogenomics.impl.AnalysisExecutionServicePhylogenomics;
import ca.corefacility.bioinformatics.irida.service.analysis.workspace.galaxy.phylogenomics.impl.WorkspaceServicePhylogenomics;

import com.github.jmchilton.blend4j.galaxy.beans.WorkflowInputs;
import com.github.jmchilton.blend4j.galaxy.beans.WorkflowOutputs;
import com.google.common.collect.ImmutableMap;

/**
 * Tests out an execution service for phylogenomics analyses.
 * @author Aaron Petkau <aaron.petkau@phac-aspc.gc.ca>
 *
 */
public class AnalysisExecutionServicePhylogenomicsTest {
	
	@Mock private AnalysisSubmissionService analysisSubmissionService;
	@Mock private AnalysisService analysisService;
	@Mock private GalaxyHistoriesService galaxyHistoriesService;
	@Mock private GalaxyWorkflowService galaxyWorkflowService;
	@Mock private AnalysisSubmissionPhylogenomics analysisSubmission;
	@Mock private AnalysisSubmissionPhylogenomics analysisSubmitted;
	@Mock private WorkspaceServicePhylogenomics workspaceServicePhylogenomics;
	@Mock private WorkflowInputs workflowInputs;
	@Mock private WorkflowOutputs workflowOutputs;
	@Mock private AnalysisPhylogenomicsPipeline analysisResults;

	private static final String WORKFLOW_ID = "1";
	private static final String WORKFLOW_CHECKSUM = "1";
	private static final Long INTERNAL_ANALYSIS_ID = 2l;
	private static final String ANALYSIS_ID = "2";
	private AnalysisExecutionServicePhylogenomics workflowManagement;
	private PreparedWorkflowGalaxy preparedWorkflow;
	private String analysisId;
	private WorkflowInputsGalaxy workflowInputsGalaxy;
	
	private static final String TREE_LABEL = "tree";
	private static final String MATRIX_LABEL = "snp_matrix";
	private static final String TABLE_LABEL = "snp_table";
	
	private Map<String,Object> analysisIdMap;

	/**
	 * Setup variables for tests.
	 * @throws WorkflowException 
	 */
	@Before
	public void setup() throws WorkflowException {
		MockitoAnnotations.initMocks(this);
		
		workflowManagement = new AnalysisExecutionServicePhylogenomics(analysisSubmissionService,
				analysisService, galaxyWorkflowService, galaxyHistoriesService,
				workspaceServicePhylogenomics);
		
		RemoteWorkflowPhylogenomics remoteWorkflow = 
				new RemoteWorkflowPhylogenomics(WORKFLOW_ID, WORKFLOW_CHECKSUM, "1", "1",
						TREE_LABEL, MATRIX_LABEL, TABLE_LABEL);
		
		when(analysisSubmission.getRemoteWorkflow()).thenReturn(remoteWorkflow);
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(ANALYSIS_ID);
		
		when(analysisSubmissionService.create(analysisSubmission)).thenReturn(analysisSubmission);
		
		analysisId = "1";
		workflowInputsGalaxy = new WorkflowInputsGalaxy(workflowInputs);
		preparedWorkflow = new PreparedWorkflowGalaxy(analysisId, workflowInputsGalaxy);
		
		analysisIdMap = ImmutableMap.of("remoteAnalysisId", ANALYSIS_ID);
	}
	
	/**
	 * Tests successfully preparing an analysis submission.
	 * @throws ExecutionManagerException
	 */
	@Test
	public void testPrepareSubmissionSuccess() throws ExecutionManagerException {
		
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(null);
		when(analysisSubmission.getId()).thenReturn(INTERNAL_ANALYSIS_ID);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.PREPARING);
		
		when(analysisSubmitted.getRemoteAnalysisId()).thenReturn(ANALYSIS_ID);
		when(analysisSubmitted.getId()).thenReturn(INTERNAL_ANALYSIS_ID);
		when(analysisSubmitted.getAnalysisState()).thenReturn(AnalysisState.PREPARING);
		
		when(analysisSubmissionService.update(INTERNAL_ANALYSIS_ID, analysisIdMap)).thenReturn(analysisSubmitted);
		when(galaxyWorkflowService.validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID)).
			thenReturn(true);
		when(workspaceServicePhylogenomics.prepareAnalysisWorkspace(analysisSubmission)).
			thenReturn(ANALYSIS_ID);
		
		AnalysisSubmissionPhylogenomics returnedSubmission = 
				workflowManagement.prepareSubmission(analysisSubmission);
		
		assertEquals("analysisSubmission not equal to returned submission", analysisSubmitted, returnedSubmission);
		
		verify(galaxyWorkflowService).validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID);
		verify(workspaceServicePhylogenomics).prepareAnalysisWorkspace(analysisSubmission);
		verify(analysisSubmissionService).update(INTERNAL_ANALYSIS_ID, ImmutableMap.of("remoteAnalysisId", ANALYSIS_ID));
	}
	
	/**
	 * Tests failing to prepare an analysis due to invalid workflow.
	 * @throws ExecutionManagerException
	 */
	@Test(expected=WorkflowChecksumInvalidException.class)
	public void testPrepareSubmissionFailInvalidWorkflow() throws ExecutionManagerException {
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(null);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.PREPARING);
		when(galaxyWorkflowService.validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID)).
			thenThrow(new WorkflowChecksumInvalidException());
		
		workflowManagement.prepareSubmission(analysisSubmission);
	}
	
	/**
	 * Tests failing to prepare an analysis workspace.
	 * @throws ExecutionManagerException
	 */
	@Test(expected=ExecutionManagerException.class)
	public void testPrepareSubmissionFailWorkspace() throws ExecutionManagerException {
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(null);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.PREPARING);
		when(galaxyWorkflowService.validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID)).
			thenReturn(true);
		when(workspaceServicePhylogenomics.prepareAnalysisWorkspace(analysisSubmission)).
			thenThrow(new ExecutionManagerException());
		
		workflowManagement.prepareSubmission(analysisSubmission);
	}
	
	/**
	 * Tests successfully executing an analysis.
	 * @throws ExecutionManagerException
	 */
	@Test
	public void testExecuteAnalysisSuccess() throws ExecutionManagerException {
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.SUBMITTING);
		when(workspaceServicePhylogenomics.prepareAnalysisFiles(analysisSubmission)).
			thenReturn(preparedWorkflow);
		when(analysisSubmission.getId()).thenReturn(INTERNAL_ANALYSIS_ID);
		when(galaxyWorkflowService.runWorkflow(workflowInputsGalaxy)).thenReturn(workflowOutputs);
		when(analysisSubmissionService.read(INTERNAL_ANALYSIS_ID)).thenReturn(analysisSubmission);
		
		AnalysisSubmissionPhylogenomics returnedSubmission = 
				workflowManagement.executeAnalysis(analysisSubmission);
		
		assertEquals("analysisSubmission not equal to returned submission", analysisSubmission, returnedSubmission);
		
		verify(workspaceServicePhylogenomics).prepareAnalysisFiles(analysisSubmission);
		verify(galaxyWorkflowService).runWorkflow(workflowInputsGalaxy);
	}
	
	/**
	 * Tests failing to executing an analysis due to already being submitted.
	 * @throws IllegalArgumentException
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testExecuteAnalysisFailAlreadySubmitted() throws ExecutionManagerException {
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.RUNNING);
		
		workflowManagement.executeAnalysis(analysisSubmission);
	}
	
	/**
	 * Tests failing to prepare a workflow.
	 * @throws ExecutionManagerException
	 */
	@Test(expected=ExecutionManagerException.class)
	public void testExecuteAnalysisFailPrepareWorkflow() throws ExecutionManagerException {
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.SUBMITTING);
		when(galaxyWorkflowService.validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID)).
			thenReturn(true);
		when(workspaceServicePhylogenomics.prepareAnalysisFiles(analysisSubmission)).
			thenThrow(new ExecutionManagerException());
		
		workflowManagement.executeAnalysis(analysisSubmission);
	}
	
	/**
	 * Tests failing to execute a workflow.
	 * @throws ExecutionManagerException
	 */
	@Test(expected=WorkflowException.class)
	public void testExecuteAnalysisFail() throws ExecutionManagerException {
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.SUBMITTING);
		when(galaxyWorkflowService.validateWorkflowByChecksum(WORKFLOW_CHECKSUM, WORKFLOW_ID)).
			thenReturn(true);
		when(workspaceServicePhylogenomics.prepareAnalysisFiles(analysisSubmission)).
			thenReturn(preparedWorkflow);
		when(galaxyWorkflowService.runWorkflow(workflowInputsGalaxy)).
			thenThrow(new WorkflowException());
		
		workflowManagement.executeAnalysis(analysisSubmission);
	}
	
	/**
	 * Tests out successfully getting the status of a workflow.
	 * @throws ExecutionManagerException
	 */
	@Test
	public void testGetWorkflowStatusSuccess() throws ExecutionManagerException {
		WorkflowStatus workflowStatus = new WorkflowStatus(WorkflowState.OK, 1.0f);
		
		when(galaxyHistoriesService.getStatusForHistory(
				analysisSubmission.getRemoteAnalysisId())).thenReturn(workflowStatus);
		
		assertEquals(workflowStatus, workflowManagement.getWorkflowStatus(analysisSubmission));
	}
	
	/**
	 * Tests failure to get the status of a workflow (no status).
	 * @throws ExecutionManagerException
	 */
	@Test(expected=WorkflowException.class)
	public void testGetWorkflowStatusFailNoStatus() throws ExecutionManagerException {		
		when(galaxyHistoriesService.getStatusForHistory(analysisSubmission.
				getRemoteAnalysisId())).thenThrow(new WorkflowException());
		
		workflowManagement.getWorkflowStatus(analysisSubmission);
	}
	
	/**
	 * Tests successfully getting analysis results.
	 * @throws ExecutionManagerException
	 * @throws IOException 
	 */
	@Test
	public void testGetAnalysisResultsSuccess() throws ExecutionManagerException, IOException {
		Long id = 99999l;
		String remoteAnalysisId = "invalid";
		
		when(analysisSubmission.getId()).thenReturn(id);
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(remoteAnalysisId);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.FINISHED_RUNNING);
		when(analysisSubmissionService.exists(id)).thenReturn(true);
		when(workspaceServicePhylogenomics.getAnalysisResults(eq(analysisSubmission), any(Path.class))).thenReturn(analysisResults);
		when(analysisService.create(analysisResults)).thenReturn(analysisResults);
		
		AnalysisPhylogenomicsPipeline actualResults = workflowManagement.transferAnalysisResults(analysisSubmission);
		assertEquals("analysisResults should be equal", analysisResults, actualResults);
		
		verify(analysisService).create(analysisResults);
	}
	
	/**
	 * Tests failing to get analysis results.
	 * @throws ExecutionManagerException
	 * @throws IOException 
	 */
	@Test(expected=ExecutionManagerException.class)
	public void testGetAnalysisResultsFail() throws ExecutionManagerException, IOException {
		Long id = 1l;
		String remoteAnalysisId = "invalid";
		
		when(analysisSubmission.getId()).thenReturn(id);
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(remoteAnalysisId);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.FINISHED_RUNNING);
		when(analysisSubmissionService.exists(id)).thenReturn(true);
		
		when(workspaceServicePhylogenomics.getAnalysisResults(eq(analysisSubmission), any(Path.class))).
			thenThrow(new ExecutionManagerException());
		
		workflowManagement.transferAnalysisResults(analysisSubmission);
	}
	
	/**
	 * Tests failing to get analysis results due to not being submitted (null id).
	 * @throws ExecutionManagerException
	 * @throws IOException 
	 */
	@Test(expected=NullPointerException.class)
	public void testGetAnalysisResultsFailNotSubmittedNullId() throws ExecutionManagerException, IOException {
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(null);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.FINISHED_RUNNING);
		
		workflowManagement.transferAnalysisResults(analysisSubmission);
	}
	
	/**
	 * Tests failing to get analysis results due to submission with invalid id.
	 * @throws ExecutionManagerException
	 * @throws IOException 
	 */
	@Test(expected=EntityNotFoundException.class)
	public void testGetAnalysisResultsFailAnalysisIdInvalid() throws ExecutionManagerException, IOException {
		Long id = 1l;
		String remoteAnalysisId = "invalid";
		
		when(analysisSubmission.getRemoteAnalysisId()).thenReturn(remoteAnalysisId);
		when(analysisSubmission.getAnalysisState()).thenReturn(AnalysisState.FINISHED_RUNNING);
		when(analysisSubmissionService.exists(id)).thenReturn(false);
		
		workflowManagement.transferAnalysisResults(analysisSubmission);
	}
}