/*
 * Copyright 2011 SecondMarket Labs, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mongeez;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mongeez.validation.ValidationException;
import org.springframework.core.io.ClassPathResource;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;

public class MongeezTest {
    private String dbName = "mongeez-test";
    String mongoHostname = "127.0.0.1";
    int mongodbPort = 27017;
    String mongoUsername = "mongeezuser";
    String mongoPassword = "test123";

    private MongoClient mongoClient;
    private MongoAuth mongoAuth;
    private MongoDatabase mongoDb;

	@Before
    public void setUp() throws Exception {

        MongoCredential credential = MongoCredential.createCredential(mongoUsername, dbName, mongoPassword.toCharArray());
        mongoAuth = new MongoAuth(mongoUsername, mongoPassword, dbName);
        MongoClientOptions options = MongoClientOptions.builder().build();
        List<ServerAddress> addresses = new ArrayList<ServerAddress>();
        ServerAddress address = new ServerAddress(mongoHostname, mongodbPort);
        addresses.add(address);
        mongoClient = new MongoClient(addresses, credential, options);
        mongoDb = mongoClient.getDatabase(dbName);
        mongoDb.drop();
    }

    private Mongeez create(String path) {
        Mongeez mongeez = new Mongeez();
        mongeez.setFile(new ClassPathResource(path));
        mongeez.setMongo(mongoClient);
        mongeez.setDbName(dbName);
        mongeez.setAuth(mongoAuth);
        return mongeez;
    }

    @Test
    public void testMongeez() throws Exception {
        Mongeez mongeez = create("mongeez.xml");

        mongeez.process();

        assertEquals(mongoDb.getCollection("mongeez").count(), 5);

        assertEquals(mongoDb.getCollection("organization").count(), 2);
        assertEquals(mongoDb.getCollection("user").count(), 2);
    }

    @Test
    public void testRunTwice() throws Exception {
        testMongeez();
        testMongeez();
    }

    @Test
    public void testFailOnError_False() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_fail.xml");
        mongeez.process();

        assertEquals(mongoDb.getCollection("mongeez").count(), 2);
    }

    @Test(expected = com.mongodb.MongoCommandException.class)
    public void testFailOnError_True() throws Exception {
        Mongeez mongeez = create("mongeez_fail_fail.xml");
        mongeez.process();
    }

    @Test
    public void testNoFiles() throws Exception {
        Mongeez mongeez = create("mongeez_empty.xml");
        mongeez.process();

        assertEquals(mongoDb.getCollection("mongeez").count(), 1);
    }

    @Test
    public void testNoFailureOnEmptyChangeLog() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_empty_changelog.xml");
        mongeez.process();

        assertEquals(mongoDb.getCollection("mongeez").count(), 1);
    }

    @Test(expected = ValidationException.class)
    public void testNoFailureOnNoChangeFilesBlock() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_no_changefiles_declared.xml");
        mongeez.process();
    }

    @Test
    public void testChangesWContextContextNotSet() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_contexts.xml");
        mongeez.process();
        assertEquals(mongoDb.getCollection("mongeez").count(), 2);
        assertEquals(mongoDb.getCollection("car").count(), 2);
        assertEquals(mongoDb.getCollection("user").count(), 0);
        assertEquals(mongoDb.getCollection("organization").count(), 0);
        assertEquals(mongoDb.getCollection("house").count(), 0);
    }

    @Test
    public void testChangesWContextContextSetToUsers() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_contexts.xml");
        mongeez.setContext("users");
        mongeez.process();
        assertEquals(mongoDb.getCollection("mongeez").count(), 4);
        assertEquals(mongoDb.getCollection("car").count(), 2);
        assertEquals(mongoDb.getCollection("user").count(), 2);
        assertEquals(mongoDb.getCollection("organization").count(), 0);
        assertEquals(mongoDb.getCollection("house").count(), 2);
    }

    @Test
    public void testChangesWContextContextSetToOrganizations() throws Exception {
        assertEquals(mongoDb.getCollection("mongeez").count(), 0);

        Mongeez mongeez = create("mongeez_contexts.xml");
        mongeez.setContext("organizations");
        mongeez.process();
        assertEquals(mongoDb.getCollection("mongeez").count(), 4);
        assertEquals(mongoDb.getCollection("car").count(), 2);
        assertEquals(mongoDb.getCollection("user").count(), 0);
        assertEquals(mongoDb.getCollection("organization").count(), 2);
        assertEquals(mongoDb.getCollection("house").count(), 2);
    }

    @Test(expected = ValidationException.class)
    public void testFailDuplicateIds() throws Exception {
        Mongeez mongeez = create("mongeez_fail_on_duplicate_changeset_ids.xml");
        mongeez.process();
    }
}
