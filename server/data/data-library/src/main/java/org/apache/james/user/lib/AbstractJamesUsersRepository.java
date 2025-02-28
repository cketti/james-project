/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.user.lib;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.lib.MappingsImpl.Builder;
import org.apache.james.user.api.JamesUsersRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.JamesUser;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.DefaultJamesUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A partial implementation of a Repository to store users.
 * <p>
 * This implements common functionality found in different UsersRespository
 * implementations, and makes it easier to create new User repositories.
 * </p>
 * 
 * @deprecated Please implement {@link UsersRepository}
 */
@Deprecated
public abstract class AbstractJamesUsersRepository extends AbstractUsersRepository implements JamesUsersRepository, RecipientRewriteTable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJamesUsersRepository.class);

    /**
     * Ignore case in usernames
     */
    protected boolean ignoreCase;

    /**
     * Enable Aliases frmo JamesUser
     */
    protected boolean enableAliases;

    /**
     * Wether to enable forwarding for JamesUser or not
     */
    protected boolean enableForwarding;

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        setIgnoreCase(configuration.getBoolean("ignoreCase", false));
        setEnableAliases(configuration.getBoolean("enableAliases", false));
        setEnableForwarding(configuration.getBoolean("enableForwarding", false));
        super.configure(configuration);
    }

    /**
     * Adds a user to the underlying Repository. The user name must not clash
     * with an existing user.
     * 
     * @param user
     *            the user to add
     */
    protected abstract void doAddUser(User user) throws UsersRepositoryException;

    /**
     * Updates a user record to match the supplied User.
     * 
     * @param user
     *            the user to update
     */
    protected abstract void doUpdateUser(User user) throws UsersRepositoryException;

    @Override
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        User newbie = new DefaultJamesUser(username, "SHA");
        newbie.setPassword(password);
        doAddUser(newbie);
    }

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     * 
     * @param user
     *            the user to be updated
     * @throws UsersRepositoryException
     */
    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        // Return false if it's not found.
        if (!contains(user.getUserName())) {
            throw new UsersRepositoryException("User " + user.getUserName() + " does not exist");
        } else {
            doUpdateUser(user);
        }
    }

    @Override
    public Mappings getResolvedMappings(String username, Domain domain) throws ErrorMappingException, RecipientRewriteTableException {
        Builder mappingsBuilder = MappingsImpl.builder();
        try {
            User user = getUserByName(username);

            if (user instanceof JamesUser) {
                JamesUser jUser = (JamesUser) user;

                if (enableAliases && jUser.getAliasing()) {
                    String alias = jUser.getAlias();
                    if (alias != null) {
                        mappingsBuilder.add(alias + "@" + domain.asString());
                    }
                }

                if (enableForwarding && jUser.getForwarding()) {
                    String forward;
                    if (jUser.getForwardingDestination() != null && ((forward = jUser.getForwardingDestination().toString()) != null)) {
                        mappingsBuilder.add(forward);
                    } else {
                        String errorBuffer = "Forwarding was enabled for " + username + " but no forwarding address was set for this account.";
                        LOGGER.error(errorBuffer);
                    }
                }
            }
        } catch (UsersRepositoryException e) {
            throw new RecipientRewriteTableException("Unable to lookup forwards/aliases", e);
        }
        Mappings mappings = mappingsBuilder.build();
        if (mappings.size() == 0) {
            return null;
        } else {
            return mappings;
        }
    }

    @Override
    public void setEnableAliases(boolean enableAliases) {
        this.enableAliases = enableAliases;
    }

    @Override
    public void setEnableForwarding(boolean enableForwarding) {
        this.enableForwarding = enableForwarding;
    }

    @Override
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() throws RecipientRewriteTableException {
        Map<MappingSource, Mappings> mappings = new HashMap<>();
        if (enableAliases || enableForwarding) {
            try {
                Iterator<String> users = list();
                while (users.hasNext()) {
                    String user = users.next();
                    int index = user.indexOf("@");
                    String username;
                    Domain domain;
                    if (index != -1) {
                        username = user.substring(0, index);
                        domain = Domain.of(user.substring(index + 1, user.length()));
                    } else {
                        username = user;
                        domain = Domain.LOCALHOST;
                    }
                    try {
                        MappingSource source = MappingSource.fromUser(org.apache.james.core.User.fromUsername(user));
                        mappings.put(source, getResolvedMappings(username, domain));
                    } catch (ErrorMappingException e) {
                        // shold never happen here
                    }
                }
            } catch (UsersRepositoryException e) {
                throw new RecipientRewriteTableException("Unable to access forwards/aliases", e);
            }
        }

        return mappings;
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return MappingsImpl.empty();
    }

    @Override
    public void addRegexMapping(MappingSource source, String regex) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void removeRegexMapping(MappingSource source, String regex) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void addAddressMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void removeAddressMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void addErrorMapping(MappingSource source, String error) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void removeErrorMapping(MappingSource source, String error) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void addAliasDomainMapping(MappingSource source, Domain realDomain) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void removeAliasDomainMapping(MappingSource source, Domain realDomain) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    @Override
    public void addForwardMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void removeForwardMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void addGroupMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void removeGroupMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void addAliasMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    @Override
    public void removeAliasMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }
}
