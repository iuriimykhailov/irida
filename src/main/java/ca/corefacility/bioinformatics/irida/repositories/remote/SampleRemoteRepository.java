package ca.corefacility.bioinformatics.irida.repositories.remote;

import ca.corefacility.bioinformatics.irida.model.RemoteAPI;
import ca.corefacility.bioinformatics.irida.model.remote.RemoteSample;

/**
 * Repository to read {@link RemoteSample}s from a {@link RemoteAPI}
 * 
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 *
 */
public interface SampleRemoteRepository extends RemoteRepository<RemoteSample> {

}