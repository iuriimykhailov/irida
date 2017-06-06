package ca.corefacility.bioinformatics.irida.service.remote.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.hateoas.Link;

import com.google.common.collect.Lists;

import ca.corefacility.bioinformatics.irida.model.RemoteAPI;
import ca.corefacility.bioinformatics.irida.model.project.Project;
import ca.corefacility.bioinformatics.irida.model.remote.RemoteStatus;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.repositories.RemoteAPIRepository;
import ca.corefacility.bioinformatics.irida.repositories.remote.SampleRemoteRepository;
import ca.corefacility.bioinformatics.irida.repositories.remote.SequenceFilePairRemoteRepository;
import ca.corefacility.bioinformatics.irida.repositories.remote.SingleEndSequenceFileRemoteRepository;
import ca.corefacility.bioinformatics.irida.service.remote.SampleRemoteService;

public class SampleRemoteServiceImplTest {
	private SampleRemoteService sampleRemoteService;
	private SampleRemoteRepository sampleRemoteRepository;
	private RemoteAPIRepository apiRepo;
	private SequenceFilePairRemoteRepository pairRemoteRepository;
	private SingleEndSequenceFileRemoteRepository unpairedRemoteRepository;

	@Before
	public void setUp() {
		sampleRemoteRepository = mock(SampleRemoteRepository.class);
		apiRepo = mock(RemoteAPIRepository.class);
		sampleRemoteService = new SampleRemoteServiceImpl(sampleRemoteRepository, pairRemoteRepository,
				unpairedRemoteRepository, apiRepo);
	}

	@Test
	public void testGetSamplesForProject() {
		String samplesHref = "http://somewhere/projects/5/samples";
		Project project = new Project();
		project.add(new Link(samplesHref, SampleRemoteServiceImpl.PROJECT_SAMPLES_REL));
		RemoteAPI api = new RemoteAPI();
		project.setRemoteStatus(new RemoteStatus("http://nowhere", api));

		Sample remoteSample = new Sample();
		remoteSample.setRemoteStatus(new RemoteStatus("http://nowhere", api));
		List<Sample> samples = Lists.newArrayList(remoteSample);

		when(sampleRemoteRepository.list(samplesHref, api)).thenReturn(samples);

		List<Sample> samplesForProject = sampleRemoteService.getSamplesForProject(project);

		verify(sampleRemoteRepository).list(samplesHref, api);
		assertEquals(samples, samplesForProject);
	}
}
