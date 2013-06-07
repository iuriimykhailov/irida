/*
 * Copyright 2013 Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.corefacility.bioinformatics.irida.repositories.sesame;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.StorageException;
import ca.corefacility.bioinformatics.irida.model.Relationship;
import ca.corefacility.bioinformatics.irida.model.alibaba.IridaThing;
import ca.corefacility.bioinformatics.irida.model.enums.Order;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Audit;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Identifier;
import ca.corefacility.bioinformatics.irida.model.roles.impl.StringIdentifier;
import ca.corefacility.bioinformatics.irida.repositories.RelationshipRepository;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.DefaultLinks;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.QualifiedRDFPredicate;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.RdfPredicate;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.TripleStore;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.*;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;

/**
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 */
public class RelationshipSesameRepository extends SesameRepository implements RelationshipRepository {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RelationshipSesameRepository.class);
    public final String linkType = "http://corefacility.ca/irida/ResourceLink";
    AuditRepository auditRepo;
    DefaultLinks linkList;

    public RelationshipSesameRepository(TripleStore store, AuditRepository auditRepo) {
        super(store, "ResourceLink");
        this.auditRepo = auditRepo;
        linkList = new DefaultLinks();
    }

    /**
     * Add a default relationship to the relationship repository repository
     *
     * @param <S>     The class of the subject
     * @param <O>     The class of the object
     * @param subject The class of the subject
     * @param pred    The predicate to link the subject/object
     * @param object  The class of the object
     */
    public <S extends IridaThing, O extends IridaThing> void addRelationship(Class subject, RdfPredicate pred, Class object) {
        linkList.addLink(subject, pred, object);
    }

    /**
     * Build an identifier object from a link binding set
     *
     * @param bs          The <type>BindingSet</type> to construct the identifier from
     * @param bindingName The binding name of the subject from this binding set
     * @return A <type>StringIdentifier</type> for this binding set
     */
    private StringIdentifier buildIdentiferFromBindingSet(BindingSet bs, String bindingName) {
        StringIdentifier id = null;
        try {
            Value uri = bs.getValue(bindingName);
            Value ident = bs.getValue("identifier");
            Value label = bs.getValue("label");
            id = new StringIdentifier();
            id.setIdentifier(ident.stringValue());
            id.setUri(new java.net.URI(uri.stringValue()));
            id.setLabel(label.stringValue());
        } catch (URISyntaxException ex) {
            logger.error("A URISyntaxException occurred at [" + new Date() + "]", ex);
            throw new StorageException(
                    "Failed to build identifier from binding set: [" + bs + "], bindingName: [" + bindingName + "]");
        }

        return id;
    }

    /**
     * Build a link identifier from a given URI and identifier string
     *
     * @param uri          The URI to build from
     * @param identifiedBy The unique string for this identifier
     * @return A new instance of an Identifier
     */
    private Identifier buildLinkIdentifier(URI uri, String identifiedBy) {
        Identifier objid = new Identifier();
        objid.setUri(java.net.URI.create(uri.toString()));
        objid.setIdentifier(identifiedBy);

        return objid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <SubjectType extends IridaThing, ObjectType extends IridaThing> Relationship create(SubjectType subject, ObjectType object) {
        return create(subject.getClass(), (Identifier) subject.getIdentifier(),
                object.getClass(), (Identifier) object.getIdentifier());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship create(Class subjectType, Identifier subject, Class objectType, Identifier object) {
        RdfPredicate predicate = linkList.getLink(subjectType, objectType);
        Relationship link = new Relationship();

        link.setSubject(subject);
        link.setPredicate(predicate);
        link.setObject(object);

        return create(link);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship create(Relationship link) {
        Identifier subject = link.getSubject();
        Identifier object = link.getObject();
        RdfPredicate predicate = link.getPredicate();

        java.net.URI subNetURI = getUriFromIdentifier(subject);
        java.net.URI objNetURI = getUriFromIdentifier(object);

        ObjectConnection con = store.getRepoConnection();
        ValueFactory fac = con.getValueFactory();

        try {
            con.begin();
            URI subURI = fac.createURI(subNetURI.toString());
            URI objURI = fac.createURI(objNetURI.toString());
            URI pred = predicate.getPredicateURI(con);

            Identifier identifier = generateNewIdentifier();
            link.setIdentifier(identifier);

            java.net.URI netURI = identifier.getUri();
            URI linkURI = fac.createURI(netURI.toString());

            Statement st = fac.createStatement(subURI, pred, objURI);
            con.add(st);

            URI rdftype = fac.createURI(con.getNamespace("rdf"), "type");
            URI type = fac.createURI(linkType);
            setIdentifiedBy(con, linkURI, identifier.getIdentifier());

            URI linkSubject = fac.createURI(con.getNamespace("irida"), "linkSubject");
            URI linkPredicate = fac.createURI(con.getNamespace("irida"), "linkPredicate");
            URI linkObject = fac.createURI(con.getNamespace("irida"), "linkObject");

            con.add(fac.createStatement(linkURI, rdftype, type));
            con.add(fac.createStatement(linkURI, linkSubject, subURI));
            con.add(fac.createStatement(linkURI, linkPredicate, pred));
            con.add(fac.createStatement(linkURI, linkObject, objURI));

            con.commit();

            auditRepo.audit(link.getAuditInformation(), linkURI.toString());

        } catch (RepositoryException ex) {
            logger.error("A RepositoryException occurred at [" + new Date() + "]", ex);
            throw new StorageException("Failed to create relationship.");
        } finally {
            store.closeRepoConnection(con);
        }

        return link;
    }

    /**
     * Get an identifier object for the given URI
     *
     * @param uri The URI to retrieve and build an identifier for
     * @return A new Identifier instance
     */
    private Identifier getIdentiferForURI(URI uri) {
        Identifier id = null;
        ObjectConnection con = store.getRepoConnection();
        logger.trace("Going to get identifier for URI: [" + uri + "]");
        try {
            String qs = store.getPrefixes()
                    + "SELECT ?object ?identifier ?label "
                    + "WHERE{ ?object irida:identifier ?identifier ;"
                    + " rdfs:label ?label .\n"
                    + "}";
            TupleQuery query = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            query.setBinding("object", uri);
            TupleQueryResult results = query.evaluate();
            BindingSet bs = results.next();
            id = buildIdentiferFromBindingSet(bs, "object");
        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error("A RepositoryException | MalformedQueryException | " +
                    "QueryEvaluationException occurred at [" + new Date() + "]", ex);
            throw new StorageException("Failed to get identifier for URI: [" + uri + "]");
        } finally {
            store.closeRepoConnection(con);
        }

        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Identifier> listObjects(Identifier subjectId, RdfPredicate predicate) {

        List<Identifier> ids = new ArrayList<>();
        java.net.URI subNetURI = getUriFromIdentifier(subjectId);

        ObjectConnection con = store.getRepoConnection();
        try {
            String qs = store.getPrefixes()
                    + "SELECT ?object ?identifier ?label "
                    + "WHERE{ ?subject ?predicate ?object .\n"
                    + "?object irida:identifier ?identifier ;"
                    + " rdfs:label ?label .\n"
                    + "}";
            ValueFactory fac = con.getValueFactory();
            TupleQuery query = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            URI subURI = fac.createURI(subNetURI.toString());
            URI predURI = predicate.getPredicateURI(con);
            query.setBinding("subject", subURI);
            query.setBinding("predicate", predURI);
            TupleQueryResult results = query.evaluate();
            while (results.hasNext()) {
                BindingSet bs = results.next();
                ids.add(buildIdentiferFromBindingSet(bs, "object"));
            }

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error("A RepositoryException | MalformedQueryException | " +
                    "QueryEvaluationException occurred at [" + new Date() + "]", ex);
            throw new StorageException(
                    "Failed to list related identifiers for [" + subjectId + "] with relationship [" + predicate + "]");
        } finally {
            store.closeRepoConnection(con);
        }
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Identifier> listSubjects(Identifier objectId, RdfPredicate predicate) {

        List<Identifier> ids = new ArrayList<>();
        java.net.URI objNetUri = getUriFromIdentifier(objectId);

        ObjectConnection con = store.getRepoConnection();
        try {
            String qs = store.getPrefixes()
                    + "SELECT ?subject ?identifier ?label "
                    + "WHERE{ ?subject ?predicate ?object .\n"
                    + "?subject irida:identifier ?identifier ;"
                    + " rdfs:label ?label .\n"
                    + "}";
            ValueFactory fac = con.getValueFactory();
            TupleQuery query = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            URI objURI = fac.createURI(objNetUri.toString());
            URI predURI = predicate.getPredicateURI(con);
            query.setBinding("object", objURI);
            query.setBinding("predicate", predURI);
            TupleQueryResult results = query.evaluate();
            while (results.hasNext()) {
                BindingSet bs = results.next();
                ids.add(buildIdentiferFromBindingSet(bs, "subject"));
            }

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error("A RepositoryException | MalformedQueryException | " +
                    "QueryEvaluationException occurred at [" + new Date() + "]", ex);
            throw new StorageException(
                    "Failed to list related identifiers for [" + objectId + "] with relationship [" + predicate + "]");
        } finally {
            store.closeRepoConnection(con);
        }
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Identifier> listLinks(Identifier id, Class subjectType, Class objectType) {
        RdfPredicate pred = linkList.getLink(subjectType, objectType);

        return listObjects(id, pred);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> getLinks(Identifier subjectId, Class subjectType, Class objectType) {
        RdfPredicate pred = linkList.getLink(subjectType, objectType);

        return getLinks(subjectId, pred, null);
    }

    @Override
    public List<Relationship> getLinks(Identifier subjectId, RdfPredicate predicate, Identifier objectId) {
        if (subjectId == null && predicate == null && objectId == null) {
            throw new IllegalArgumentException("subjectId, predicate, and objectId cannot all be null");
        }

        List<Relationship> links = new ArrayList<>();

        ObjectConnection con = store.getRepoConnection();
        try {
            String qs = store.getPrefixes() +
                    "SELECT ?link ?sub ?pred ?obj " +
                    "WHERE{ " +
                    "?link a irida:ResourceLink ; " +
                    " irida:linkPredicate ?linkPred ; " +
                    " irida:linkElement ?sub ; " +
                    " irida:linkElement ?obj . " +
                    "?sub ?pred ?obj. " +
                    "OPTIONAL{ " +
                    "?linkPred owl:inverseOf ?inv " +
                    "} " +
                    "FILTER(?pred IN (?linkPred,?inv)). " +
                    "}";
            ValueFactory fac = con.getValueFactory();
            TupleQuery query = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            if (subjectId != null) {
                java.net.URI subNetURI = getUriFromIdentifier(subjectId);
                URI subURI = fac.createURI(subNetURI.toString());
                query.setBinding("sub", subURI);
            }

            if (predicate != null) {
                URI predURI = predicate.getPredicateURI(con);
                query.setBinding("pred", predURI);
            }

            if (objectId != null) {
                java.net.URI objNetURI = getUriFromIdentifier(objectId);
                URI objURI = fac.createURI(objNetURI.toString());
                query.setBinding("obj", objURI);
            }

            TupleQueryResult results = query.evaluate();
            while (results.hasNext()) {
                BindingSet bs = results.next();

                String uristr = bs.getValue("link").stringValue();
                URI uri = fac.createURI(uristr);
                String identifiedBy = getIdentifiedBy(con, uri);

                Identifier linkId = buildLinkIdentifier(uri, identifiedBy);
                Relationship link = buildLinkfromBindingSet(bs, con);
                link.setIdentifier(linkId);
                Audit audit = auditRepo.getAudit(uri.toString());
                link.setAuditInformation(audit);

                links.add(link);
            }

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error("A RepositoryException | MalformedQueryException | " +
                    "QueryEvaluationException occurred at [" + new Date() + "]", ex);
            throw new StorageException(
                    "Failed to get links for [" + subjectId + "] with predicate ["
                            + predicate + "] to [" + objectId + "]");
        } finally {
            store.closeRepoConnection(con);
        }

        return links;
    }

    private Relationship buildLinkfromBindingSet(BindingSet bs, ObjectConnection con) {
        logger.trace("Building relationship from binding set [" + bs + "]");
        ValueFactory fac = con.getValueFactory();

        String substr = bs.getValue("sub").stringValue();
        URI subURI = fac.createURI(substr);

        String objstr = bs.getValue("obj").stringValue();
        URI objURI = fac.createURI(objstr);

        String predstr = bs.getValue("pred").stringValue();
        URI predURI = fac.createURI(predstr);

        RdfPredicate pred = new QualifiedRDFPredicate(predstr);//new RdfPredicate(predURI.getNamespace(), predURI.getLocalName());

        Identifier subId = getIdentiferForURI(subURI);
        Identifier objId = getIdentiferForURI(objURI);
        Relationship l = new Relationship();
        l.setSubject(subId);
        l.setObject(objId);

        l.setPredicate(pred);

        return l;
    }

    @Override
    public Relationship read(Identifier id) throws EntityNotFoundException {
        Relationship ret = null;

        java.net.URI linkNetURI = getUriFromIdentifier(id);

        ObjectConnection con = store.getRepoConnection();
        try {
            String qs = store.getPrefixes()
                    + "SELECT ?link ?sub ?pred ?obj "
                    + "WHERE{ ?link a irida:ResourceLink ;\n"
                    + " irida:linkSubject ?sub ; \n"
                    + " irida:linkPredicate ?pred ;\n"
                    + " irida:linkObject ?obj ."
                    + "}";
            ValueFactory fac = con.getValueFactory();
            TupleQuery query = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            URI subURI = fac.createURI(linkNetURI.toString());
            query.setBinding("link", subURI);

            TupleQueryResult results = query.evaluate();
            if (results.hasNext()) {
                BindingSet bs = results.next();

                String uristr = bs.getValue("link").stringValue();
                URI uri = fac.createURI(uristr);
                String identifiedBy = getIdentifiedBy(con, uri);

                Identifier linkId = buildLinkIdentifier(uri, identifiedBy);
                ret = buildLinkfromBindingSet(bs, con);
                ret.setIdentifier(linkId);
                Audit audit = auditRepo.getAudit(uri.toString());
                ret.setAuditInformation(audit);
            }

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error("A RepositoryException | MalformedQueryException | " +
                    "QueryEvaluationException occurred at [" + new Date() + "]", ex);
            throw new StorageException(
                    "Failed to get load relationship with id [" + id + "]");
        } finally {
            store.closeRepoConnection(con);
        }

        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <SubjectType extends IridaThing, ObjectType extends IridaThing> void delete(SubjectType subject, ObjectType object) {
        RdfPredicate pred = linkList.getLink(subject.getClass(), object.getClass());

        List<Relationship> links = getLinks((Identifier) subject.getIdentifier(), pred, (Identifier) object.getIdentifier());
        if (links.isEmpty()) {
            throw new EntityNotFoundException("No relationship found to delete between objects");
        }
        logger.trace("Deleting " + links.size() + " relationships.");
        for (Relationship r : links) {
            delete(r.getIdentifier());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Identifier id) throws EntityNotFoundException {
        if (!exists(id)) {
            throw new EntityNotFoundException("Object does not exist in the database.");
        }

        ObjectConnection con = store.getRepoConnection();

        java.net.URI netURI = buildURIFromIdentifier(id);
        String uri = netURI.toString();

        ValueFactory vf = con.getValueFactory();
        URI objecturi = vf.createURI(uri);

        try {
            con.begin();
            //first we'll remove the predicate between the subject and object.
            Relationship relationship = read(id);
            java.net.URI subNetURI = getUriFromIdentifier(relationship.getSubject());
            java.net.URI objNetURI = getUriFromIdentifier(relationship.getObject());
            RdfPredicate predicate = relationship.getPredicate();
            URI subURI = vf.createURI(subNetURI.toString());
            URI objURI = vf.createURI(objNetURI.toString());

            URI pred = predicate.getPredicateURI(con);
            con.remove(subURI, pred, objURI);

            //then we'll remove the relationship object
            con.remove(objecturi, null, null);
            con.commit();

        } catch (RepositoryException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to remove object" + id);
        } finally {
            store.closeRepoConnection(con);
        }
    }

    @Override
    public List<Relationship> list() {
        throw new UnsupportedOperationException("Listing links will not be supported.");
    }

    @Override
    public List<Relationship> list(int page, int size, String sortProperty, Order order) {
        throw new UnsupportedOperationException("Listing links will not be supported.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean exists(Identifier id) {
        boolean exists = false;
        ObjectConnection con = store.getRepoConnection();

        try {
            java.net.URI netURI = buildURIFromIdentifier(id);
            String uri = netURI.toString();

            logger.trace("Checking for the existence of [" + uri + "]");

            String querystring = store.getPrefixes()
                    + "ASK\n"
                    + "{?uri a irida:ResourceLink}";

            BooleanQuery existsQuery = con.prepareBooleanQuery(QueryLanguage.SPARQL, querystring);

            ValueFactory vf = con.getValueFactory();
            URI objecturi = vf.createURI(uri);
            existsQuery.setBinding("uri", objecturi);

            exists = existsQuery.evaluate();

            logger.trace("[" + uri + "] exists? " + exists);

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Couldn't run exists query");
        } finally {
            store.closeRepoConnection(con);
        }

        return exists;
    }

    @Override
    public Integer count() {
        throw new UnsupportedOperationException("Counting links will not be supported.");
    }

    @Override
    public Relationship update(Identifier id, Map<String, Object> updatedFields) {
        throw new UnsupportedOperationException("Updating a relationship will not be supported");
    }

    @Override
    public Collection<Relationship> readMultiple(Collection<Identifier> idents) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
