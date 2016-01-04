package ca.corefacility.bioinformatics.irida.service;

import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.SampleSequencingObjectJoin;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequencingObject;

/**
 * Service for managing {@link SequencingObject}s and relationships with related
 * objects
 */
public interface SequencingObjectService extends CRUDService<Long, SequencingObject> {

	/**
	 * Create a new {@link SequencingObject} associated with a {@link Sample}
	 * 
	 * @param seqObject
	 *            The {@link SequencingObject} to create
	 * @param sample
	 *            the {@link Sample} to associate it with
	 * @return a new {@link SampleSequencingObjectJoin} describing the
	 *         relationship
	 */
	public SampleSequencingObjectJoin createSequencingObjectInSample(SequencingObject seqObject, Sample sample);
}
