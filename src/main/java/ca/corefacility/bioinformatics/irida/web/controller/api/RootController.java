/*
 * Copyright 2013 Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>.
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
package ca.corefacility.bioinformatics.irida.web.controller.api;

import ca.corefacility.bioinformatics.irida.web.assembler.resource.RootResource;
import ca.corefacility.bioinformatics.irida.web.controller.api.projects.ProjectsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

/**
 * A basis for clients to begin discovering other URLs in our API.
 *
 * @author Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>
 */
@Controller
public class RootController {

    /**
     * A collection of the controllers in our system.
     */
    public static final Map<String, Class> CONTROLLERS = new ConcurrentHashMap<>();
    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(RootController.class);

    /**
     * Initialize a collection of all controllers in the system.
     */
    @PostConstruct
    public void initLinks() {
        CONTROLLERS.put("users", UsersController.class);
        CONTROLLERS.put("projects", ProjectsController.class);
    }

    /**
     * Creates a response with a set of links used to discover the rest of the system.
     *
     * @return a response to the client.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/")
    public ModelMap getLinks() {
        logger.debug("Discovering application");
        RootResource resource = new RootResource();
        List<Link> links = new ArrayList<>();

        // create a link to all of the controllers defined in our set, then add the link to the list of links.
        for (Entry<String, Class> entry : CONTROLLERS.entrySet()) {
            Link link = linkTo(entry.getValue()).withRel(entry.getKey());
            links.add(link);
        }

        // add a self-rel to the current page
        resource.add(linkTo(methodOn(RootController.class).
                getLinks()).withSelfRel());

        // add all of the links to the response
        resource.add(links);

        ModelMap map = new ModelMap();
        map.addAttribute(GenericController.RESOURCE_NAME, resource);

        // respond to the client
        return map;
    }
}
