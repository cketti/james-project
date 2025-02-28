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
package org.apache.james.rrt.jdbc;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.junit.After;
import org.junit.Before;

public class JDBCRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JDBCRecipientRewriteTable localVirtualUserTable = new JDBCRecipientRewriteTable();
        localVirtualUserTable.setDataSource(getDataSource());
        localVirtualUserTable.setFileSystem(new MockFileSystem());
        BaseHierarchicalConfiguration defaultConfiguration = new BaseHierarchicalConfiguration();
        defaultConfiguration.addProperty("[@destinationURL]", "db://maildb/RecipientRewriteTable");
        defaultConfiguration.addProperty("sqlFile", "file://conf/sqlResources.xml");
        localVirtualUserTable.configure(defaultConfiguration);
        localVirtualUserTable.init();
        return localVirtualUserTable;
    }

    private BasicDataSource getDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(EmbeddedDriver.class.getName());
        ds.setUrl("jdbc:derby:target/testdb;create=true");
        ds.setUsername("james");
        ds.setPassword("james");
        return ds;
    }
}
